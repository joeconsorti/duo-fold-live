package org.duofold.live
import android.content.*
import android.os.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
/** Short-lived independent Binder connection with a UI-side deadline. */
internal object FoldSettingsClient {
 private val main=Handler(Looper.getMainLooper())
 private var busy=false
 private val worker=Executors.newSingleThreadExecutor()
 fun request(context:Context,action:String,original:String?,done:(Bundle)->Unit){main.post{
  if(busy){done(Bundle().apply{putString("error","Keep-awake verification already running")});return@post}
  busy=true
  val args=Shizuku.UserServiceArgs(ComponentName(context.applicationContext,FoldSettingsService::class.java))
   .daemon(false).processNameSuffix("fold_settings").version(BuildConfig.VERSION_CODE)
  var finished=false
  lateinit var connection:ServiceConnection
  lateinit var timeout:Runnable
  fun finish(result:Bundle){
   if(finished)return
   finished=true;busy=false;main.removeCallbacks(timeout)
   runCatching{Shizuku.unbindUserService(args,connection,true)}
   done(result)
  }
  fun error(message:String)=Bundle().apply{putString("error",message)}
  timeout=Runnable{finish(error("Keep-awake helper timed out; will retry"))}
  connection=object:ServiceConnection{
   override fun onServiceConnected(name:ComponentName,binder:IBinder){main.post{
    if(finished)return@post
    worker.execute{
     val p=Parcel.obtain();val r=Parcel.obtain()
     val result=try{
      p.writeInterfaceToken(FoldSettingsService.TOKEN);p.writeString(action);p.writeString(original)
      check(binder.transact(1,p,r,0)){"Keep-awake helper unavailable"};r.readException()
      r.readBundle(FoldSettingsClient::class.java.classLoader)?:error("Empty response")
     }catch(e:Exception){error(e.toString())}finally{p.recycle();r.recycle()}
     main.post{finish(result)}
    }
   }}
   override fun onServiceDisconnected(name:ComponentName){main.post{finish(error("Keep-awake helper disconnected"))}}
  }
  main.postDelayed(timeout,10000)
  try{Shizuku.bindUserService(args,connection)}catch(e:Exception){finish(error(e.toString()))}
 }}
}
