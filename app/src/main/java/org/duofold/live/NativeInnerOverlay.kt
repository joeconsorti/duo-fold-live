package org.duofold.live

import android.content.Context
import android.graphics.PixelFormat
import android.view.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Native animation uses an accessibility window, not a presentation-only display. */
internal class NativeInnerOverlay(service:StandaloneService,display:Display,intensity:Float){
 private val context=service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null)
 private val wm=context.getSystemService(WindowManager::class.java)
 private val owner=OverlayOwner()
 private val view=ComposeView(context)
 var draws=0;private set
 var strength=0f;private set
 var attached=false;private set
 init{
  owner.registry.currentState=Lifecycle.State.CREATED
  view.setViewTreeLifecycleOwner(owner);view.setViewTreeSavedStateRegistryOwner(owner)
  view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  view.viewTreeObserver.addOnDrawListener{draws++}
  view.setContent{
   CompositionLocalProvider(LocalConfiguration provides context.resources.configuration,LocalHinge provides null){
    DuoLiveShade(object:StandaloneFoldHost{
     override fun onMovement(){}
     override fun onFrame(active:Boolean,strength:Float){this@NativeInnerOverlay.strength=strength}
    },intensity,true)
   }
  }
 }
 fun show(){
  val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT)
  params.setFitInsetsTypes(0);params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
  params.title="Duo native inner animation overlay"
  try{wm.addView(view,params);attached=true;owner.registry.currentState=Lifecycle.State.RESUMED}
  catch(e:Exception){dismiss();throw e}
 }
 fun dismiss(){
  if(attached)runCatching{wm.removeViewImmediate(view)}
  attached=false;owner.registry.currentState=Lifecycle.State.DESTROYED;view.disposeComposition()
 }
}
