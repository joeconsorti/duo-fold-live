package org.duofold.live

import android.content.Context
import android.graphics.*
import android.hardware.HardwareBuffer
import android.hardware.SyncFence
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceControl
import java.time.Duration
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.concurrent.TimeUnit

/** A bounded three-buffer producer, independent of WindowManager/SurfaceView.
 * A buffer is reusable only after the compositor's release fence signals.
 * Root visibility and rendered content are submitted in the SAME transaction.
 */
// Created only for the Android 15+ LiveB8Scene.supported() route.
@android.annotation.TargetApi(35)
internal class B8BufferLayer(
 private val context:Context,private val root:SurfaceControl,
 width:Int,height:Int,logicalWidth:Int,logicalHeight:Int
):AutoCloseable {
 var width=width;private set
 var height=height;private set
 var preferredFrameRate=60f
 private var logicalWidth=logicalWidth
 private var logicalHeight=logicalHeight
 companion object{private val releases=Executors.newSingleThreadScheduledExecutor{job->Thread(job,"Duo-buffer-release").apply{isDaemon=true}}}
 private val main=Handler(Looper.getMainLooper())
 @Volatile private var closed=false
 private var fenceTimeouts=0;private var bufferMisses=0;private var maxFenceMs=0L
 fun report()="buffers=${slots.size} busy=${slots.count{it.busy&&!it.closed}} retired=${slots.count{it.closed}} bufferMisses=$bufferMisses fenceTimeouts=$fenceTimeouts maxFenceMs=$maxFenceMs refreshVoteHz=$preferredFrameRate vsyncPaced=true"
 private class Slot(val w:Int,val h:Int):AutoCloseable {
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
 fun resize(w:Int,h:Int,logicalW:Int,logicalH:Int){
  check(!closed&&slots.none{it.drawing}){"Resize while GPU frame pending"}
  require(w>0&&h>0&&logicalW>0&&logicalH>0)
  width=w;height=h;logicalWidth=logicalW;logicalHeight=logicalH
  // Do not hide, clear or replace the layer. Compositor-owned old-size slots
  // count toward the same three-slot cap until their release fences signal.
 }
 fun draw(paint:(Canvas)->Unit,presented:()->Unit,failed:(String)->Unit,rootCrop:()->Rect?,canSubmit:()->Boolean,latched:()->Unit):Boolean {
  if(closed)return false
  slots.filter{!it.busy&&!it.drawing&&(it.w!=width||it.h!=height)}.forEach{it.close()}
  slots.removeAll{it.closed&&!it.drawing} // Timed-out fences retire buffers; they must not permanently exhaust capacity.
  val slot=slots.firstOrNull{!it.busy&&!it.closed&&it.w==width&&it.h==height}?:if(slots.size<3){
   try{Slot(width,height).also{slots.add(it)}}catch(e:Exception){failed("scene buffer allocation: ${e.javaClass.simpleName}");return false}
  }else{bufferMisses++;return false}
  slot.busy=true;slot.drawing=true
  try{
   val canvas=slot.node.beginRecording();try{canvas.drawColor(Color.BLACK);paint(canvas)}finally{slot.node.endRecording()}
   slot.renderer.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB)).draw(context.mainExecutor){result->
    slot.drawing=false
    val fence=result.fence
    if(closed){fence.close();slot.close();return@draw}
    if(result.status!=HardwareBufferRenderer.RenderResult.SUCCESS){fence.close();slot.close();failed("GPU render status=${result.status}");return@draw}
    try{
     // A display can rotate while the GPU finishes the frame. Keep the previous buffer installed.
     if(!canSubmit()){slot.close();return@draw}
     SurfaceControl.Transaction().use{t->
      t.setBuffer(layer,slot.buffer,fence,Consumer<SyncFence>{released->
       // Zero-time fence checks: one slow release cannot head-of-line block
       // either panel's other buffers. Only signal permits reuse, never timeout.
       val began=System.nanoTime()
       releases.execute(object:Runnable{override fun run(){
        val elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)
        var error=false
        val reusable=try{!released.isValid||released.await(Duration.ZERO)}catch(e:Exception){error=true;false}
        if(!closed&&!reusable&&!error&&elapsed<2000){releases.schedule(this,8,TimeUnit.MILLISECONDS);return}
        released.close()
        main.post{
         maxFenceMs=maxOf(maxFenceMs,elapsed)
         if(!reusable&&!closed)fenceTimeouts++
         if(closed||!reusable)slot.close() else slot.busy=false
        }
       }})
      }).setDataSpace(layer,android.hardware.DataSpace.DATASPACE_SRGB)
       .setCrop(layer,Rect(0,0,width,height)).setScale(layer,logicalWidth.toFloat()/width,logicalHeight.toFloat()/height)
       .setPosition(layer,0f,0f).setLayer(layer,1).setOpaque(layer,true)
       .setVisibility(layer,true).setCrop(root,rootCrop()).setVisibility(root,true)
       .setFrameRate(layer,preferredFrameRate,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
       // Latch advances producer pacing, but only completion acknowledges
       // presentation. Release fences above remain the sole reuse authority.
       .addTransactionCommittedListener(context.mainExecutor){if(!closed)latched()}
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
