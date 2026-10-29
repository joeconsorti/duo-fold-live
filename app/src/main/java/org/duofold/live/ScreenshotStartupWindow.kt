package org.duofold.live

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Pre-attached on D1. It stays transparent until D1 owns the captured physical panel. */
internal class ScreenshotStartupWindow(context:StandaloneService,display:Display){
 private val ctx=context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null)
 private val displays=context.getSystemService(DisplayManager::class.java)
 private val displayId=display.displayId
 private val wm=ctx.getSystemService(WindowManager::class.java)
 private var added=false
 private val life=OverlayOwner()
 private val view=ComposeView(ctx)
 private var physical by mutableStateOf("")
 private var incoming by mutableStateOf(false)
 private var inner by mutableStateOf(false)
 private var configuration by mutableStateOf(android.content.res.Configuration(ctx.resources.configuration))
 private var rootRegistered=false
 private var composedStamp=0L
 private var pendingStamp=0L
 var committedStamp=0L;private set
 var draws=0;private set
 var strength=0f;private set
 val rendererRoute:String get()=if(incoming)"live glass SurfaceView incoming D$displayId" else if(composedStamp>0)"opaque composite outgoing D$displayId" else "idle"
 val attached:Boolean get()=added&&view.isAttachedToWindow
 val windowReport:String get()="Secondary window: TYPE_ACCESSIBILITY_OVERLAY (2032); requested alpha=1.0; view alpha=${view.alpha}; attached=$attached; system attributes in window snapshots"
 private fun identity():String=runCatching{Display::class.java.getMethod("getUniqueId").invoke(displays.getDisplay(displayId)).toString()}.getOrDefault("")
 fun refresh(){
  val next=identity()
  if(next!=physical){physical=next;committedStamp=0;pendingStamp=0;view.alpha=0f}
  val frame=HandoffFrames.frame
  val d=displays.getDisplay(displayId)
  inner=d?.mode?.let{minOf(it.physicalWidth,it.physicalHeight).toFloat()/maxOf(it.physicalWidth,it.physicalHeight)>.7f}?:false
  incoming=!ctx.getSharedPreferences("standalone",0).getBoolean("screenshot_native_test",false)&&ScreenshotRenderPolicy.incoming(frame?.physicalId,next,LiveAngles.dualActive)
  view.alpha=if((frame!=null&&ScreenshotStartupPolicy.ownsFrame(frame.physicalId,next))||incoming)1f else 0f
  configuration=android.content.res.Configuration(ctx.resources.configuration)
  if(view.isAttachedToWindow)runCatching{
   val root=View::class.java.getDeclaredMethod("getViewRootImpl").invoke(view)
   val sc=root.javaClass.getMethod("getSurfaceControl").invoke(root) as android.view.SurfaceControl
   if(sc.isValid){GlassFrames.surface(view,sc);rootRegistered=true}
  }
  view.invalidate()
 }
 init{
  life.registry.currentState=Lifecycle.State.CREATED
  view.setViewTreeLifecycleOwner(life);view.setViewTreeSavedStateRegistryOwner(life)
  view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  view.viewTreeObserver.addOnPreDrawListener{
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
   true
  }
  view.setContent{
   val frame=HandoffFrames.frame?.takeIf{ScreenshotStartupPolicy.ownsFrame(it.physicalId,physical)}
   SideEffect{composedStamp=frame?.stamp ?: 0L}
   CompositionLocalProvider(LocalConfiguration provides configuration,LocalHinge provides null){
    Box(Modifier.fillMaxSize()){
     val input=frame ?: IncomingAnimationFrame.frame?.takeIf{incoming&&it.physicalId==physical}
     if(input!=null){
      DuoLiveShade(object:StandaloneFoldHost{
       override fun onMovement(){}
       override fun onFrame(active:Boolean,strength:Float){this@ScreenshotStartupWindow.strength=strength}
      },ctx.getSharedPreferences("standalone",0).getFloat("intensity",RenderQuality.DEFAULT_GLASS),innerPanel=inner,frozenFrame=frame,opaqueContent=frame!=null,liveFrame=if(frame==null)input else null)
     }
    }
   }
  }
 }
 fun show(){
  // Same service-owned window type as the working live renderer. Non-touchable
  // TYPE_APPLICATION_OVERLAY windows can have their alpha capped by Android.
  // Keep input pass-through without requesting private flags or changing policy.
  val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT)
  params.alpha=1f;params.dimAmount=0f;params.windowAnimations=0
  params.gravity=Gravity.TOP or Gravity.LEFT;params.setFitInsetsTypes(0)
  params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
  params.title="Duo physical outgoing screenshot"
  view.alpha=0f
  wm.addView(view,params);added=true
  life.registry.currentState=Lifecycle.State.RESUMED;refresh()
 }

 fun close(){GlassFrames.surface(view,null);rootRegistered=false;life.registry.currentState=Lifecycle.State.DESTROYED;view.disposeComposition();if(added)runCatching{wm.removeViewImmediate(view)};added=false}
}
