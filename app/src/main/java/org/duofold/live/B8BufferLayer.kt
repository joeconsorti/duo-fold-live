package org.duofold.live

import android.content.Context
import android.graphics.*
import android.hardware.HardwareBuffer
import android.hardware.SyncFence
import android.os.Handler
import android.os.Looper
import android.view.SurfaceControl
import java.time.Duration
import java.util.concurrent.Executors
import java.util.function.Consumer

/** A bounded three-buffer producer, independent of WindowManager/SurfaceView.
 * A buffer is reusable only after the compositor's release fence signals.
 * Root visibility and rendered content are submitted in the SAME transaction.
 */
// Created only for the Android 15+ LiveB8Scene.supported() route.
@android.annotation.TargetApi(35)
internal class B8BufferLayer(
 private val context:Context,private val root:SurfaceControl,
 val width:Int,val height:Int,private val logicalWidth:Int,private val logicalHeight:Int
):AutoCloseable {
 companion object{private val releases=Executors.newSingleThreadExecutor()}
 private val main=Handler(Looper.getMainLooper())
 private var closed=false
 private class Slot(w:Int,h:Int):AutoCloseable {
  val buffer=HardwareBuffer.create(w,h,HardwareBuffer.RGBA_8888,1,
   HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_COMPOSER_OVERLAY)
  val renderer=try{HardwareBufferRenderer(buffer)}catch(e:Exception){buffer.close();throw e}
  val node=RenderNode("DuoB8Frame").apply{setPosition(0,0,w,h)}
  var busy=false;var drawing=false;var closed=false
  init{renderer.setContentRoot(node)}
  override fun close(){if(!closed){closed=true;renderer.close();node.discardDisplayList();buffer.close()}}
 }
 private val slots=ArrayList<Slot>()
 val layer=SurfaceControl.Builder().setName("DuoB8MovingAppPixels").setParent(root).setBufferSize(width,height).build()
 // Prepare one slot alongside capture. Grow only when an unreleased compositor
 // buffer requires it; first presentation no longer allocates all six panel slots.
 init{try{slots.add(Slot(width,height))}catch(e:Exception){close();throw e}}
 fun draw(paint:(Canvas)->Unit,presented:()->Unit,failed:(String)->Unit):Boolean {
  if(closed)return false
  val slot=slots.firstOrNull{!it.busy&&!it.closed}?:if(slots.size<3){
   try{Slot(width,height).also{slots.add(it)}}catch(e:Exception){failed("scene buffer allocation: ${e.javaClass.simpleName}");return false}
  }else return false
  slot.busy=true;slot.drawing=true
  try{
   val canvas=slot.node.beginRecording();try{canvas.drawColor(Color.BLACK);paint(canvas)}finally{slot.node.endRecording()}
   slot.renderer.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB)).draw(context.mainExecutor){result->
    slot.drawing=false
    val fence=result.fence
    if(closed){fence.close();slot.close();return@draw}
    if(result.status!=HardwareBufferRenderer.RenderResult.SUCCESS){fence.close();slot.close();failed("GPU render status=${result.status}");return@draw}
    try{
     SurfaceControl.Transaction().use{t->
      t.setBuffer(layer,slot.buffer,fence,Consumer<SyncFence>{released->
       // Never block the UI, render thread or compositor callback on a fence.
       releases.execute{
        val reusable=try{!released.isValid||released.await(Duration.ofMillis(2000))}catch(e:Exception){false}finally{released.close()}
        main.post{if(closed||!reusable)slot.close() else slot.busy=false}
       }
      }).setDataSpace(layer,android.hardware.DataSpace.DATASPACE_SRGB)
       .setCrop(layer,Rect(0,0,width,height)).setScale(layer,logicalWidth.toFloat()/width,logicalHeight.toFloat()/height)
       .setPosition(layer,0f,0f).setLayer(layer,1).setOpaque(layer,true)
       .setVisibility(layer,true).setVisibility(root,true)
       .addTransactionCompletedListener(context.mainExecutor){if(!closed)presented()}.apply()
     }
    }catch(e:Exception){slot.close();failed("scene submit: ${e.javaClass.simpleName}")}
    finally{fence.close()}
   }
   return true
  }catch(e:Exception){slot.drawing=false;slot.close();failed("scene draw: ${e.javaClass.simpleName}");return false}
 }
 override fun close(){
  if(closed)return;closed=true
  runCatching{SurfaceControl.Transaction().use{it.setVisibility(layer,false).setBuffer(layer,null).reparent(layer,null).apply()}}
  layer.release();root.release()
  slots.filter{!it.drawing}.forEach{it.close()}
 }
}
