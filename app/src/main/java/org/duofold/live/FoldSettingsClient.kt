package org.duofold.live
import android.content.*
import android.os.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
/** Short-lived independent Binder connection with a UI-side deadline. */
internal object FoldSettingsClient {
 private val main=Handler(Looper.getMainLooper())
 private class Lane {
  var busy=false
  val inFlight=java.util.concurrent.atomic.AtomicBoolean(false)
  val worker=Executors.newSingleThreadExecutor()
 }
 private val awakeLane=Lane()
 private val decorLane=Lane()
 fun request(context:Context,action:String,original:String?,done:(Bundle)->Unit){main.post{
  val decoration=action.startsWith("decor_")
  val lane=if(decoration)decorLane else awakeLane
  val label=if(decoration)"Display helper" else "Keep-awake helper"
  if(lane.busy || lane.inFlight.get()){done(Bundle().apply{putString("error","$label already running");putBoolean("busy",true)});return@post}
  lane.busy=true
  val args=Shizuku.UserServiceArgs(ComponentName(context.applicationContext,if(decoration) DisplaySettingsService::class.java else FoldSettingsService::class.java))
   .daemon(false).processNameSuffix(if(decoration)"display_settings" else "fold_settings").version(BuildConfig.VERSION_CODE)
  var finished=false
  lateinit var connection:ServiceConnection
  lateinit var timeout:Runnable
  fun finish(result:Bundle){
   if(finished)return
   finished=true;lane.busy=false;main.removeCallbacks(timeout)
   runCatching{Shizuku.unbindUserService(args,connection,true)}
   done(result)
  }
  fun error(message:String)=Bundle().apply{putString("error",message)}
  timeout=Runnable{finish(error("$label timed out; will retry"))}
  connection=object:ServiceConnection{
   override fun onServiceConnected(name:ComponentName,binder:IBinder){main.post{
    if(finished)return@post
    if(!lane.inFlight.compareAndSet(false,true)){finish(error("$label previous transaction still running"));return@post}
    lane.worker.execute{
     val p=Parcel.obtain();val r=Parcel.obtain()
     val result=try{
      p.writeInterfaceToken(FoldSettingsService.TOKEN);p.writeString(action);p.writeString(original)
      check(binder.transact(1,p,r,0)){"$label unavailable"};r.readException()
      r.readBundle(FoldSettingsClient::class.java.classLoader)?:error("Empty response")
     }catch(e:Exception){error(e.toString())}finally{p.recycle();r.recycle();lane.inFlight.set(false)}
     main.post{finish(result)}
    }
   }}
   override fun onServiceDisconnected(name:ComponentName){main.post{finish(error("$label disconnected"))}}
  }
  main.postDelayed(timeout,10000)
  try{Shizuku.bindUserService(args,connection)}catch(e:Exception){finish(error(e.toString()))}
 }}
}
