package org.duofold.live

import android.view.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Native animation on the inner panel, drawn into a compositor layer (no window, no accessibility service). */
internal class NativeInnerOverlay(service:StandaloneService,display:Display,intensity:Float){
 private val layer=LayerOverlay(service,display,"Duo native inner animation overlay")
 private val context=layer.context
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
  try{layer.show(view);attached=true;owner.registry.currentState=Lifecycle.State.RESUMED}
  catch(e:Exception){dismiss();throw e}
 }
 fun dismiss(){
  if(attached)runCatching{layer.close()}
  attached=false;owner.registry.currentState=Lifecycle.State.DESTROYED;view.disposeComposition()
 }
}
