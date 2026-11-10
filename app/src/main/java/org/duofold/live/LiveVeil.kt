package org.duofold.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import android.view.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Windowed Glass over live content, with the existing hinge and neutral-return behavior. */
@Composable
internal fun DuoLiveShade(host: StandaloneFoldHost, intensity: Float, innerPanel: Boolean? = null, frozenFrame:GlassFrame? = null, reflectedCover:Boolean = false, opaqueContent:Boolean = false, liveFrame:GlassFrame? = null) {
    val routePrefs=LocalContext.current.getSharedPreferences("standalone",0)
    if(frozenFrame==null&&!reflectedCover&&routePrefs.getBoolean("live_b8_enabled",false)&&routePrefs.getBoolean("b8_snapshot_test",false)&&!routePrefs.getBoolean("dual",false)){
        androidx.compose.ui.viewinterop.AndroidView(factory={CaptureExclusionSurface(it)},modifier=androidx.compose.ui.Modifier.fillMaxSize())
        return
    }
    if(!HandoffVisualPolicy.ANIMATION_ENABLED&&routePrefs.getBoolean("dual",false)){
        SideEffect { host.onFrame(frozenFrame!=null,if(frozenFrame!=null)1f else 0f) }
        if(frozenFrame!=null){
            androidx.compose.ui.viewinterop.AndroidView(factory={ScreenshotGlassView(it)},modifier=Modifier.fillMaxSize(),update={it.configure(frozenFrame,0f,0f,innerPanel?:false,0,0f)})
        }else{
            androidx.compose.ui.viewinterop.AndroidView(factory={CaptureExclusionSurface(it)},modifier=Modifier.fillMaxSize())
        }
        return
    }
    var angle by remember { mutableFloatStateOf(Float.NaN) }
    var visualAngle by remember { mutableFloatStateOf(Float.NaN) }
    var amount by remember { mutableFloatStateOf(0f) }
    val expanded=innerPanel ?: LocalConfiguration.current.isRegular()
    val motionPrefs=LocalContext.current.getSharedPreferences("standalone",0)
    val openThreshold=LocalContext.current.getSharedPreferences("standalone",0).getFloat("open_threshold",172f)
    val smoothingMs=FrameSmoothing.sanitize(LocalContext.current.getSharedPreferences("standalone",0).getFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS))
    val rotation=LocalView.current.display?.rotation ?: Surface.ROTATION_0
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
                val unwind=LiveAngles.pauseFraction()
                val pose=HingePausePolicy.pose(visualAngle,latestExpanded,openThreshold,unwind)
                amount=if(unwind==0f&&latestExpanded && frozenFrame==null && !motionPrefs.getBoolean("dual",false))MotionGlass.innerAmount(DuoShadeCurve.progress(visualAngle,true,openThreshold),angle>=FoldThreshold.sanitize(openThreshold))
                  else if(latestExpanded && angle>=FoldThreshold.sanitize(openThreshold))0f
                  else DuoShadeCurve.progress(pose,latestExpanded,openThreshold)
            }
        }
    }}
    SideEffect { host.onFrame(amount>.003f,amount) }
    DuoGlassSurface(visualAngle,amount,intensity,expanded,rotation,frozenFrame,reflectedCover,opaqueContent,liveFrame)
}
