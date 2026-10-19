package org.duofold.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import android.view.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.exp

/** Screenshot-free reference darkening over the live wallpaper and apps; no blur or 3D projection. */
@Composable
internal fun DuoLiveShade(host: StandaloneFoldHost, intensity: Float, innerPanel: Boolean? = null, frozenFrame:GlassFrame? = null, reflectedCover:Boolean = false, opaqueContent:Boolean = false, liveFrame:GlassFrame? = null) {
    var angle by remember { mutableFloatStateOf(Float.NaN) }
    var visualAngle by remember { mutableFloatStateOf(Float.NaN) }
    var amount by remember { mutableFloatStateOf(0f) }
    val expanded=innerPanel ?: LocalConfiguration.current.isRegular()
    val plainPrefs=LocalContext.current.getSharedPreferences("standalone",0)
    val plain=plainPrefs.getString("animation_mode","")=="plain_fade"
    val tuning=remember(expanded){PlainFadeSettings.read(plainPrefs,expanded)}
    val handoff=HandoffSettings.angle(plainPrefs.getFloat("handoff_angle",HandoffSettings.DEFAULT))
    val openThreshold=LocalContext.current.getSharedPreferences("standalone",0).getFloat("open_threshold",172f)
    val smoothingMs=FrameSmoothing.sanitize(LocalContext.current.getSharedPreferences("standalone",0).getFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS))
    val rotation=LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val hinge=LocalHinge.current
    val latestExpanded by rememberUpdatedState(expanded)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit){
        val listener=LiveAngles.Listener { value, _ -> angle=value }
        LiveAngles.add(listener)
        onDispose { LiveAngles.remove(listener) }
    }
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        var previous=0L
        while(true){
            val fresh=LiveAngles.fresh() && angle.isFinite()
            if(!fresh || !LiveAngles.effectAllowed){amount=0f;visualAngle=Float.NaN;previous=0L;delay(30);continue}
            withFrameNanos { now ->
                val dt=if(previous==0L)8.33f else ((now-previous)/1_000_000f).coerceIn(1f,50f)
                previous=now
                visualAngle=FrameSmoothing.step(visualAngle,angle,dt,smoothingMs)
                amount=if(latestExpanded && !plain && frozenFrame==null && !plainPrefs.getBoolean("dual",false))MotionGlass.innerAmount(DuoShadeCurve.progress(visualAngle,true,openThreshold),angle>=FoldThreshold.sanitize(openThreshold))
                  else if(latestExpanded && angle>=FoldThreshold.sanitize(openThreshold))0f
                  else if(plain)PlainFadeCurve.progress(visualAngle,latestExpanded,openThreshold,handoff)
                  else DuoShadeCurve.progress(visualAngle,latestExpanded,openThreshold)
            }
        }
    }}
    SideEffect { host.onFrame(amount>.003f,amount) }
    val fadePrefs=LocalContext.current.getSharedPreferences("standalone",0)
    val debug=AnimationModePolicy.blackFade(fadePrefs.getString("animation_mode",AnimationModePolicy.DEFAULT),fadePrefs.getBoolean("debug_mode",false))
    if(!debug || (liveFrame!=null && IncomingGhostDiagnostic.mode!=0)){DuoGlassSurface(visualAngle,amount,intensity,expanded,rotation,frozenFrame,reflectedCover,opaqueContent,liveFrame);return}
    Canvas(Modifier.fillMaxSize()){
        if(opaqueContent && frozenFrame!=null)drawScreenshotBackdrop(drawContext.canvas.nativeCanvas,frozenFrame,size.width.toInt(),size.height.toInt())
        if(plain)drawRect(Color.Black.copy(alpha=PlainFadeCurve.dim(amount,tuning)))
        val horizontal=rotation==Surface.ROTATION_90 || rotation==Surface.ROTATION_270
        val reversed=rotation==Surface.ROTATION_90 || rotation==Surface.ROTATION_180
        val extent=if(horizontal)size.height else size.width
        val center=hinge?.takeIf { it.vertical != horizontal }?.let { (it.startPx+it.endPx)/2f } ?: extent/2f
        val hingeAt=center.coerceIn(1f,(extent-1f).coerceAtLeast(1f))
        val segments=if(plain)256 else 64
        val stops=(0..segments).map { i ->
            val fraction=i/segments.toFloat()
            val coord=fraction*extent
            val edge=if(expanded){if(reversed)(coord-hingeAt)/(extent-hingeAt) else (hingeAt-coord)/hingeAt}
                     else if(reversed)1f-fraction else fraction
            fraction to Color.Black.copy(alpha=if(plain)PlainFadeCurve.wipe(amount,1f-edge,tuning) else DuoShadeCurve.alpha(amount,edge,intensity))
        }.toTypedArray()
        drawRect(Brush.linearGradient(*stops,start=Offset.Zero,end=if(horizontal)Offset(0f,extent) else Offset(extent,0f)))
    }
}
