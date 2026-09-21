package org.duofold.live
import android.content.Context
import android.view.*
/** A compositor mirror, not an independently running second app. */
internal class LivePanelSurface(context:Context,private val result:(Boolean,String)->Unit):SurfaceView(context),SurfaceHolder.Callback {
 private var id=0;private var generation=0
 init{holder.addCallback(this);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
 override fun surfaceCreated(holder:SurfaceHolder)=Unit
 override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){
  close();if(width<=0||height<=0||!surfaceControl.isValid)return
  val gen=++generation
  id=LiveAngles.attachMirror(surfaceControl,width,height){ok,note->if(gen==generation)result(ok,note)}
 }
 override fun surfaceDestroyed(holder:SurfaceHolder){close()}
 fun close(){generation++;if(id!=0)LiveAngles.detachMirror(id);id=0;result(false,"Live mirror released")}
}
