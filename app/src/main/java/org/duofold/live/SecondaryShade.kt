package org.duofold.live
import androidx.compose.runtime.*
import android.app.Presentation
import android.accessibilityservice.AccessibilityService
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

/** The outgoing panel shows only its own pre-switch frame; incoming content stays native. */
internal class SecondaryShade(private val service:AccessibilityService,display:Display,private val intensity:Float,private val preview:Boolean=false,private val nativeContent:Boolean=false,private val event:(String)->Unit):Presentation(service,display,android.R.style.Theme_Material_NoActionBar){
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
 val layer:String get()=if(nativeContent) "Transparent native inner animation" else if(preview) "Live cover preview; ready=$mirrorReady; ${LiveAngles.mirrorStatus}" else "Frozen outgoing ${if(inner) "inner" else "cover"}; own frame=${HandoffFrames.forPanel(inner)!=null}"
 val ready:Boolean get()=isShowing && draws>0 && (if(nativeContent)true else if(preview)mirrorReady else HandoffFrames.forPanel(inner)!=null) && display.state==Display.STATE_ON
 fun refresh(){compose?.invalidate()}
 override fun onCreate(saved:Bundle?){
  super.onCreate(saved);life.registry.currentState=Lifecycle.State.CREATED
  service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(0)?.mode?.let{coverAspect=it.physicalWidth.toFloat()/it.physicalHeight}
  val mode=display.mode
  inner=minOf(mode.physicalWidth,mode.physicalHeight).toFloat()/maxOf(mode.physicalWidth,mode.physicalHeight)>.7f
  val view=ComposeView(context);compose=view
  view.setViewTreeLifecycleOwner(life);view.setViewTreeSavedStateRegistryOwner(life)
  view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  view.viewTreeObserver.addOnDrawListener{draws++;if(draws==1)event("Frozen ${if(inner) "inner" else "cover"} first draw on display ${display.displayId}")}
  view.setContent {
   CompositionLocalProvider(LocalConfiguration provides context.resources.configuration,LocalHinge provides null){
    val frozen=HandoffFrames.forPanel(inner)
    Box(Modifier.fillMaxSize().background(if(nativeContent)androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color.Black)){
     if(nativeContent){
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
    }
   }
  }
  setContentView(view)
  window?.apply{
   setBackgroundDrawable(ColorDrawable(if(nativeContent)Color.TRANSPARENT else Color.BLACK));if(nativeContent)setFormat(android.graphics.PixelFormat.TRANSLUCENT) else addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
   addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
   attributes=attributes.apply{setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;title=if(nativeContent)"Duo native inner animation" else if(preview)"Duo live cover preview" else "Duo outgoing frozen panel"}
  }
 }
 override fun onStart(){super.onStart();window?.setLayout(-1,-1);life.registry.currentState=Lifecycle.State.RESUMED}
 override fun onStop(){if(life.registry.currentState!=Lifecycle.State.DESTROYED)life.registry.currentState=Lifecycle.State.CREATED;super.onStop()}
 override fun dismiss(){leftStarted=false;mirrorView?.close();mirrorView=null;mirrorReady=false;life.registry.currentState=Lifecycle.State.DESTROYED;compose?.disposeComposition();compose=null;super.dismiss()}
}
