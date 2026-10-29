package org.duofold.live

import android.graphics.*
import android.os.SystemClock

/** Cosmetic Home texture only. Never enters heldPanels or capture readiness. */
internal class HomePreview(private val source:GlassFrame,val width:Int,val height:Int,val rotation:Int):AutoCloseable {
 private val policy=HomePreviewPolicy()
 private var texture:GlassFrame?=null
 private var alpha=0f
 private var stage="unused"
 private fun prepare():GlassFrame {
  texture?.let{return it}
  // Tiny texture stretches across the destination. Filtering supplies strong
  // frost without full-resolution readback or a per-frame blur allocation.
  val low=source.levels.firstOrNull{maxOf(it.width,it.height)<=24}?:source.bitmap
  val scale=24f/maxOf(width,height)
  val bitmap=Bitmap.createScaledBitmap(low,maxOf(1,(width*scale).toInt()),maxOf(1,(height*scale).toInt()),true)
  val levels=ArrayList<Bitmap>();var mip=bitmap
  repeat(7){mip=Bitmap.createScaledBitmap(mip,maxOf(1,mip.width/2),maxOf(1,mip.height/2),true);levels.add(mip)}
  return GlassFrame(bitmap,width,height,source.stamp,levels=levels,rotation=rotation,exclusions="Home cosmetic frost; never native readiness").also{texture=it}
 }
 fun draw(canvas:Canvas,frame:GlassFrame,nativeReady:Boolean,raw:Float,openThreshold:Float,render:(GlassFrame)->Unit){
  val endpoint=raw.isFinite()&&(raw<=0f||raw>=FoldThreshold.sanitize(openThreshold))
  alpha=policy.opacity(nativeReady,endpoint,SystemClock.elapsedRealtime())
  if(nativeReady)render(frame)
  if(alpha<=0f){stage="native";return}
  stage=if(nativeReady)"dissolving" else "waiting for native Home"
  // Both textures pass through the SAME current-angle stretch/glass painter.
  // Alpha isolates the entire composition, including its opaque backdrop.
  if(alpha>=1f){render(prepare());return}
  val saved=canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(alpha*255).toInt())
  try{render(prepare())}finally{canvas.restoreToCount(saved)}
 }
 fun animating()=policy.animating()
 fun report()="Home frosted preview lastDraw=$stage veilAlpha=${"%.2f".format(java.util.Locale.ROOT,alpha)}; current-pose stretch; cosmetic texture, not native readiness or optical proof"
 override fun close(){texture=null}
}
