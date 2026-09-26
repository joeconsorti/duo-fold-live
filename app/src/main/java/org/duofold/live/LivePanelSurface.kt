package org.duofold.live
import android.content.Context
import android.view.*
/** Retry startup ordering and rebuild mirrors after animation exclusion is confirmed. */
internal class LivePanelSurface(context:Context,private val result:(Boolean,String)->Unit):SurfaceView(context),SurfaceHolder.Callback {
 private var id=0;private var generation=0;private var active=false;private var pending=false;private var attached=false
 private var mirrorWidth=0;private var mirrorHeight=0;private var revision=-1
 private val exclusionChanged=Runnable{if(active){removeCallbacks(retry);post(retry)}}
 init{holder.addCallback(this);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
 private val retry:Runnable=object:Runnable{override fun run(){
  if(!active || !surfaceControl.isValid)return
  val ready=PreviewTransition.exclusionsReady()
  if(!pending && (!ready || revision!=PreviewTransition.exclusionRevision) && id!=0){LiveAngles.detachMirror(id);id=0;attached=false;result(false,"Waiting for clean preview setup")}
  if(ready && !pending && !attached){
   pending=true;val gen=generation;val requestedRevision=PreviewTransition.exclusionRevision
   id=LiveAngles.attachMirror(surfaceControl,mirrorWidth,mirrorHeight){ok,note->
    if(gen==generation){pending=false;attached=ok;revision=requestedRevision;result(ok,note);if(!ok){removeCallbacks(retry);postDelayed(retry,100)}}
   }
  }
  if(!attached && !pending)postDelayed(this,32)
 }}
 override fun surfaceCreated(holder:SurfaceHolder)=Unit
 override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){
  close();if(width<=0||height<=0||!surfaceControl.isValid)return
  mirrorWidth=width;mirrorHeight=height;active=true;PreviewTransition.listenForExclusions(exclusionChanged);post(retry)
 }
 override fun surfaceDestroyed(holder:SurfaceHolder){close()}
 fun close(){active=false;PreviewTransition.stopListeningForExclusions(exclusionChanged);removeCallbacks(retry);generation++;pending=false;attached=false;revision=-1;if(id!=0)LiveAngles.detachMirror(id);id=0;result(false,"Live mirror released")}
}
