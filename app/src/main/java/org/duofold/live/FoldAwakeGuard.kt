package org.duofold.live
import android.app.KeyguardManager
import android.content.Context
import android.os.*
internal class FoldAwakeGuard(private val context:Context){
 private val main=Handler(Looper.getMainLooper())
 private val power=context.getSystemService(PowerManager::class.java)
 private val keyguard=context.getSystemService(KeyguardManager::class.java)
 private var previous=Float.NaN
 private var until=0L;private var armed=0L;private var pending=false;private var attempted=false
 private var lock:PowerManager.WakeLock?=null
 private val listener=LiveAngles.Listener{angle,_->main.post{sample(angle)}}
 fun start(){LiveAngles.add(listener)}
 private fun sample(angle:Float){
  if(!LiveAngles.fresh())return
  val now=SystemClock.uptimeMillis()
  val enabled=context.getSharedPreferences("standalone",0).getBoolean("enabled",false)
  if(!enabled){release();previous=angle;return}
  if(until==0L && previous.isFinite() && previous-angle>=.5f && previous>20f && power.isInteractive && !keyguard.isKeyguardLocked){
   armed=now;until=now+5000;attempted=false
   try{lock=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"Duo:fold-handoff").apply{setReferenceCounted(false);acquire(5000)}}catch(e:Exception){RecoveryLog.add("Fold wake hold unavailable: ${e.message}")}
   FoldAwakeDefault.reconnect();FoldAwakeDefault.tick(context)
   RecoveryLog.add("Fold wake hold armed for at most 5 seconds")
   main.removeCallbacks(tick);main.post(tick)
  }
  if(until>0 && angle<=2f)until=minOf(until,now+750)
  if(until>0 && angle>previous+5f)release()
  previous=angle
 }
 private val tick=object:Runnable{override fun run(){
  if(until==0L)return
  if(SystemClock.uptimeMillis()>=until || !context.getSharedPreferences("standalone",0).getBoolean("enabled",false)){release();return}
  if(!power.isInteractive && !pending && !attempted){
   pending=true;attempted=true
   FoldSettingsClient.request(context,"fold_wake",armed.toString()){result->pending=false;if(result.getBoolean("busy"))attempted=false else RecoveryLog.add("Fold wake recovery: "+(result.getString("value")?:result.getString("error")))}
  }
  main.postDelayed(this,100)
 }}
 private fun release(){until=0;main.removeCallbacks(tick);runCatching{lock?.let{if(it.isHeld)it.release()}};lock=null}
 fun close(){LiveAngles.remove(listener);release();main.removeCallbacksAndMessages(null)}
}
