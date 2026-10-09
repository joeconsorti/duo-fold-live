package org.duofold.live
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.unit.sp
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The outgoing panel shows only its own pre-switch frame; incoming content stays native.
 * Root build: drawn into a compositor layer on the second display (LayerOverlay), not a Presentation window. A
 * Presentation was created and drew but did not end up on top of the panel's own content.
 */
internal class SecondaryShade(private val service:Context,val display:Display,private val intensity:Float,private val preview:Boolean=false,private val nativeContent:Boolean=false,private val blackOnly:Boolean=false,private val event:(String)->Unit){
 private companion object{
  /** Diagnostic build: purple background + label, so it is obvious whether this layer and its Compose content are visible. */
  const val DEBUG_SECOND_PANEL=false
 }
 private val surface=LayerOverlay(service,display,"Duo secondary panel")
 private val context:Context get()=surface.context
 var isShowing=false;private set
 private var closed=false
 private var dismissListener:(()->Unit)?=null
 fun setOnDismissListener(listener:()->Unit){dismissListener=listener}
 private val life=OverlayOwner()
 private var compose:ComposeView?=null
 private var coverAspect=.63f
 private val frostedReflection=service.getSharedPreferences("standalone",0).getBoolean("frosted_reflection",true)
 private var inner=false
 private var mirrorReady=false
 private var leftStarted by mutableStateOf(false)
 private var mirrorView:LivePanelSurface?=null
 var strength=0f;private set
 var draws=0;private set
 val layer:String get()=if(blackOnly) "Plain Fade opaque inner hold" else if(nativeContent) "Transparent native inner animation" else if(preview) "Live cover preview; ready=$mirrorReady; ${LiveAngles.mirrorStatus}" else "Frozen outgoing ${if(inner) "inner" else "cover"}; own frame=${HandoffFrames.forPanel(inner)!=null}"
 val ready:Boolean get()=isShowing && draws>0 && (if(blackOnly||nativeContent)true else if(preview)mirrorReady else HandoffFrames.forPanel(inner)!=null) && display.state==Display.STATE_ON
 fun refresh(){compose?.invalidate()}
 fun relayout(){if(isShowing)surface.resize()}
 fun show(){
  closed=false
  try{build();surface.show(compose!!);isShowing=true;life.registry.currentState=Lifecycle.State.RESUMED}
  catch(e:Exception){dismiss();throw e}
 }
 private fun build(){
  life.registry.currentState=Lifecycle.State.CREATED
  service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(0)?.mode?.let{coverAspect=it.physicalWidth.toFloat()/it.physicalHeight}
  val mode=display.mode
  inner=minOf(mode.physicalWidth,mode.physicalHeight).toFloat()/maxOf(mode.physicalWidth,mode.physicalHeight)>.7f
  val view=ComposeView(context);compose=view
  view.setViewTreeLifecycleOwner(life);view.setViewTreeSavedStateRegistryOwner(life)
  view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  view.viewTreeObserver.addOnDrawListener{draws++;if(draws==1){event("Frozen ${if(inner) "inner" else "cover"} first draw on display ${display.displayId}");HandoffFrames.forPanel(inner)?.bitmap?.let{b->event("Second-panel frozen frame ${b.width}x${b.height} centre pixel #${Integer.toHexString(b.getPixel(b.width/2,b.height/2))}")}?:event("Second-panel frozen frame: none")}}
  view.setContent {
   CompositionLocalProvider(LocalConfiguration provides context.resources.configuration,LocalHinge provides null){
    val frozen=HandoffFrames.forPanel(inner)
    Box(Modifier.fillMaxSize().background(if(nativeContent)androidx.compose.ui.graphics.Color.Transparent else if(DEBUG_SECOND_PANEL)androidx.compose.ui.graphics.Color(0xFF6A00B8) else androidx.compose.ui.graphics.Color.Black)){
     if(blackOnly){
      // Opaque black only: no wallpaper, frozen frame, or mirrored content.
     }else if(nativeContent){
      DuoLiveShade(object:StandaloneFoldHost{
       override fun onMovement(){}
       override fun onFrame(active:Boolean,strength:Float){this@SecondaryShade.strength=strength}
      },intensity,true)
     }else if(preview){
      AndroidView(factory={LivePanelSurface(it){ok,note->mirrorReady=ok;PreviewTransition.previewReady(ok);if(ok && !leftStarted){leftStarted=true;event("Right preview committed; starting reflected left preview")};event(note)}.also{mirrorView=it}},modifier=Modifier.fillMaxSize())
      BoxWithConstraints(Modifier.fillMaxSize()){
       // Geometry must not depend on the shared frame: surface creation clears that frame.
       val left=maxWidth-maxHeight*coverAspect
       if(!frostedReflection && leftStarted && left.value>0 && coverAspect<.7f){
        Box(Modifier.fillMaxHeight().width(left)){
         DuoLiveShade(object:StandaloneFoldHost{
          override fun onMovement(){}
          override fun onFrame(active:Boolean,strength:Float){}
         },intensity,false,reflectedCover=true)
        }
       }
      }
     }else if(frozen!=null){
      Image(frozen.bitmap.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
      DuoLiveShade(object:StandaloneFoldHost{
       override fun onMovement(){}
       override fun onFrame(active:Boolean,strength:Float){this@SecondaryShade.strength=strength}
      },intensity,inner,frozen)
     }
     if(DEBUG_SECOND_PANEL&&!nativeContent)BasicText("Duo second panel · ${if(preview)"preview" else "frozen"} · ${if(inner)"inner" else "cover"} · frame=${frozen!=null}",style=androidx.compose.ui.text.TextStyle(color=androidx.compose.ui.graphics.Color.White,fontSize=26.sp))
    }
   }
  }
 }
 fun dismiss(){
  if(closed)return
  closed=true
  leftStarted=false;mirrorView?.close();mirrorView=null;mirrorReady=false
  life.registry.currentState=Lifecycle.State.DESTROYED
  compose?.disposeComposition();compose=null
  surface.close();isShowing=false
  dismissListener?.invoke()
 }
}
