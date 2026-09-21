package org.duofold.live
import android.os.SystemClock
import androidx.compose.runtime.*
/** Main-thread coordinator. Only the captured outgoing panel may display a frozen frame. */
internal object HandoffFrames {
 var frame by mutableStateOf<GlassFrame?>(null);private set
 var status="Freeze handoff idle";private set
 private var sourceInner=false;private var preparing=false;private var active=false
 private var unavailableSince=0L
 private var generation=0;private var retryAt=0L;private var readyAt=0L
 @Volatile private var ready=-1
 @JvmStatic fun readySource():Int=ready
 @JvmStatic fun clear(){generation++;frame=null;preparing=false;active=false;ready=-1;readyAt=0;status="Freeze handoff idle"}
 fun forPanel(inner:Boolean):GlassFrame?=frame?.takeIf{sourceInner==inner}
 @JvmStatic fun update(inner:Boolean,dual:Boolean,angle:Float,fresh:Boolean,openThreshold:Float,enabled:Boolean){
  val now=SystemClock.elapsedRealtime()
  if(!enabled || !fresh){
   if(unavailableSince==0L)unavailableSince=now
   ready=-1
   if(now-unavailableSince>=300)clear()
   return
  }
  unavailableSince=0L
  if(dual){active=true;return}
  if(active){clear();return}
  if(!FoldThreshold.canStart(angle,openThreshold)){clear();return}
  if(ready>=0){if(inner!=sourceInner || now-readyAt>1500){clear();retryAt=now+200};return}
  if(preparing || now<retryAt)return
  preparing=true;sourceInner=inner;val gen=++generation
  status="Capturing outgoing ${if(inner) "inner" else "cover"} before display switch"
  GlassFrames.freeze{captured,note->
   if(gen!=generation)return@freeze
   preparing=false
   val nowCapture=SystemClock.elapsedRealtime()
   if(captured!=null && FreezePolicy.accepts(inner,captured.width,captured.height,captured.stamp,nowCapture)){
    frame=captured;readyAt=nowCapture;ready=if(inner)1 else 0
    status="Frozen ${if(inner) "inner" else "cover"} ${captured.width}×${captured.height}; ready for incoming display"
    RecoveryLog.add(status)
   }else{status="Freeze handoff waiting: $note";retryAt=nowCapture+500;RecoveryLog.add(status)}
  }
 }
}
