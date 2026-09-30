package org.duofold.live

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Attached before concurrent entry, even while the destination panel is OFF.
 * Shows a held outgoing image as a transition preview, never claims native content. */
internal class ScreenshotStartupWindow(context:Context,display:Display,intensity:Float){
 private val life=OverlayOwner()
 private val ctx=context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
 private val dialog=Dialog(ctx)
 private val view=ComposeView(ctx)
 private val inner=display.mode.let{minOf(it.physicalWidth,it.physicalHeight).toFloat()/maxOf(it.physicalWidth,it.physicalHeight)>.7f}
 var draws=0;private set
 val attached:Boolean get()=dialog.isShowing&&view.isAttachedToWindow
 init{
  life.registry.currentState=Lifecycle.State.CREATED
  view.setViewTreeLifecycleOwner(life);view.setViewTreeSavedStateRegistryOwner(life)
  view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  view.viewTreeObserver.addOnDrawListener{draws++}
  view.setContent{
   CompositionLocalProvider(LocalConfiguration provides ctx.resources.configuration,LocalHinge provides null){
    val frozen=HandoffFrames.frame
    if(frozen!=null){
     Box(Modifier.fillMaxSize().background(Color.Black)){
      Image(frozen.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
      DuoLiveShade(object:StandaloneFoldHost{
       override fun onMovement(){}
       override fun onFrame(active:Boolean,strength:Float){}
      },intensity,inner,frozen)
     }
    }
   }
  }
  dialog.window?.apply{
   setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
   setFormat(PixelFormat.TRANSLUCENT)
   setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
   clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
   addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
   attributes=attributes.apply{setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;title="Duo screenshot startup preview"}
  }
  dialog.setContentView(view)
 }
 fun show(){dialog.show();dialog.window?.setLayout(-1,-1);life.registry.currentState=Lifecycle.State.RESUMED}
 fun close(){life.registry.currentState=Lifecycle.State.DESTROYED;view.disposeComposition();runCatching{dialog.dismiss()}}
}
