package org.duofold.live

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.ViewGroup

/** Two persistent buffers; standby pixels are never destination-readiness evidence. */
internal class PreparedGlassHost(context:Context):ViewGroup(context) {
 private val panels= arrayOf(FrostSurface(context),FrostSurface(context))
 private val sizes= arrayOf(Point(),Point())
 private val dm=context.getSystemService(DisplayManager::class.java)
 private val identities=HashMap<String,Int>()
 private var lastIntensity=RenderQuality.DEFAULT_GLASS
 private var configured=false
 private var selected=-1
 private val captureListener:()->Unit={if(configured)selectPrimary()}
 private val displayListener=object:DisplayManager.DisplayListener{
  override fun onDisplayAdded(id:Int){if(configured)selectPrimary()}
  override fun onDisplayRemoved(id:Int){if(configured)selectPrimary()}
  override fun onDisplayChanged(id:Int){if(id==0 && configured)selectPrimary()}
 }
 override fun onAttachedToWindow(){
  super.onAttachedToWindow()
  dm.registerDisplayListener(displayListener,Handler(Looper.getMainLooper()))
  GlassFrames.listen(captureListener)
 }
 override fun onDetachedFromWindow(){
  GlassFrames.unlisten(captureListener);dm.unregisterDisplayListener(displayListener)
  super.onDetachedFromWindow()
 }
 private fun selectPrimary():Boolean {
  val d=dm.getDisplay(0) ?: return false
  val slot=identities[CoverBridge.identity(d)] ?: return false
  if(!LiveAngles.fresh() || !LiveAngles.effectAllowed)return false
  // Identity survives logical display remapping. Cached per-panel geometry avoids
  // accepting the outgoing mode briefly reported alongside the incoming identity.
  GlassFrames.primaryChanged(d)
  val angle=LiveAngles.currentAngle()
  val prefs=context.getSharedPreferences("standalone",0)
  select(slot)
  panels[slot].configure(GlassFrames.frame,DuoShadeCurve.progress(angle,slot==1,prefs.getFloat("open_threshold",172f)),lastIntensity,slot==1,d.rotation,angle=angle)
  return true
 }
 private fun select(target:Int){
  if(selected==target)return
  panels.forEachIndexed { i,p -> p.preparedStandby=i!=target }
  selected=target
  HandoffStartupTrace.event("PREPARED_RENDERER_SELECT","inner=${target==1}; geometry=${sizes[target].x}x${sizes[target].y}; direct display/capture delivery; surfaceValid=${panels[target].holder.surface.isValid}",false)
 }
 private var warmAt=0L
 private var warmStamp=-1L
 init {
  clipChildren=true
  panels.forEach { it.preparedStandby=true;it.alpha=0f;addView(it,LayoutParams(1,1)) }
 }
 fun configure(frame:GlassFrame?,amount:Float,intensity:Float,inner:Boolean,rotation:Int,angle:Float){
  configured=true;lastIntensity=intensity
  val target=if(inner)1 else 0
  // Query physical mode sizes, not stale bounds inherited by D0 during a remap.
  dm.displays.forEach { d ->
   val mode=d.mode
   val slot=if(minOf(mode.physicalWidth,mode.physicalHeight).toFloat()/maxOf(mode.physicalWidth,mode.physicalHeight)>.7f)1 else 0
   val identity=CoverBridge.identity(d)
   if(d.state==Display.STATE_ON && identity!="unknown" && !identities.containsKey(identity))identities[identity]=slot
   // Ignore transient mode/identity disagreement during a remap.
   if(identities[identity]!=null && identities[identity]!=slot)return@forEach
   val geometry=LivePanelGeometry.resolve(mode.physicalWidth,mode.physicalHeight,rotation,0,0)
   if(sizes[slot].x!=geometry[0] || sizes[slot].y!=geometry[1]){
    sizes[slot].set(geometry[0],geometry[1]);requestLayout()
    RecoveryLog.add("Prepared glass geometry ${if(slot==1) "inner" else "cover"}: ${geometry[0]}x${geometry[1]}")
   }
  }
  if(!selectPrimary()){
   select(target)
   panels[target].configure(frame,amount,intensity,inner,rotation,angle=angle)
  }
  val other=1-selected
  val now=SystemClock.elapsedRealtime()
  if(frame!=null && sizes[other].x>0 && frame.stamp!=warmStamp && now-warmAt>=180 && LiveAngles.fresh() && LiveAngles.effectAllowed){
   warmAt=now;warmStamp=frame.stamp
   // Exercise the destination shader with bounded, hidden outgoing content. A switch
   // reconfigures this surface with live validation before revealing new draws.
   panels[other].configure(frame,DuoShadeCurve.progress(angle,other==1,context.getSharedPreferences("standalone",0).getFloat("open_threshold",172f)),intensity,other==1,rotation,frozen=true,angle=angle)
  }
 }
 override fun onMeasure(w:Int,h:Int){
  setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h))
  panels.forEachIndexed { i,p ->
   val s=sizes[i]
   p.measure(MeasureSpec.makeMeasureSpec(maxOf(1,s.x),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(maxOf(1,s.y),MeasureSpec.EXACTLY))
  }
 }
 override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
  panels.forEach { it.layout(0,0,it.measuredWidth,it.measuredHeight) }
 }
}
