package org.duofold.live
import android.app.KeyguardManager
import android.content.Context
import android.os.*
internal class FoldAwakeGuard(private val context:Context){
 private val main=Handler(Looper.getMainLooper())
 private val power=context.getSystemService(PowerManager::class.java)
 private val keyguard=context.getSystemService(KeyguardManager::class.java)
 companion object {@JvmField var status="Fold wake guard waiting for closing motion"}
 private var blocked=false
 private val physicalEpisode=FoldWakeEpisode()
 private var previous=Float.NaN;private var peak=Float.NaN;private var lowest=180f;private var sessionStarted=0L;private var lastRenew=0L
 private var until=0L;private var armed=0L;private var pending=false;private var attempted=false
 private var wakeConnection:FoldWakeConnection?=null
 private var episode=0L
 private var lock:PowerManager.WakeLock?=null
 private val baseListener=LiveAngles.BaseListener{base->physicalEpisode.observe(base)}
 private val listener=LiveAngles.Listener{angle,_->val base=LiveAngles.freshPhysicalBase();main.post{sample(angle,base)}}
 fun start(){LiveAngles.addBase(baseListener);LiveAngles.add(listener)}
 private fun sample(angle:Float,base:Int){
  if(!LiveAngles.fresh() || !angle.isFinite())return
  val now=SystemClock.uptimeMillis()
  val enabled=RuntimeConsent.ready(context) && FoldAwakeDefault.enabled(context) && context.getSharedPreferences("standalone",0).getBoolean("enabled",false)
  if(!enabled){release();previous=angle;peak=angle;return}
  if(blocked && (angle>=172f || angle>lowest+5f)){blocked=false;peak=angle}
  if(!peak.isFinite() || angle>peak)peak=angle
  if(!blocked && until==0L && previous.isFinite() && FoldWakePolicy.closingMotion(peak,angle) && power.isInteractive && !keyguard.isKeyguardLocked && base>0 && physicalEpisode.take()){
   armed=now;sessionStarted=now;lastRenew=now;lowest=angle;until=now+5000;attempted=false;status="Fold wake hold active"
   try{lock=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"Duo:fold-handoff").apply{setReferenceCounted(false);acquire(5000)}}catch(e:Exception){RecoveryLog.add("Fold wake hold unavailable: ${e.message}")}
   episode++;wakeConnection?.close();wakeConnection=FoldWakeConnection(context)
   FoldAwakeDefault.tick(context)
   RecoveryLog.add("Fold wake hold armed; renews during closing, maximum 30 seconds")
   main.removeCallbacks(tick);main.post(tick)
  }
  if(until>0 && angle<lowest){
   lowest=angle
   if(now-lastRenew>=1000){armed=now;lastRenew=now;until=FoldWakePolicy.holdUntil(sessionStarted,now);runCatching{lock?.acquire((until-now).coerceAtLeast(1))};status="Fold wake hold active; renewed during closing"}
  }
  if(until>0 && angle<=2f)until=minOf(until,now+2000)
  if(until>0 && angle>lowest+5f){release();peak=angle}
  if(until==0L && (angle<=2f || !power.isInteractive))peak=angle
  previous=angle
 }
 private val tick=object:Runnable{override fun run(){
  if(until==0L)return
  if(!RuntimeConsent.ready(context) || !FoldAwakeDefault.enabled(context) || SystemClock.uptimeMillis()>=until || !context.getSharedPreferences("standalone",0).getBoolean("enabled",false)){blocked=true;release();return}
  if(!power.isInteractive && !pending && !attempted){
   pending=true;attempted=true
   val expectedEpisode=episode
   wakeConnection?.wake(armed,until){result->
    if(expectedEpisode==episode){pending=false;status="Fold wake recovery: "+(result.getString("value")?:result.getString("error"));RecoveryLog.add(status)}
   }
  }
  main.postDelayed(this,if(lowest<=20f)20 else 100)
 }}
 private fun release(){if(until>0 && !attempted)status="Fold wake hold released; no sleep observed during hold";until=0;episode++;pending=false;wakeConnection?.close();wakeConnection=null;main.removeCallbacks(tick);runCatching{lock?.let{if(it.isHeld)it.release()}};lock=null}
 fun close(){LiveAngles.removeBase(baseListener);LiveAngles.remove(listener);release();main.removeCallbacksAndMessages(null)}
}
