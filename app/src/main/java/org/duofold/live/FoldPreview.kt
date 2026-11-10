package org.duofold.live

import android.graphics.*
import android.os.SystemClock

internal enum class FoldFrameStyle { STANDARD, FULL_WIDTH_HOME, APP_FROST }

/** Cosmetic texture shared by Home and regular apps. Never enters heldPanels or capture readiness. */
internal class FoldPreview(private val source:GlassFrame,val width:Int,val height:Int,val rotation:Int,private val app:Boolean):AutoCloseable {
 private val policy=FoldPreviewPolicy()
 private var texture:GlassFrame?=null
 private var appLayer:AppFrostLayer?=null
 // One cached elliptical tint: 30% black through the center, 45% at the edges.
 // It shares the frost's opacity layer, so both disappear on the same frame.
 private val dim by lazy { Paint().apply {
  shader=RadialGradient(0f,0f,1f,intArrayOf(0x4d000000,0x4d000000,0x73000000),floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP).apply {
   setLocalMatrix(Matrix().apply {setScale(width/2f,height/2f);postTranslate(width/2f,height/2f)})
  }
 } }
 private var alpha=0f
 private var stage="unused"
 private fun prepare():GlassFrame {
  texture?.let{return it}
  // The tiny texture covers the destination. Filtering supplies strong
  // frost without full-resolution readback or a per-frame blur allocation.
  val low=source.levels.firstOrNull{maxOf(it.width,it.height)<=24}?:source.bitmap
  val scale=(if(app)12f else 24f)/maxOf(width,height)
  val w=maxOf(1,(width*scale).toInt());val h=maxOf(1,(height*scale).toInt())
  val bitmap=if(app){
   // Bake the app's centered, aspect-preserving crop once at preview size.
   val result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
   val fit=LiveMirrorLayout.fill(low.width,low.height,w,h)
   Canvas(result).drawBitmap(low,null,RectF(fit[1],fit[2],fit[1]+low.width*fit[0],fit[2]+low.height*fit[0]),Paint(Paint.FILTER_BITMAP_FLAG))
   result
  }else Bitmap.createScaledBitmap(low,w,h,true)
  val levels=ArrayList<Bitmap>();var mip=bitmap
  repeat(7){mip=Bitmap.createScaledBitmap(mip,maxOf(1,mip.width/2),maxOf(1,mip.height/2),true);levels.add(mip)}
  return GlassFrame(bitmap,width,height,source.stamp,levels=levels,rotation=rotation,exclusions="Cosmetic fold preview").also{texture=it}
 }
 fun draw(canvas:Canvas,frame:GlassFrame,nativeReady:Boolean,raw:Float,openThreshold:Float,render:(Canvas,GlassFrame,FoldFrameStyle)->Unit){
  val endpoint=raw.isFinite()&&(raw<=0f||raw>=FoldThreshold.sanitize(openThreshold))
  alpha=policy.opacity(nativeReady,endpoint,SystemClock.elapsedRealtime())
  if(nativeReady)render(canvas,frame,if(app)FoldFrameStyle.STANDARD else FoldFrameStyle.FULL_WIDTH_HOME)
  if(alpha<=0f){stage="native";return}
  stage=if(nativeReady)"dissolving" else "waiting for destination"
  // Both textures pass through the SAME current-angle stretch/glass painter.
  // Alpha isolates the entire composition, including its opaque backdrop.
  val saved=if(alpha<1f)canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(alpha*255).toInt()) else -1
  try{
   val image=prepare()
   if(app){
    val layer=appLayer?:AppFrostLayer(width,height).also{appLayer=it}
    layer.draw(canvas){target->render(target,image,FoldFrameStyle.APP_FROST)}
   }else render(canvas,image,FoldFrameStyle.FULL_WIDTH_HOME)
   canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),dim)
  }finally{if(saved>=0)canvas.restoreToCount(saved)}
 }
 fun animating()=policy.animating()
 fun report()="Frosted preview input=${texture?.bitmap?.width?:0}x${texture?.bitmap?.height?:0} lastDraw=$stage veilAlpha=${"%.2f".format(java.util.Locale.ROOT,alpha)}; current-pose stretch; cosmetic texture, not native readiness or optical proof"
 override fun close(){appLayer?.close();appLayer=null;texture=null}
}
