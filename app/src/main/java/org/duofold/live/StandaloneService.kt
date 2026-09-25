package org.duofold.live

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.*
import androidx.savedstate.*

/** Persistent display overlays and lifecycle recovery for Duo fold rendering. */
class StandaloneService : AccessibilityService(), DisplayManager.DisplayListener, StandaloneFoldHost {
    companion object { var instance: StandaloneService? = null; private set }
    private val handler=Handler(Looper.getMainLooper())
    private var view: ComposeView?=null
    private var secondary:SecondaryShade?=null
    private var nativeOverlay:NativeInnerOverlay?=null
    @Volatile private var secondaryReadySnapshot=false
    private var secondaryRetry=0L
    private var secondaryError=""
    private var primaryKey=""
    private var secondaryKey=""
    private var owner: OverlayOwner?=null
    private var windowContext: Context?=null
    private lateinit var wm: WindowManager
    private lateinit var displays: DisplayManager
    private var config by mutableStateOf(Configuration())
    private var image by mutableStateOf<Bitmap?>(null)
    private var frameBounds by mutableStateOf(Rect())
    private var capturedWindow=-1
    private val liveVeil=true
    private var sourceWasFresh=false
    private var active=false
    private var strength=0f
    private var generation=0
    private var pending=false
    private val captureGate=CaptureGate()
    private var captureQueued=false
    private var screenWidth=0
    private var screenHeight=0
    private val history=ArrayDeque<String>()
    var status="Service starting"; private set
    private fun note(message:String){status=message;history.addLast("${SystemClock.uptimeMillis()} $message");while(history.size>50)history.removeFirst()}
    private val captureTask=Runnable { captureQueued=false }
    private val screenReceiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){
        RecoveryLog.add("Overlay received ${i.action}")
        if(i.action==Intent.ACTION_SCREEN_OFF){GlassFrames.suspendCapture();handler.postDelayed({if(!usable())HandoffFrames.clear()},300);owner?.registry?.currentState=Lifecycle.State.CREATED;clearUnavailableEffect()}
        else if(i.action==Intent.ACTION_USER_PRESENT){
            // Recreate the composition after unlock: old window IDs and stopped frame clocks are invalid.
            handler.postDelayed({if(instance===this@StandaloneService && settings().getBoolean("enabled",false)){restart();RecoveryLog.add("Overlay rebuilt after unlock")}},150)
        } else {updateDisplay();resumeIfUsable()}
    }}
    private fun usable()=::displays.isInitialized && displays.getDisplay(Display.DEFAULT_DISPLAY)?.state==Display.STATE_ON && !getSystemService(KeyguardManager::class.java).isKeyguardLocked()
    private fun previewMode()=AnimationModePolicy.mirrors(settings().getString("animation_mode",AnimationModePolicy.DEFAULT)) && LiveAngles.effectAllowed && settings().getBoolean("cover_preview",true) && !settings().getBoolean("dual",false)
    private fun settings()=getSharedPreferences("standalone",0)
    override fun onServiceConnected(){
        instance=this;RecoveryLog.init(this);RecoveryLog.add("Accessibility service connected");handler.post(sourceGuard);displays=getSystemService(DisplayManager::class.java)
        displays.registerDisplayListener(this,handler)
        registerReceiver(screenReceiver,IntentFilter().apply {addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)},Context.RECEIVER_NOT_EXPORTED)
        restart()
    }
    fun restart(){
        removeHost()
        if(!settings().getBoolean("enabled",false)){note("Off — turn on Enable fold animation");return}
        runCatching { startForegroundService(Intent(this, FoldBackgroundService::class.java)) }.onFailure { note("Open Duo and tap Start background animation") }
        val display=displays.getDisplay(Display.DEFAULT_DISPLAY)?:return
        primaryKey=physicalKey(display)
        val context=createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null)
        windowContext=context;wm=context.getSystemService(WindowManager::class.java)
        config=Configuration(context.resources.configuration)
        val bounds=wm.currentWindowMetrics.bounds;screenWidth=bounds.width();screenHeight=bounds.height()
        val lifecycle=OverlayOwner();owner=lifecycle
        lifecycle.registry.currentState=Lifecycle.State.CREATED
        val compose=ComposeView(context);view=compose;compose.alpha=0f
        compose.viewTreeObserver.addOnDrawListener{
            val inner=minOf(screenWidth,screenHeight).toFloat()/maxOf(screenWidth,screenHeight)>.7f
            // The glass SurfaceView supplies its own frame-bound completion.
            val glass=AnimationModePolicy.mirrors(settings().getString("animation_mode",AnimationModePolicy.DEFAULT)) && !settings().getBoolean("debug_mode",false)
            if(compose.alpha>0f && (!inner || !glass))HandoffFadeFrames.drawn(inner)
        }
        compose.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        compose.setViewTreeLifecycleOwner(lifecycle);compose.setViewTreeSavedStateRegistryOwner(lifecycle)
        val intensity=settings().getFloat("intensity",1f)
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides config, LocalHinge provides rememberWindowHinge(context)) {
                DuoLiveShade(this@StandaloneService,intensity)
            }
        }
        val lp=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT)
        lp.gravity=Gravity.TOP or Gravity.LEFT;lp.setFitInsetsTypes(0)
        lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        lp.title="Duo Fold Live"
        try {wm.addView(compose,lp);if(previewMode())PreviewTransition.markAnimation(compose);resumeIfUsable();requestCapture();note("Ready — Duo live shading running")}
        catch(e:Exception){removeHost();note("Could not attach overlay: ${e.javaClass.simpleName}")}
    }
    private fun resumeIfUsable(){if(usable()){GlassFrames.resumeCapture();owner?.registry?.currentState=Lifecycle.State.RESUMED}}
    override fun onMovement(){requestCapture()}
    override fun onFrame(active:Boolean,strength:Float){
        val changed=this.active!=active;this.active=active;this.strength=strength
        if(!usable() || !LiveAngles.fresh() || !LiveAngles.effectAllowed){hide();this.strength=0f;return}
        view?.alpha=if(active && (liveVeil || image!=null))1f else 0f
        if(changed && !active)SupportPrompts.recordUse(this)
        if(changed)note(if(active)"Duo transition active" else "Ready — Duo transition complete")
        if(active)requestCapture()
    }
    fun clearUnavailableEffect(){
        dismissSecondary();hide();strength=0f;generation++;pending=false;image=null
    }
    private val sourceGuard=object:Runnable {override fun run(){
        val fresh=LiveAngles.fresh()
        if(!fresh && (active || sourceWasFresh)){hide();note("Effect paused — waiting for fresh angles; panel session retained")}
        sourceWasFresh=fresh
        refreshSecondary()
        handler.postDelayed(this,250)
    }}
    private fun hide(){view?.alpha=0f;active=false;handler.removeCallbacks(captureTask);captureQueued=false}
    override fun onAccessibilityEvent(event:AccessibilityEvent)=Unit
    private fun requestCapture()=Unit // Retained host callback; this release never captures pixels.
    private fun updateDisplay(){
        if(view==null)return
        val display=displays.getDisplay(0)?:return
        if(physicalKey(display)!=primaryKey){RecoveryLog.add("Primary physical panel changed; rebuilding overlay");restart();return}
        val next=Configuration(windowContext!!.resources.configuration)
        val bounds=wm.currentWindowMetrics.bounds
        if(screenWidth!=bounds.width()||screenHeight!=bounds.height()){
            screenWidth=bounds.width();screenHeight=bounds.height();generation++;pending=false;image=null;view?.alpha=0f
        }
        // Keep the composition alive: Duo retains its timeline and both snapshot-morph images.
        config=next;requestCapture()
    }
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);handler.post{updateDisplay()}}
    override fun onDisplayChanged(id:Int){handler.post{if(id==Display.DEFAULT_DISPLAY){updateDisplay();resumeIfUsable()};refreshSecondary()}}
    override fun onDisplayAdded(id:Int){handler.post{refreshSecondary()}}
    override fun onDisplayRemoved(id:Int){handler.post{refreshSecondary()}}
    private fun removeHost(){
        dismissSecondary();generation++;pending=false;hide();FoldBridgeActivity.cancel()
        owner?.registry?.currentState=Lifecycle.State.DESTROYED
        view?.let {it.disposeComposition();runCatching {wm.removeViewImmediate(it)}}
        view=null;owner=null;image=null;windowContext=null
    }
    fun secondaryReady():Boolean=secondaryReadySnapshot
    private fun dismissSecondary(){nativeOverlay?.dismiss();nativeOverlay=null;secondaryReadySnapshot=false;val old=secondary;secondary=null;runCatching{old?.dismiss()}}
    fun refreshSecondary(){
        if(!::displays.isInitialized)return
        val current=displays.getDisplay(0)
        val primaryInner=current?.mode?.let{minOf(it.physicalWidth,it.physicalHeight).toFloat()/maxOf(it.physicalWidth,it.physicalHeight)>.7f}?:false
        PreviewTransition.update(!LiveAngles.nativeInner && previewMode() && settings().getBoolean("enabled",false) && !getSystemService(KeyguardManager::class.java).isKeyguardLocked(),primaryInner)
        val native=LiveAngles.continuityNative && !primaryInner
        val preview=!settings().getBoolean("dual",false) && settings().getBoolean("cover_preview",true) && LiveAngles.coverPreview
        if(!LiveAngles.effectAllowed||!settings().getBoolean("enabled",false)||(!native && !preview && (!settings().getBoolean("dual",false)||!LiveAngles.dualActive))||!usable()){dismissSecondary();return}
        val target=displays.displays.firstOrNull{it.displayId==1 && BuiltInPanel.accepts(it)}
        if(target==null){secondaryError="Concurrent mode has not exposed the second built-in panel";dismissSecondary();return}
        if(native){
            val key=physicalKey(target)+":native-overlay"
            if(secondaryKey==key && nativeOverlay?.attached==true){secondaryReadySnapshot=nativeOverlay!!.draws>0;return}
            if(SystemClock.elapsedRealtime()<secondaryRetry)return
            dismissSecondary()
            try{
                val overlay=NativeInnerOverlay(this,target,settings().getFloat("intensity",1f));nativeOverlay=overlay
                overlay.show();secondaryKey=key;secondaryError="";RecoveryLog.add("Native inner accessibility overlay attached on display 1")
            }catch(e:Exception){dismissSecondary();secondaryError="${e.javaClass.simpleName}: ${e.message}";secondaryRetry=SystemClock.elapsedRealtime()+1500;RecoveryLog.add("Native inner overlay error: $secondaryError")}
            return
        }
        if(nativeOverlay!=null)dismissSecondary()
        if(secondaryKey==physicalKey(target)+":"+preview+":"+native && secondary?.display?.displayId==target.displayId && secondary?.isShowing==true){secondary?.refresh();secondaryReadySnapshot=secondary?.ready==true;return}
        if(SystemClock.elapsedRealtime()<secondaryRetry)return
        dismissSecondary()
        try{
            val next=SecondaryShade(this,target,settings().getFloat("intensity",1f),preview,native){RecoveryLog.add(it)}
            next.setOnDismissListener{RecoveryLog.add("Secondary Presentation dismissed");if(secondary===next){secondary=null;secondaryReadySnapshot=false}}
            secondary=next;secondaryKey=physicalKey(target)+":"+preview+":"+native;next.show();secondaryError="";RecoveryLog.add("Secondary Presentation shown, display ${target.displayId}")
        }catch(e:Exception){dismissSecondary();secondaryError="${e.javaClass.simpleName}: ${e.message}";secondaryRetry=SystemClock.elapsedRealtime()+1500;RecoveryLog.add("Secondary Presentation error: $secondaryError")}
    }
    private fun physicalKey(d:Display)="${d.displayId}:${d.mode.physicalWidth}x${d.mode.physicalHeight}"
    private fun displayReport()=if(!::displays.isInitialized)"unavailable" else displays.displays.joinToString("; "){d->
        "id=${d.displayId} state=${d.state} mode=${d.mode.physicalWidth}x${d.mode.physicalHeight} rotation=${d.rotation}"
    }
    fun report()="Duo Fold Live ${BuildConfig.VERSION_NAME}\nSelectable glass / live angles\n${Build.MODEL} / Android ${Build.VERSION.RELEASE}\n$status\n${LiveAngles.status}\n${FoldAwakeDefault.status}\n${RotationMigration.status}\nCustom wallpaper: ${org.duofold.live.wallpaperlayer.WallpaperRestore.status}\nBackground service: ${FoldBackgroundService.running}\n${FoldBackgroundService.connectionReport()}\n${LiveAngles.latencyReport()}\nAngle age: ${LiveAngles.ageMs()} ms\nCover preview: ${settings().getBoolean("cover_preview",true)}\nPreview bridge: ${LiveAngles.expansionStatus}\nPreview preparation: ${PreviewTransition.status}\nBridge trace: ${LiveAngles.bridgeTrace}\nSmoothing: ${settings().getFloat("smoothing_ms",12f)} ms; full-resolution glass: ${settings().getBoolean("full_resolution_glass",false)}\nMode: ${if(settings().getBoolean("debug_mode",false)) "Debug black fade" else "Projected glass"}\n${LiveAngles.handoffStatus}\n${LiveAngles.handoffFade}\n${LiveAngles.rotationHold}\n${LiveAngles.continuityStatus}\n${InnerDecorRecovery.apiStatus}\n${InnerDecorRecovery.status}\n${LiveAngles.continuityTrace}\nGlass: ${GlassFrames.status}\nHandoff: ${HandoffFrames.status}\nSecondary ready: ${secondaryReady()}; draws: ${nativeOverlay?.draws ?: secondary?.draws ?: 0}\nSecondary layer: ${if(nativeOverlay!=null)"Native inner accessibility overlay" else secondary?.layer ?: "none"}; strength: ${nativeOverlay?.strength ?: secondary?.strength ?: 0f}\nSecondary error: $secondaryError\nDisplays: ${displayReport()}\nAnimation: ${AnimationModePolicy.label(settings().getString("animation_mode",AnimationModePolicy.DEFAULT))}\n${FrameTelemetry.report()}\nStrength: $strength\n"+history.joinToString("\n")+"\nLifecycle / connection events:\n"+RecoveryLog.report()+"\n24-hour health samples (app process only):\n"+HealthTrace.report(this)
    override fun onInterrupt(){hide();image=null;note("Feedback interrupted — overlay remains attached")}
    override fun onDestroy(){PreviewTransition.update(false,false);removeHost();if(::displays.isInitialized)displays.unregisterDisplayListener(this);runCatching {unregisterReceiver(screenReceiver)};handler.removeCallbacksAndMessages(null);instance=null;super.onDestroy()}
}
internal class OverlayOwner:LifecycleOwner,SavedStateRegistryOwner {
    val registry=LifecycleRegistry(this)
    private val controller=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=controller.savedStateRegistry
    init {controller.performAttach();controller.performRestore(null)}
}
