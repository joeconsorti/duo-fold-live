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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.*
import androidx.savedstate.*

/** Persistent display overlays and lifecycle recovery for Duo fold rendering. */
class StandaloneService : AccessibilityService(), DisplayManager.DisplayListener, StandaloneFoldHost {
    companion object { var instance: StandaloneService? = null; private set }
    private val handler=Handler(Looper.getMainLooper())
    private var displayObserver:HandlerThread?=null
    private val displayEventAt=java.util.concurrent.atomic.AtomicLong(0L)
    private val displayUpdate=Runnable {
        val observed=displayEventAt.getAndSet(0L)
        if(instance===this && ::displays.isInitialized){
            val started=SystemClock.elapsedRealtime()
            val before=primaryKey
            HandoffStartupTrace.event("MAIN_DISPLAY_UPDATE","callback=$observed; queue=${started-observed}ms; hostAlpha=${view?.alpha}")
            updateDisplay();resumeIfUsable()
            if(before!=primaryKey)RecoveryLog.add("Overlay remap callback=$observed; main queue=${started-observed} ms; update=${SystemClock.elapsedRealtime()-started} ms; $before -> $primaryKey")
            refreshSecondary()
        }
    }
    private fun queueDisplayUpdate(){
        displays.getDisplay(0)?.let{HandoffStartupTrace.observe(it)}
        if(displayEventAt.compareAndSet(0L,SystemClock.elapsedRealtime()))handler.post(displayUpdate)
    }
    private var view: ComposeView?=null
    private var screenshotWindow:ScreenshotStartupWindow?=null
    private var screenshotKey=""
    @Volatile private var screenshotPrepared=false
    private var screenshotRetry=0L
    private var composedScreenshotStamp=0L
    private var commitPendingStamp=0L
    @Volatile private var committedScreenshotStamp=0L
    fun screenshotWindowReady():Boolean=screenshotPrepared
    fun screenshotFrameReady():Boolean=ScreenshotStartupPolicy.presented(HandoffFrames.frame?.stamp ?: 0L,committedScreenshotStamp)

    private fun closeScreenshotWindow(){screenshotPrepared=false;screenshotWindow?.close();screenshotWindow=null;screenshotKey=""}
    private var secondary:SecondaryShade?=null
    private var nativeOverlay:NativeInnerOverlay?=null
    @Volatile private var secondaryReadySnapshot=false
    private var secondaryRetry=0L
    private var secondaryError=""
    private var primaryKey=""
    private var retainedPrimaryHost=false
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
    private var transientOffStarted=0L
    private var sourceWasFresh=false
    private var active=false
    private var strength=0f
    private var generation=0
    private var pending=false
    private val captureGate=CaptureGate()
    private var captureQueued=false
    private var screenWidth by mutableIntStateOf(0)
    private var screenHeight by mutableIntStateOf(0)
    private val history=ArrayDeque<String>()
    var status="Service starting"; private set
    private fun note(message:String){status=message;history.addLast("${SystemClock.uptimeMillis()} $message");while(history.size>50)history.removeFirst()}
    private val captureTask=Runnable { captureQueued=false }
    private val screenReceiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){
        RecoveryLog.add("Overlay received ${i.action}");diagnosticState("broadcast ${i.action}",true)
        if(i.action==Intent.ACTION_SCREEN_OFF){GlassFrames.suspendCapture();handler.postDelayed({if(!usable())HandoffFrames.clear()},300);owner?.registry?.currentState=Lifecycle.State.CREATED;clearUnavailableEffect()}
        else if(i.action==Intent.ACTION_USER_PRESENT){
            // Recreate the composition after unlock: old window IDs and stopped frame clocks are invalid.
            handler.postDelayed({if(instance===this@StandaloneService && settings().getBoolean("enabled",false)){restart();RecoveryLog.add("Overlay rebuilt after unlock")}},150)
        } else {updateDisplay();resumeIfUsable()}
    }}
    private fun motionUsable()=RuntimeConsent.ready(this) && getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private val motionListener=LiveAngles.Listener { value,_ -> MotionGlass.angle(value);if(settings().getBoolean("motion_glass",false))MotionGlass.refresh(this,motionUsable()) }
    private fun usable()=RuntimeConsent.ready(this) && ::displays.isInitialized && displays.getDisplay(Display.DEFAULT_DISPLAY)?.state==Display.STATE_ON && !getSystemService(KeyguardManager::class.java).isKeyguardLocked()
    private fun previewMode()=AnimationModePolicy.mirrors(settings().getString("animation_mode",AnimationModePolicy.DEFAULT)) && LiveAngles.effectAllowed && settings().getBoolean("cover_preview",true) && !settings().getBoolean("dual",false)
    private val runtimeSettings by lazy { RuntimeSettings.get(this) }
    private fun settings()=runtimeSettings
    override fun onServiceConnected(){
        instance=this;RecoveryLog.init(this);RecoveryLog.add("Accessibility service connected");handler.post(sourceGuard);displays=getSystemService(DisplayManager::class.java);LiveAngles.add(motionListener)
        // Observe delivery independently of main-thread rendering; coalesce bursts into one update.
        displayObserver=HandlerThread("duo-overlay-display").also{it.start()}
        displays.registerDisplayListener(this,Handler(displayObserver!!.looper))
        registerReceiver(screenReceiver,IntentFilter().apply {addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)},Context.RECEIVER_NOT_EXPORTED)
        restart()
    }
    fun restart(){
        retainedPrimaryHost=false
        removeHost()
        if(!RuntimeConsent.ready(this) || !settings().getBoolean("enabled",false)){note("Off — turn on Enable fold animation");return}
        runCatching { startForegroundService(Intent(this, FoldBackgroundService::class.java)) }.onFailure { note("Open Duo and tap Start background animation") }
        val display=displays.getDisplay(Display.DEFAULT_DISPLAY)?:return
        primaryKey=primaryHostKey(display)
        val context=createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null)
        windowContext=context;wm=context.getSystemService(WindowManager::class.java)
        config=Configuration(context.resources.configuration)
        val bounds=wm.currentWindowMetrics.bounds;screenWidth=bounds.width();screenHeight=bounds.height()
        val lifecycle=OverlayOwner();owner=lifecycle
        lifecycle.registry.currentState=Lifecycle.State.CREATED
        val compose=ComposeView(context);view=compose;compose.alpha=0f
        compose.viewTreeObserver.addOnPreDrawListener{
            if(settings().getBoolean("screenshot_camera_startup",false))runCatching{
                val root=View::class.java.getDeclaredMethod("getViewRootImpl").invoke(compose)
                val sc=root.javaClass.getMethod("getSurfaceControl").invoke(root) as android.view.SurfaceControl
                if(sc.isValid)GlassFrames.surface(compose,sc)
            }
            val frame=HandoffFrames.frame
            if(composedScreenshotStamp>0L && frame!=null && !ScreenshotStartupPolicy.ownsFrame(frame.physicalId,displayIdentity(displays.getDisplay(0)))){
                compose.alpha=0f
                handler.post{if(view===compose)updateDisplay()}
            }
            true
        }
        compose.viewTreeObserver.addOnDrawListener{

            val inner=minOf(screenWidth,screenHeight).toFloat()/maxOf(screenWidth,screenHeight)>.7f
            val stamp=composedScreenshotStamp
            if(stamp>0L && stamp!=commitPendingStamp && stamp==HandoffFrames.forPanel(inner)?.stamp && compose.alpha>0f){
                commitPendingStamp=stamp
                compose.viewTreeObserver.registerFrameCommitCallback{
                    if(view===compose && stamp==composedScreenshotStamp && stamp==HandoffFrames.forPanel(inner)?.stamp){
                        committedScreenshotStamp=stamp
                        RecoveryLog.add("Screenshot route: source D${HandoffFrames.frame?.displayId} physical=${HandoffFrames.frame?.physicalId}; overlay D${compose.display?.displayId} physical=${displayIdentity(compose.display)}; bitmap=${HandoffFrames.frame?.bitmap?.width}x${HandoffFrames.frame?.bitmap?.height}; view=${compose.width}x${compose.height}")
                        RecoveryLog.add("Outgoing screenshot committed: source=${if(inner) "inner" else "cover"}; stamp=$stamp; age=${SystemClock.elapsedRealtime()-stamp} ms")
                        LiveAngles.handoffReady()
                    }
                }
            }
            // The glass SurfaceView supplies its own frame-bound completion.
            val glass=!AnimationModePolicy.blackFade(settings().getString("animation_mode",AnimationModePolicy.DEFAULT),settings().getBoolean("debug_mode",false))
            if(compose.alpha>0f && (!inner || !glass))HandoffFadeFrames.drawn(inner)
        }
        compose.post(object:Runnable{override fun run(){
            if(view!==compose||!compose.isAttachedToWindow)return
            if(!settings().getBoolean("screenshot_camera_startup",false))return
            val registered=runCatching{
                val root=View::class.java.getDeclaredMethod("getViewRootImpl").invoke(compose)
                val sc=root.javaClass.getMethod("getSurfaceControl").invoke(root) as android.view.SurfaceControl
                if(!sc.isValid)throw IllegalStateException("Overlay root not ready")
                GlassFrames.surface(compose,sc)
            }.isSuccess
            if(!registered)compose.postDelayed(this,100)
        }})
        compose.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        compose.setViewTreeLifecycleOwner(lifecycle);compose.setViewTreeSavedStateRegistryOwner(lifecycle)
        val intensity=settings().getFloat("intensity",.4f)
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides config, LocalHinge provides rememberWindowHinge(context)) {
                val screenshot=settings().getBoolean("dual",false)&&settings().getBoolean("screenshot_camera_startup",false)
                val panelInner=minOf(screenWidth,screenHeight).toFloat()/maxOf(screenWidth,screenHeight)>.7f
                val outgoing=if(screenshot)HandoffFrames.forPanel(panelInner)?.takeIf{ScreenshotStartupPolicy.ownsFrame(it.physicalId,displayIdentity(displays.getDisplay(0)))} else null
                val retained=if(screenshot && outgoing==null)IncomingAnimationFrame.retainedFor(displays.getDisplay(0),screenWidth,screenHeight) else null
                val frozen=outgoing ?: retained
                SideEffect{composedScreenshotStamp=outgoing?.stamp ?: 0L}
                Box(Modifier.fillMaxSize()){
                    DuoLiveShade(this@StandaloneService,intensity,innerPanel=panelInner,frozenFrame=frozen,opaqueContent=screenshot&&frozen!=null,liveFrame=if(screenshot&&frozen==null)IncomingAnimationFrame.frame?.takeIf{it.physicalId==displayIdentity(displays.getDisplay(0))} else null)
                }
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
        if(!usable() || !LiveAngles.fresh() || !LiveAngles.effectAllowed){
            val now=SystemClock.elapsedRealtime()
            val transientOff=active && view?.alpha==1f && !settings().getBoolean("dual",false) &&
                LiveAngles.fresh() && LiveAngles.effectAllowed &&
                getSystemService(PowerManager::class.java).isInteractive &&
                !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
                displays.getDisplay(0)?.state==Display.STATE_OFF
            if(transientOff){
                if(transientOffStarted==0L)transientOffStarted=now
                if(CoverBridgePolicy.active(now,transientOffStarted)){
                    HandoffStartupTrace.event("HOST_RETAIN_TRANSIENT_OFF","alpha retained; bounded 600ms; interactive and unlocked")
                    return
                }
            }
            hide();this.strength=0f;return
        }
        transientOffStarted=0L
        val inner=minOf(screenWidth,screenHeight).toFloat()/maxOf(screenWidth,screenHeight)>.7f
        val held=settings().getBoolean("dual",false)&&settings().getBoolean("screenshot_camera_startup",false)&&HandoffFrames.forPanel(inner)!=null
        view?.alpha=if(held || active && (liveVeil || image!=null))1f else 0f
        if(!inner && view?.alpha==1f)CoverStartupTrace.reveal()
        HandoffStartupTrace.event("HOST_ALPHA_${view?.alpha}","active=$active; inner=$inner; geometry=${screenWidth}x${screenHeight}")
        if(changed && !active)SupportPrompts.recordUse(this)
        if(changed)note(if(active)"Duo transition active" else "Ready — Duo transition complete")
        if(active)requestCapture()
    }
    fun clearUnavailableEffect(){
        MotionGlass.stop()
        CoverBridge.clear();transientOffStarted=0L
        closeScreenshotWindow();dismissSecondary();hide();strength=0f;generation++;pending=false;image=null
    }
    private val sourceGuard=object:Runnable {override fun run(){
        // SCREEN_ON may arrive before keyguard unlock; retry independently of broadcasts.
        if(settings().getBoolean("enabled",false))resumeIfUsable()
        MotionGlass.refresh(this@StandaloneService,settings().getBoolean("motion_glass",false) && motionUsable())
        diagnosticState("poll")
        val fresh=LiveAngles.fresh()
        if(!fresh && (active || sourceWasFresh)){hide();note("Effect paused — waiting for fresh angles; panel session retained")}
        sourceWasFresh=fresh
        refreshSecondary()
        handler.postDelayed(this,250)
    }}
    private fun hide(){HandoffStartupTrace.event("HOST_HIDE","active=$active; fresh=${LiveAngles.fresh()}; allowed=${LiveAngles.effectAllowed}");view?.alpha=0f;active=false;handler.removeCallbacks(captureTask);captureQueued=false}
    override fun onAccessibilityEvent(event:AccessibilityEvent)=Unit
    private fun requestCapture()=Unit // Retained host callback; this release never captures pixels.
    private fun updateDisplay(reason:String="display callback"){
        if(view==null)return
        val display=displays.getDisplay(0)?:return
        // OFF snapshots may contain the previous panel's dimensions. Keep the prepared
        // live host until ON, then adapt it instead of compiling a new renderer.
        val liveRetention=LiveHostRetentionPolicy.eligible(
            settings().getBoolean("dual",false),settings().getBoolean("debug_mode",false),
            settings().getString("animation_mode",AnimationModePolicy.DEFAULT)==AnimationModePolicy.DEFAULT,
            LiveAngles.fresh(),LiveAngles.effectAllowed,
            getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        if(liveRetention && display.state!=Display.STATE_ON)return
        if(primaryHostKey(display)!=primaryKey){
            val size=android.graphics.Point();CoverBridge.size(display,size)
            val screenshotRetain=settings().getBoolean("dual",false)&&settings().getBoolean("screenshot_camera_startup",false)&&LiveAngles.dualActive&&LiveAngles.fresh()&&usable()&&LiveAngles.effectAllowed
            val retain=screenshotRetain || liveRetention
            if(!retain){RecoveryLog.add("Primary physical panel changed; rebuilding overlay");restart();return}
            // D0's window context follows D0. Keep its composition, exclusion surface and
            // capture client alive while swapping panel geometry; tearing them down here
            // used to add a second startup after the cover was already reported ON.
            if(minOf(size.x,size.y).toFloat()/maxOf(size.x,size.y)<=.7f)CoverStartupTrace.begin("retained host; ${primaryKey} -> ${physicalKey(display)}; physical=${displayIdentity(display)}; state=${display.state}; size=${size.x}x${size.y}; r=${display.rotation}")
            RecoveryLog.add(if(screenshotRetain)"Screenshot remap: retained primary overlay and composition" else "Live handoff: retained prepared primary host; shader/composition reused; destination=${size.x}x${size.y}; trigger=$reason")
            HandoffStartupTrace.event("HOST_REMAP","realSize=${size.x}x${size.y}; mode=${display.mode.physicalWidth}x${display.mode.physicalHeight}; alpha=${view?.alpha}")
            primaryKey=primaryHostKey(display)
            retainedPrimaryHost=true
            if(liveRetention)CoverBridge.prepare(display)
            screenWidth=size.x;screenHeight=size.y
            config=Configuration(windowContext!!.resources.configuration)
            GlassFrames.requestFreshCapture()
            view?.requestLayout()
            resumeIfUsable()
            return
        }
        val next=Configuration(windowContext!!.resources.configuration)
        val bounds=if(retainedPrimaryHost){val size=android.graphics.Point();CoverBridge.size(display,size);Rect(0,0,size.x,size.y)} else wm.currentWindowMetrics.bounds
        if(screenWidth!=bounds.width()||screenHeight!=bounds.height()){
            screenWidth=bounds.width();screenHeight=bounds.height();generation++;pending=false;image=null;if(!retainedPrimaryHost)view?.alpha=0f
        }
        // Keep the composition alive: Duo retains its timeline and both snapshot-morph images.
        config=next;requestCapture()
    }
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);updateDisplay("configuration callback")}
    override fun onDisplayChanged(id:Int){queueDisplayUpdate()}
    override fun onDisplayAdded(id:Int){queueDisplayUpdate()}
    override fun onDisplayRemoved(id:Int){queueDisplayUpdate()}
    private fun removeHost(){
        composedScreenshotStamp=0L;commitPendingStamp=0L;committedScreenshotStamp=0L
        closeScreenshotWindow();dismissSecondary();generation++;pending=false;hide();FoldBridgeActivity.cancel()
        owner?.registry?.currentState=Lifecycle.State.DESTROYED
        view?.let {GlassFrames.surface(it,null);it.disposeComposition();runCatching {wm.removeViewImmediate(it)}}
        view=null;owner=null;image=null;windowContext=null
    }
    fun secondaryReady():Boolean=secondaryReadySnapshot
    private fun dismissSecondary(){nativeOverlay?.dismiss();nativeOverlay=null;secondaryReadySnapshot=false;val old=secondary;secondary=null;runCatching{old?.dismiss()}}
    private var secondaryRefreshPending=false
    private val secondaryRefreshFrame=android.view.Choreographer.FrameCallback {
        secondaryRefreshPending=false
        if(instance===this)refreshSecondary()
    }
    fun requestSecondaryRefresh(){
        if(!secondaryRefreshPending){secondaryRefreshPending=true;android.view.Choreographer.getInstance().postFrameCallback(secondaryRefreshFrame)}
    }
    fun refreshSecondary(){
        if(secondaryRefreshPending){secondaryRefreshPending=false;android.view.Choreographer.getInstance().removeFrameCallback(secondaryRefreshFrame)}
        if(!::displays.isInitialized)return
        val current=displays.getDisplay(0)
        val primaryInner=current?.mode?.let{minOf(it.physicalWidth,it.physicalHeight).toFloat()/maxOf(it.physicalWidth,it.physicalHeight)>.7f}?:false
        PreviewTransition.configure(this)
        PreviewTransition.update(!LiveAngles.nativeInner && previewMode() && settings().getBoolean("enabled",false) && !getSystemService(KeyguardManager::class.java).isKeyguardLocked(),primaryInner)
        val screenshotStartup=settings().getBoolean("dual",false)&&settings().getBoolean("screenshot_camera_startup",false)
        if(screenshotStartup && settings().getBoolean("enabled",false) && usable() && LiveAngles.effectAllowed){
            dismissSecondary()
            val target=displays.getDisplays("com.samsung.android.hardware.display.category.BUILTIN").firstOrNull{it.displayId==1}
            val key=if(target==null)"" else "logical-D${target.displayId}"
            if(target==null){closeScreenshotWindow();secondaryError="Screenshot destination unavailable";return}
            if(screenshotWindow!=null && screenshotKey!=key)closeScreenshotWindow()
            if(screenshotWindow==null && SystemClock.elapsedRealtime()>=screenshotRetry){
                var candidate:ScreenshotStartupWindow?=null
                try{
                    candidate=ScreenshotStartupWindow(this,target)
                    candidate.show();screenshotWindow=candidate;screenshotKey=key;secondaryError=""
                    RecoveryLog.add("Screenshot secondary window attached BEFORE concurrent request: $key")
                }catch(e:Exception){candidate?.close();closeScreenshotWindow();secondaryError="Screenshot preparation: $e";screenshotRetry=SystemClock.elapsedRealtime()+1500}
            }
            screenshotWindow?.refresh()
            screenshotPrepared=screenshotWindow?.attached==true
            val outgoing=HandoffFrames.frame
            val targetIsOutgoing=outgoing!=null&&ScreenshotStartupPolicy.ownsFrame(outgoing.physicalId,displayIdentity(target))
            secondaryReadySnapshot=screenshotPrepared&&target.state==Display.STATE_ON&&outgoing!=null&&(!targetIsOutgoing||screenshotWindow?.committedStamp==outgoing.stamp)
            return
        }
        closeScreenshotWindow()
        val blackHold=settings().getString("animation_mode","")=="plain_fade" && !primaryInner && !settings().getBoolean("dual",false)
        val native=LiveAngles.continuityNative && !primaryInner && !blackHold
        val preview=AnimationModePolicy.mirrors(settings().getString("animation_mode",AnimationModePolicy.DEFAULT)) && !settings().getBoolean("dual",false) && settings().getBoolean("cover_preview",true) && LiveAngles.coverPreview
        if(!LiveAngles.effectAllowed||!settings().getBoolean("enabled",false)||(!blackHold && !native && !preview && (!settings().getBoolean("dual",false)||!LiveAngles.dualActive))||!usable()){dismissSecondary();return}
        val target=displays.displays.firstOrNull{it.displayId==1 && BuiltInPanel.accepts(it)}
        if(target==null){secondaryError="Concurrent mode has not exposed the second built-in panel";dismissSecondary();return}
        if(native){
            val key=physicalKey(target)+":native-overlay"
            if(secondaryKey==key && nativeOverlay?.attached==true){secondaryReadySnapshot=nativeOverlay!!.draws>0;return}
            if(SystemClock.elapsedRealtime()<secondaryRetry)return
            dismissSecondary()
            try{
                val overlay=NativeInnerOverlay(this,target,settings().getFloat("intensity",.4f));nativeOverlay=overlay
                overlay.show();secondaryKey=key;secondaryError="";RecoveryLog.add("Native inner accessibility overlay attached on display 1")
            }catch(e:Exception){dismissSecondary();secondaryError="${e.javaClass.simpleName}: ${e.message}";secondaryRetry=SystemClock.elapsedRealtime()+1500;RecoveryLog.add("Native inner overlay error: $secondaryError")}
            return
        }
        if(nativeOverlay!=null)dismissSecondary()
        if(secondaryKey==physicalKey(target)+":"+preview+":"+native+":"+blackHold && secondary?.display?.displayId==target.displayId && secondary?.isShowing==true){secondary?.refresh();secondaryReadySnapshot=secondary?.ready==true;return}
        if(SystemClock.elapsedRealtime()<secondaryRetry)return
        dismissSecondary()
        try{
            val next=SecondaryShade(this,target,settings().getFloat("intensity",.4f),preview,native,blackHold){RecoveryLog.add(it)}
            next.setOnDismissListener{RecoveryLog.add("Secondary Presentation dismissed");if(secondary===next){secondary=null;secondaryReadySnapshot=false}}
            secondary=next;secondaryKey=physicalKey(target)+":"+preview+":"+native+":"+blackHold;next.show();secondaryError="";RecoveryLog.add("Secondary Presentation shown, display ${target.displayId}")
        }catch(e:Exception){dismissSecondary();secondaryError="${e.javaClass.simpleName}: ${e.message}";secondaryRetry=SystemClock.elapsedRealtime()+1500;RecoveryLog.add("Secondary Presentation error: $secondaryError")}
    }
    private fun displayIdentity(d:Display?):String=if(d==null)"none" else runCatching{Display::class.java.getMethod("getUniqueId").invoke(d).toString()}.getOrDefault("unavailable")
    private val diagnosticHistory=ArrayDeque<String>()
    private var lastDiagnosticState=""
    private var diagnosticDual=false
    private var diagnosticTail=0L
    private fun diagnosticState(event:String,force:Boolean=false){
        if(!settings().getBoolean("dual",false)||!settings().getBoolean("screenshot_camera_startup",false))return
        val now=SystemClock.elapsedRealtime()
        if(LiveAngles.dualActive!=diagnosticDual){diagnosticDual=LiveAngles.dualActive;diagnosticTail=now+5000}
        if(!force&&!diagnosticDual&&now>diagnosticTail)return
        val pm=getSystemService(PowerManager::class.java);val kg=getSystemService(KeyguardManager::class.java)
        val state="interactive=${pm.isInteractive} keyguardLocked=${kg.isKeyguardLocked} deviceLocked=${kg.isDeviceLocked} dual=${LiveAngles.dualActive}; ${displayReport()}"
        if(force||state!=lastDiagnosticState){
            lastDiagnosticState=state;diagnosticHistory.addLast("$now $event $state")
            while(diagnosticHistory.size>40)diagnosticHistory.removeFirst()
        }
    }
    private fun primaryHostKey(d:Display)=if(settings().getBoolean("dual",false))physicalKey(d) else "${displayIdentity(d)}:${physicalKey(d)}"
    private fun physicalKey(d:Display)="${d.displayId}:${d.mode.physicalWidth}x${d.mode.physicalHeight}"
    private fun displayReport()=if(!::displays.isInitialized)"unavailable" else (displays.displays.toList()+displays.getDisplays("com.samsung.android.hardware.display.category.BUILTIN").toList()).distinctBy{it.displayId}.joinToString("; "){d->
        "id=${d.displayId} state=${d.state} mode=${d.mode.physicalWidth}x${d.mode.physicalHeight} rotation=${d.rotation}"
    }
    fun screenshotReport()="Duo Fold Live ${BuildConfig.VERSION_NAME}\nPrimary handoff: endpoint only; mid-fold ON override inactive\nScreenshot mode: ${settings().getBoolean("dual",false)}; window-first: ${settings().getBoolean("screenshot_camera_startup",false)}\n${LiveAngles.handoffStatus}\n${HandoffFrames.status}\n${IncomingAnimationFrame.report()}\n${IncomingGhostDiagnostic.report()}\n${IncomingHold.report()}\n${screenshotWindow?.windowReport ?: "Secondary window not attached"}\nOutgoing committed=${screenshotFrameReady()}; destination attached=$screenshotPrepared; secondary ready=${secondaryReady()}\nRenderer=${screenshotWindow?.rendererRoute ?: "none"}; draws=${screenshotWindow?.draws ?: 0}; error=$secondaryError\nDisplays: ${displayReport()}\nSoftware observations only; not optical proof.\n${LiveAngles.screenshotTrace}"
    fun report(includeHealthHistory:Boolean=true)="Duo Fold Live ${BuildConfig.VERSION_NAME}\nSelectable glass / live angles\n${Build.MODEL} / Android ${Build.VERSION.RELEASE}\n$status\n${LiveAngles.status}\n${FoldAwakeDefault.status}\n${FoldAwakeGuard.status}\n${RotationMigration.status}\n${org.duofold.live.wallpaperlayer.WakePreparationTest.report(this)}\nCustom wallpaper: ${org.duofold.live.wallpaperlayer.WallpaperRestore.status}\nBackground service: ${FoldBackgroundService.running}\n${FoldBackgroundService.connectionReport()}\nReader connection: ${LiveAngles.readerDiagnostics}\n${LiveAngles.latencyReport()}\nAngle age: ${LiveAngles.ageMs()} ms\nClosed-hinge jitter protection setting: ${settings().getBoolean("closed_hinge_jitter_protection",true)}\n${LiveAngles.closedHingeReport}\nCover preview: ${settings().getBoolean("cover_preview",true)}\nHandoff path: ${if(settings().getBoolean("dual",false)) "dual-screen screenshot" else "normal (dual OFF)"}\nWindow-first setting: ${settings().getBoolean("screenshot_camera_startup",false)}; effective: ${settings().getBoolean("dual",false)&&settings().getBoolean("screenshot_camera_startup",false)}\nSamsung ON hold: inactive on endpoint-only screenshot route\nClosing app-first return setting: ${settings().getBoolean("closing_app_return",false)}\nScreenshot endpoint black fade: smooth final 12°; primary handoff at endpoint\nIncoming app screenshot hold: disabled internally\n${IncomingHold.report()}\nHistorical traces below can include earlier settings; use the route-labelled motion entries for this test.\nPreview bridge: ${LiveAngles.expansionStatus}\nPreview preparation: ${PreviewTransition.status}\nBridge trace: ${LiveAngles.bridgeTrace}\nSmoothing: ${settings().getFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS)} ms; full-resolution glass: ${settings().getBoolean("full_resolution_glass",false)}\nMode: ${if(AnimationModePolicy.blackFade(settings().getString("animation_mode",AnimationModePolicy.DEFAULT),settings().getBoolean("debug_mode",false))) "Debug black fade" else "Projected glass"}\n${LiveAngles.handoffStatus}\n${LiveAngles.handoffFade}\n${LiveAngles.rotationHold}\n${LiveAngles.continuityStatus}\n${InnerDecorRecovery.apiStatus}\n${InnerDecorRecovery.status}\n${LiveAngles.continuityTrace}\nHandoff angle: ${HandoffSettings.angle(settings().getFloat("handoff_angle",HandoffSettings.DEFAULT))}°\nScreen placement: ${if(settings().getBoolean("window_reveal_v2",true)) "V2 Window Reveal" else "Original"}\nCover startup easing: ${settings().getBoolean("startup_easing",true)}\nCover early stretch (%): ${settings().getFloat("early_stretch",2.7f)*100f/3f}; enhanced end=${settings().getBoolean("enhanced_end_stretch",true)}; end stretch=${settings().getFloat("end_stretch",1.25f)*100f}%\nInner startup easing: ${settings().getBoolean("inner_startup_easing",true)}; early stretch=${settings().getFloat("inner_early_stretch",.9f)*100f/3f}%; enhanced end=${settings().getBoolean("inner_enhanced_end_stretch",true)}; end stretch=${settings().getFloat("inner_end_stretch",.6f)*100f}%\nVertical compression: cover=${settings().getFloat("cover_vertical_compression",.9f)*100f}%; inner=${settings().getFloat("inner_vertical_compression",.4f)*100f}%\nAA method: ${settings().getInt("antialias_method_v2",0)} (0=lightweight, 1=edge post-process, 2=4x buffer); enabled=${settings().getBoolean("antialias_enabled",true)}; strength=${settings().getFloat("antialias_strength",.35f)}\nGlass strength: ${settings().getFloat("intensity",.4f)*100f}%; blur amount: ${RenderQuality.blur(settings().getFloat("blur_strength",.3f))}\nGlass: ${GlassFrames.status}\n${GlassFrames.pipelineReport()}\n${MotionGlass.report()}\nHandoff: ${HandoffFrames.status}\nScreenshot window-first: ${settings().getBoolean("screenshot_camera_startup",false)}; attached=$screenshotPrepared; anchor draws=${screenshotWindow?.draws ?: 0}; outgoing frame committed=${screenshotFrameReady()}\n${IncomingAnimationFrame.report()}\nScreenshot secondary renderer: ${screenshotWindow?.rendererRoute ?: "none"}; strength=${screenshotWindow?.strength ?: 0f}\nScreenshot content: ${if(settings().getBoolean("dual",false)&&settings().getBoolean("screenshot_camera_startup",false)) "outgoing=frozen screenshot; incoming=live physical-panel capture animation after exact app verification; incoming endpoint fade with bounded ready-app reveal" else "window-first diagnostic path inactive; retained screenshot traces may describe earlier tests"}\n${FoldWakeConnection.report()}\n${ClosureDiagnostics.report()}\n${LiveAngles.screenshotTrace}\nScreenshot power/keyguard events:\n${diagnosticHistory.joinToString("\n")}\nSecondary ready: ${secondaryReady()}; draws: ${nativeOverlay?.draws ?: secondary?.draws ?: 0}\nSecondary layer: ${if(nativeOverlay!=null)"Native inner accessibility overlay" else secondary?.layer ?: "none"}; strength: ${nativeOverlay?.strength ?: secondary?.strength ?: 0f}\nSecondary error: $secondaryError\nDisplays: ${displayReport()}\nFolding Mode: ${if(settings().getBoolean("dual",false)) "Screenshot Mode" else "Live Screen"}\nAnimation: ${AnimationModePolicy.label(settings().getString("animation_mode",AnimationModePolicy.DEFAULT))}\n${HandoffStartupTrace.report()}\nLegacy cover trace (geometry-based; may misclassify remaps):\n${CoverStartupTrace.report()}\n${FrameTelemetry.report()}\nStrength: $strength\n"+history.joinToString("\n")+"\n"+PlainFadeSettings.report(getSharedPreferences("standalone",0))+"\nLifecycle / connection events:\n"+RecoveryLog.report()+(if(includeHealthHistory)"\n24-hour health samples (app process only):\n"+HealthTrace.report(this) else "")
    override fun onInterrupt(){hide();image=null;note("Feedback interrupted — overlay remains attached")}
    override fun onDestroy(){LiveAngles.remove(motionListener);MotionGlass.stop();android.view.Choreographer.getInstance().removeFrameCallback(secondaryRefreshFrame);secondaryRefreshPending=false;IncomingHold.clear("service destroyed");PreviewTransition.update(false,false);removeHost();if(::displays.isInitialized)displays.unregisterDisplayListener(this);displayObserver?.quitSafely();displayObserver=null;runCatching {unregisterReceiver(screenReceiver)};handler.removeCallbacksAndMessages(null);instance=null;super.onDestroy()}
}
internal class OverlayOwner:LifecycleOwner,SavedStateRegistryOwner {
    val registry=LifecycleRegistry(this)
    private val controller=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=controller.savedStateRegistry
    init {controller.performAttach();controller.performRestore(null)}
}
