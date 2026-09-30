package org.duofold.live

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.view.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Pre-attached on D1. It stays transparent until D1 owns the captured physical panel. */
internal class ScreenshotStartupWindow(context:Context,display:Display){
 private val ctx=context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
 private val displays=context.getSystemService(DisplayManager::class.java)
 private val displayId=display.displayId
 private val dialog=Dialog(ctx)
 private val life=OverlayOwner()
 private val view=ComposeView(ctx)
 private var physical by mutableStateOf("")
 private var composedStamp=0L
 private var pendingStamp=0L
 var committedStamp=0L;private set
 var draws=0;private set
 val attached:Boolean get()=dialog.isShowing&&view.isAttachedToWindow
 private fun identity():String=runCatching{Display::class.java.getMethod("getUniqueId").invoke(displays.getDisplay(displayId)).toString()}.getOrDefault("")
 fun refresh(){
  val next=identity()
  if(next!=physical){physical=next;committedStamp=0;pendingStamp=0;view.alpha=0f}
  val frame=HandoffFrames.frame
  view.alpha=if(frame!=null&&ScreenshotStartupPolicy.ownsFrame(frame.physicalId,next))1f else 0f
  view.invalidate()
 }
 init{
  life.registry.currentState=Lifecycle.State.CREATED
  view.setViewTreeLifecycleOwner(life);view.setViewTreeSavedStateRegistryOwner(life)
  view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  view.viewTreeObserver.addOnDrawListener{
   draws++
   val frame=HandoffFrames.frame;val stamp=composedStamp
   if(frame!=null&&stamp==frame.stamp&&stamp>0&&stamp!=pendingStamp&&ScreenshotStartupPolicy.ownsFrame(frame.physicalId,identity())){
    pendingStamp=stamp
    view.viewTreeObserver.registerFrameCommitCallback{
     if(attached&&HandoffFrames.frame?.stamp==stamp&&ScreenshotStartupPolicy.ownsFrame(frame.physicalId,identity())){
      committedStamp=stamp
      RecoveryLog.add("Outgoing D1 screenshot committed: physical=${frame.physicalId}; stamp=$stamp")
      LiveAngles.handoffReady()
     }
    }
   }
  }
  val intensity=context.getSharedPreferences("standalone",0).getFloat("intensity",1f)
  view.setContent{
   val frame=HandoffFrames.frame?.takeIf{ScreenshotStartupPolicy.ownsFrame(it.physicalId,physical)}
   SideEffect{composedStamp=frame?.stamp ?: 0L}
   CompositionLocalProvider(LocalHinge provides null){
    Box(Modifier.fillMaxSize()){
     if(frame!=null){
      Image(frame.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
      DuoLiveShade(object:StandaloneFoldHost{
       override fun onMovement(){}
       override fun onFrame(active:Boolean,strength:Float){}
      },intensity,minOf(frame.width,frame.height).toFloat()/maxOf(frame.width,frame.height)>.7f,frame)
     }
    }
   }
  }
  dialog.window?.apply{
   setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
   setFormat(PixelFormat.TRANSLUCENT)
   setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
   clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
   addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
   attributes=attributes.apply{dimAmount=0f;setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;title="Duo physical outgoing screenshot"}
  }
  dialog.setContentView(view)
 }
 fun show(){dialog.show();dialog.window?.setLayout(-1,-1);life.registry.currentState=Lifecycle.State.RESUMED;refresh()}
 fun close(){life.registry.currentState=Lifecycle.State.DESTROYED;view.disposeComposition();runCatching{dialog.dismiss()}}
}
