package org.duofold.live

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader

/** The fixed Home waiting veil, scoped to the temporary incoming app image.
 * Record the changing hinge pose at a small raster so the full-panel blur is bounded. */
internal class AppFrostLayer(private val width:Int,private val height:Int):AutoCloseable {
 private val scale=96f/maxOf(width,height)
 private val w=maxOf(1,(width*scale).toInt())
 private val h=maxOf(1,(height*scale).toInt())
 private val tint=Paint().apply {
  shader=LinearGradient(0f,0f,0f,height.toFloat(),Color.argb(18,255,255,255),Color.argb(24,0,0,0),Shader.TileMode.CLAMP)
 }
 private var node:RenderNode?=null

 fun draw(canvas:Canvas,render:(Canvas)->Unit){
  if(canvas.isHardwareAccelerated){
   val layer=node?:RenderNode("Duo incoming app frost").apply {
    setPosition(0,0,w,h)
    val radius=minOf(w,h)*.035f
    setRenderEffect(RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP))
   }.also{node=it}
   val recording=layer.beginRecording(w,h)
   try{
    recording.scale(w.toFloat()/width,h.toFloat()/height)
    render(recording)
   }finally{layer.endRecording()}
   val saved=canvas.save()
   try{
    canvas.scale(width.toFloat()/w,height.toFloat()/h)
    canvas.drawRenderNode(layer)
   }finally{canvas.restoreToCount(saved)}
  }else render(canvas) // The cached coarse image still covers a software canvas.
  canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),tint)
 }

 override fun close(){node?.discardDisplayList();node?.setRenderEffect(null);node=null}
}
