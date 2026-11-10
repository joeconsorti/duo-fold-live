package org.duofold.live

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.*

/** Verify the exact incoming app once, then feed fresh excluded-overlay captures to its animation. */
internal object IncomingAnimationFrame {
 var frame by mutableStateOf<GlassFrame?>(null);private set
 var status="Incoming animation idle";private set
 @Volatile var captureDisplay=1;private set
 private val main=android.os.Handler(android.os.Looper.getMainLooper())
 var remapFrame by mutableStateOf<GlassFrame?>(null);private set
 private var remapStarted=0L
 private var remapNote="No retained incoming frame"
 fun retainedFor(display:android.view.Display?,width:Int,height:Int):GlassFrame? {
  val f=remapFrame ?: return null
  val id=runCatching{android.view.Display::class.java.getMethod("getUniqueId").invoke(display).toString()}.getOrDefault("")
  return f.takeIf{display!=null && RemapFramePolicy.matches(physical,id,f.width,f.height,width,height,f.rotation,display.rotation,remapStarted,android.os.SystemClock.elapsedRealtime())}
 }
 private fun endRemap(reason:String){
  if(remapFrame!=null){remapNote="Incoming remap frame $reason after ${SystemClock.elapsedRealtime()-remapStarted} ms";RecoveryLog.add(remapNote)}
  remapFrame=null
 }
 private var session=0L
 private var generation=0
 private var pending=false
 private var retryAt=0L
 private var physical=""
 private var verifiedThisSession=false
 private var cleanStamp=0L
 private var capturing=false
 private var updates=0
 private var lastStamp=0L
 private var lastSession="No incoming live session completed"
 @JvmStatic fun report()="$status\nLast incoming session: $lastSession\n$remapNote"
 @JvmStatic fun clear(){
  if(session>0L)lastSession="session=$session; physical=$physical; live updates=$updates; exact app verified during session=$verifiedThisSession; last status=$status"
  endRemap("cleared");generation++;session=0L;physical="";frame=null;pending=false;retryAt=0L;cleanStamp=0L;verifiedThisSession=false;updates=0;lastStamp=0L;releaseCapture();status="Incoming animation idle"
 }
 private fun releaseCapture(){if(capturing){capturing=false;GlassFrames.release()}}
 @JvmStatic fun update(context:Context,info:Bundle?,fresh:Boolean,allowed:Boolean){
  val prefs=context.getSharedPreferences("standalone",0)
  if(prefs.getBoolean("screenshot_native_test",false)&&prefs.getBoolean("dual",false)){if(session!=0L)clear();status="Screenshot test: incoming app renders natively; continuous capture disabled";return}
  val next=info?.getLong("session")?:0L
  val valid=fresh&&allowed&&prefs.getBoolean("enabled",false)&&prefs.getBoolean("dual",false)&&prefs.getBoolean("screenshot_camera_startup",false)&&info?.getBoolean("active")==true&&next>0L&&context.getSystemService(PowerManager::class.java).isInteractive&&!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
  val targetDisplay=info?.getInt("display",1)?:1
  val target=info?.getString("physical")?:""
  if(!valid||next!=session||target!=physical){
   clear()
   captureDisplay=targetDisplay
   session=if(valid)next else 0L;physical=if(valid)target else ""
   status=if(valid)"Waiting for exact incoming app route" else "Incoming animation idle"
  }
  if(!valid||target.isEmpty())return
  if(targetDisplay!=captureDisplay){
   val now=SystemClock.elapsedRealtime()
   val saved=frame?.takeIf{verifiedThisSession && it.physicalId==target && RemapFramePolicy.canStart(captureDisplay,targetDisplay,it.stamp,now)}
   generation++;pending=false;retryAt=0L;cleanStamp=0L;frame=null;releaseCapture()
   captureDisplay=targetDisplay
   endRemap("replaced")
   if(saved!=null){
    remapFrame=saved;remapStarted=now
    remapNote="Incoming remap frame retained; session=$session; physical=$physical; capture=${saved.stamp}; deadline=${now+1000}"
    RecoveryLog.add(remapNote)
    val started=remapStarted
    main.postDelayed({if(remapStarted==started)endRemap("expired")},1000)
   }else{remapNote="Incoming remap frame unavailable: no fresh verified D1 frame";RecoveryLog.add(remapNote)}
  }
  if(info?.getBoolean("routed")!=true){
   if(capturing||pending||cleanStamp>0L){generation++;pending=false;cleanStamp=0L;frame=null;releaseCapture()}
   status="Incoming animation waiting for fullscreen app route";return
  }
  val now=SystemClock.elapsedRealtime()
  if(cleanStamp>0L){
   val live=GlassFrames.frame
   if(live!=null&&ScreenshotLiveFramePolicy.accepts(captureDisplay,physical,live.physicalId,live.displayId,cleanStamp,live.stamp,now,info.getInt("width"),info.getInt("height"),live.width,live.height)){
    frame=live
    if(lastStamp!=live.stamp){lastStamp=live.stamp;updates++}
    status="Incoming live animation: D$captureDisplay; updates=$updates; frame age=${now-live.stamp} ms; physical=$physical"
   }else{
    if(frame!=null&&now-frame!!.stamp>350L)frame=null
    status="Incoming live animation waiting for fresh destination frame; updates=$updates; ${GlassFrames.status}"
   }
   return
  }
  if(pending||now<retryAt)return
  val task=info.getInt("task",-1)
  if(task<0)return
  pending=true;val epoch=generation
  GlassFrames.incoming(task,target,captureDisplay){captured,note->
   if(epoch!=generation)return@incoming
   pending=false
   if(captured!=null&&captured.physicalId==physical){
    endRemap("replaced by verified capture");verifiedThisSession=true;cleanStamp=captured.stamp;frame=captured
    GlassFrames.configure(context)
    if(!capturing){capturing=true;GlassFrames.acquire()}
    GlassFrames.requestFreshCapture()
    status="Exact incoming app verified; starting live animation captures; task=$task; D$captureDisplay"
    RecoveryLog.add(status)
   }else{status="Incoming capture waiting: $note";retryAt=SystemClock.elapsedRealtime()+150L}
  }
 }
}
