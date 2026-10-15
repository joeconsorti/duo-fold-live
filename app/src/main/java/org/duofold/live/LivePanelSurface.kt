package org.duofold.live
import android.content.Context
import android.view.*
import android.hardware.display.DisplayManager
import android.graphics.Point
/** Retry startup ordering and rebuild mirrors after animation exclusion is confirmed. */
internal class LivePanelSurface(context:Context,private val halfPane:Boolean=false,private val result:(Boolean,String)->Unit):SurfaceView(context),SurfaceHolder.Callback,DisplayManager.DisplayListener {
 private var id=0;private var generation=0;private var active=false;private var pending=false;private var attached=false
 private val displayManager=context.getSystemService(DisplayManager::class.java)
 private var geometry=""
 private var landscapeGeometry=false
 private fun geometryKey():String{val source=displayManager.getDisplay(0)?:return "missing";val size=Point();source.getRealSize(size);landscapeGeometry=size.x>size.y && minOf(size.x,size.y).toFloat()/maxOf(size.x,size.y)<.7f;return "${size.x}x${size.y}:${source.rotation}:${display?.rotation}"}
 override fun onDisplayAdded(id:Int)=onDisplayChanged(id)
 override fun onDisplayRemoved(id:Int)=onDisplayChanged(id)
 override fun onDisplayChanged(id:Int){if(!active)return;val wasLandscape=landscapeGeometry;val next=geometryKey();if((wasLandscape||landscapeGeometry)&&next!=geometry){rebuildMirror(mirrorWidth,mirrorHeight)}}
 private var mirrorWidth=0;private var mirrorHeight=0;private var revision=-1
 private var since=0L;private var lastWait=""
 private fun dlog(m:String){if(BuildConfig.DIAGNOSTICS)android.util.Log.i("DuoMirror","+${android.os.SystemClock.elapsedRealtime()-since}ms $m")}
 private val exclusionChanged=Runnable{if(active){removeCallbacks(retry);post(retry)}}
 init{holder.addCallback(this);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
 private val retry:Runnable=object:Runnable{override fun run(){
  if(!active || !surfaceControl.isValid)return
  val ready=PreviewTransition.exclusionsReady()
  val wait=if(!ready)"waiting for exclusions" else if(pending)"attach pending" else "";if(wait!=lastWait&&wait.isNotEmpty()){lastWait=wait;dlog(wait)}
  if(!pending && (!ready || revision!=PreviewTransition.exclusionRevision) && id!=0){LiveAngles.detachMirror(id);id=0;attached=false;result(false,"Waiting for clean preview setup")}
  if(ready && !pending && !attached){
   pending=true;val gen=generation;val requestedRevision=PreviewTransition.exclusionRevision
   id=LiveAngles.attachMirror(surfaceControl,mirrorWidth,mirrorHeight,halfPane){ok,note->
    if(gen==generation){dlog("attach "+(if(ok)"OK" else "FAILED")+": "+note);pending=false;attached=ok;revision=requestedRevision;result(ok,note);if(!ok){removeCallbacks(retry);postDelayed(retry,100)}}
   }
  }
  if(!attached && !pending)postDelayed(this,32)
 }}
 override fun surfaceCreated(holder:SurfaceHolder)=Unit
 override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int)=rebuildMirror(width,height)
 private fun rebuildMirror(width:Int,height:Int){
  close();if(width<=0||height<=0||!surfaceControl.isValid)return
  since=android.os.SystemClock.elapsedRealtime();lastWait="";dlog("surface ready ${width}x$height")
  mirrorWidth=width;mirrorHeight=height;geometry=geometryKey();active=true;displayManager.registerDisplayListener(this,android.os.Handler(android.os.Looper.getMainLooper()));PreviewTransition.listenForExclusions(exclusionChanged);post(retry)
 }
 override fun surfaceDestroyed(holder:SurfaceHolder){close()}
 fun close(){displayManager.unregisterDisplayListener(this);active=false;PreviewTransition.stopListeningForExclusions(exclusionChanged);removeCallbacks(retry);generation++;pending=false;attached=false;revision=-1;if(id!=0)LiveAngles.detachMirror(id);id=0;result(false,"Live mirror released")}
}
