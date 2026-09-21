package com.mccal.folio

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.exp
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

/**
 * iPhone Duo–style fold effect, as dynamic as a Galaxy Z Fold allows.
 *
 * Android doesn't require the public hinge sensor to be continuous, and on a Galaxy Z Fold8 it reports only 0°, 90°
 * and 180° to apps (Samsung's finer readings stay with its own components); One UI decides when displays switch.
 * So on stepped sensors the effect is *step-anchored and speed-adaptive*: each real step is a checkpoint, the motion
 * between checkpoints is predicted from how fast this person folds (learned over time), and a late or early step
 * bends the animation instead of snapping it. Phones whose sensor proves continuous follow the angle itself
 * (see [HingeTracker]).
 *
 * Visual model (from the MIT Three.js recreation): the half left of the hinge is blurred with
 * radius ∝ m·e^1.35 and darkened toward its outer edge; m is 1 half-folded and 0 flat.
 */
@Composable
fun FoldTransitionHost(enabled: Boolean = true, intensity: Float = 1f, stayAwake: Boolean = true,
    snapshotMorph: Boolean = false, haptics: Boolean = true, content: @Composable () -> Unit) {
    // Which screen we're on, by size in both dimensions, so rotating the cover to landscape never looks like an unfold.
    val expanded = LocalConfiguration.current.isRegular()
    val view = LocalView.current
    val context = LocalContext.current
    // Where the hinge is and which half moves, from the real fold and the display's rotation, so the effect is
    // right in portrait, upside down and on a rotated cover, not just in the unfolded landscape it was tuned in.
    val hinge = LocalHinge.current
    val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) DuoShader() else null }
    val fold = remember { FoldTimeline(context) }
    fold.stayAwake = stayAwake
    // One light tick as the hinge passes halfway, opening or closing (idea from FoldFX).
    val tick by androidx.compose.runtime.rememberUpdatedState(enabled && haptics)
    fold.onHalfway = { if (tick) view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) android.view.HapticFeedbackConstants.SEGMENT_TICK else android.view.HapticFeedbackConstants.CLOCK_TICK) }
    // m: 0 = clean, 1 = fully half-folded look. cover = whole-screen mode on the cover display.
    var m by remember { mutableFloatStateOf(0f) }

    // Screenshot morph (fallback style): snapshots of Folio's own screen taken the moment the hinge
    // starts moving, drawn over the new display and melted into the live UI. Memory only, never saved.
    val contentLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var coverShot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var innerShot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var morph by remember { mutableFloatStateOf(1f) } // 0 = snapshot fully shown, 1 = done
    val morphEnabled by androidx.compose.runtime.rememberUpdatedState(enabled && snapshotMorph)
    fold.onOpeningStarted = { if (morphEnabled && !fold.expanded) scope.launch { coverShot = runCatching { contentLayer.toImageBitmap() }.getOrNull() } }
    fold.onClosingStarted = { if (morphEnabled && fold.expanded) scope.launch { innerShot = runCatching { contentLayer.toImageBitmap() }.getOrNull() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> fold.start()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> fold.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); fold.stop() }
    }

    // Decide during composition so the very first frame on the new display is already covered.
    if (expanded != fold.expanded) {
        fold.expanded = expanded
        fold.onDisplaySwitched(SystemClock.uptimeMillis())
        m = if (expanded) START_M_ON_UNFOLD else START_M_ON_COVER
        // Cover the very first frame on the new display with the snapshot (held until the panel is lit).
        if (enabled && snapshotMorph && (if (expanded) coverShot != null else innerShot != null)) morph = 0f
    }

    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        var litFrames = 0
        var lastFrame = 0L
        while (true) {
            if (!fold.busy && m == 0f) { lastFrame = 0L; kotlinx.coroutines.delay(IDLE_POLL_MS); continue }
            withFrameNanos { frame ->
                val now = SystemClock.uptimeMillis()
                val dt = if (lastFrame == 0L) 16f else ((frame - lastFrame) / 1_000_000f).coerceIn(1f, 64f)
                lastFrame = frame
                if (fold.waitingForPanel) {
                    val lit = view.display?.state == Display.STATE_ON
                    litFrames = if (lit) litFrames + 1 else 0
                    if (litFrames >= 2 || now - fold.switchedAt > LIT_TIMEOUT_MS) {
                        fold.onPanelLit(now); litFrames = 0
                        if (morphEnabled && (if (fold.expanded) coverShot != null else innerShot != null)) { fold.morphFrom = now; morph = 0f }
                    }
                }
                val target = fold.targetM(now)
                // Follow the target closely but never jump: small time constant, frame-rate independent.
                val next = m + (target - m) * (1f - exp(-dt / FOLLOW_MS))
                m = if (target == 0f && next < .003f) 0f else next
                if (fold.morphFrom >= 0) {
                    val t = ((now - fold.morphFrom) / (if (fold.expanded) MORPH_UNFOLD_MS else MORPH_FOLD_MS)).coerceIn(0f, 1f)
                    morph = easeInOutSine(t)
                    if (t >= 1f) { fold.morphFrom = -1L; morph = 1f; if (fold.expanded) coverShot = null }
                }
            }
        }
    } }

    // The Duo shader always drives the rotating half; the iPhone Duo style adds the still right half on top.
    val useBlurEffect = enabled
    // The Duo effect wraps both the live screen and the still picture (so the cover's blur applies to both),
    // while the recording below it captures the clean screen (a snapshot must never have blur baked in).
    Box(Modifier.fillMaxSize().then(
        if (shader != null) Modifier.graphicsLayer {
            renderEffect = if (useBlurEffect && m > 0f && Build.VERSION.SDK_INT >= 33) shader.effect(size.width, size.height, (m * intensity).coerceIn(0f, 1.5f),
                cover = !fold.expanded, geometry = foldGeometry(rotation, hinge, size.width, size.height)) else null
            // The open screen settles up to full size as it clears, and eases back down as it folds.
            val settle = if (useBlurEffect && fold.expanded) 1f - FOLD_SCALE * m.coerceIn(0f, 1f) else 1f
            scaleX = settle; scaleY = settle
        } else Modifier.drawWithContent {
            drawContent()
            if (useBlurEffect && m > 0f) {
                if (fold.expanded) drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = m),
                    .5f to Color.Transparent, startX = 0f, endX = size.width))
                else drawRect(Color.Black.copy(alpha = .5f * m))
            }
        })) {
        Box(Modifier.fillMaxSize()
            // Keep a live recording of Folio's screen so a snapshot can be taken instantly when folding starts.
            .then(if (enabled && snapshotMorph) Modifier.drawWithContent {
                contentLayer.record { this@drawWithContent.drawContent() }
                drawLayer(contentLayer)
            } else Modifier)) { content() }
        // The still picture maps the cover 1:1 onto the inner half only in the natural orientation; rotated, the
        // pictures don't line up, so the blur carries the transition on its own.
        if (snapshotMorph && morph < 1f && rotation == android.view.Surface.ROTATION_0) SnapshotMorph(fold.expanded, coverShot, innerShot) { morph }
        // Whole screen dims as it folds, like the display powering down with the hinge.
        if (enabled && fold.closing) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = (m * FOLD_DIM).coerceIn(0f, FOLD_DIM)))
        }
    }
}

/**
 * iPhone Duo's trick (per hands-on reviews and chuspeeism/iphone-duo): content doesn't move. The panel with the
 * rear cameras stays put, so the cover screen's picture is shown on the inner screen's right half at the same
 * physical size and position (hinge-side edge on the hinge, top-aligned) while the left half comes into focus
 * (the Duo shader on the live screen). Folding does the reverse. The still picture then fades into the live UI.
 * Both Fold screens share a density, so 1:1 pixels means the same physical size.
 */
@Composable
private fun SnapshotMorph(expanded: Boolean, coverShot: androidx.compose.ui.graphics.ImageBitmap?,
    innerShot: androidx.compose.ui.graphics.ImageBitmap?, progress: () -> Float) {
    // Hold the still picture while the rotating half clears, then hand over to the live UI.
    fun stillAlpha(p: Float) = 1f - ((p - STILL_HOLD) / (1f - STILL_HOLD)).coerceIn(0f, 1f)
    if (expanded) coverShot?.let { shot ->
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = stillAlpha(progress()) }) {
            val hinge = size.width / 2
            clipRect(left = hinge) {
                drawImage(shot, dstOffset = androidx.compose.ui.unit.IntOffset(hinge.toInt(), 0),
                    dstSize = androidx.compose.ui.unit.IntSize(shot.width, shot.height))
            }
        }
    } else innerShot?.let { shot ->
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = stillAlpha(progress()) }) {
            // The inner right half, 1:1, with the hinge on the cover's left edge.
            val hinge = shot.width / 2
            drawImage(shot, srcOffset = androidx.compose.ui.unit.IntOffset(hinge, 0),
                srcSize = androidx.compose.ui.unit.IntSize(shot.width - hinge, shot.height),
                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                dstSize = androidx.compose.ui.unit.IntSize(shot.width - hinge, shot.height))
        }
    }
}

/** Hinge steps + learned timing → target effect strength over time. */
private class FoldTimeline(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val hinge: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private val prefs = context.getSharedPreferences("folio", 0)

    var expanded = false
    var stayAwake = true
    var switchedAt = -1L; private set
    var waitingForPanel = false; private set

    // What this phone's hinge sensor reports, learned from its readings and remembered. The Fold8's public sensor is
    // stepped (0/90/180), so the motion between steps is predicted from learned timing; a continuous sensor is
    // followed directly.
    private val tracker = HingeTracker(if (hinge == null) HingeCapability.POSTURE_ONLY
        else if (prefs.getString(CAPABILITY_KEY, null) == HingeCapability.CONTINUOUS.name) HingeCapability.CONTINUOUS else HingeCapability.STEPPED)
    private val continuous get() = tracker.capability == HingeCapability.CONTINUOUS
    private var angleAt = 0L
    // A real movement, not sensor jitter: what the stall checks measure from on continuous sensors.
    private var movedAt = 0L
    private var movedFrom = 0f
    // Highest angle while not folding, and lowest while folding: a fold or a reopen is a real change from these.
    private var peak = 0f
    private var trough = 0f

    // Unfold (inner display): lit → flat.
    private var litAt = -1L
    private var flatAt = -1L
    private var litAngle = HingeTracker.FLAT_ENTER_DEG
    private var predictedOpenMs = prefs.getFloat("fold_open_ms", 520f)

    // Fold (inner display): 180→90 step → 0 step.
    private var closeStartAt = -1L
    private var closedAt = -1L
    private var reopenedAt = -1L
    private var predictedCloseMs = prefs.getFloat("fold_close_ms", 650f)

    // Cover display after folding, and while starting to open from the cover.
    private var coverLitAt = -1L
    private var coverOpeningAt = -1L
    private val appContext = context.applicationContext

    var onOpeningStarted: (() -> Unit)? = null
    var onClosingStarted: (() -> Unit)? = null
    var onHalfway: (() -> Unit)? = null
    /** Uptime when the screenshot morph started on the new display, or -1. */
    var morphFrom = -1L
    /** Folding from the open screen right now. */
    val closing get() = closeStartAt >= 0 && expanded
    val busy get() = morphFrom >= 0 || waitingForPanel || litAt >= 0 || closeStartAt >= 0 || reopenedAt >= 0 || coverLitAt >= 0 || coverOpeningAt >= 0

    fun start() { hinge?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { sensors?.unregisterListener(this) }

    fun onDisplaySwitched(now: Long) {
        switchedAt = now; waitingForPanel = true
        litAt = -1L; flatAt = -1L; closeStartAt = -1L; closedAt = -1L; reopenedAt = -1L; coverLitAt = -1L; coverOpeningAt = -1L
    }

    fun onPanelLit(now: Long) {
        waitingForPanel = false
        if (expanded) { litAt = now; litAngle = tracker.visual ?: HingeTracker.FLAT_ENTER_DEG; if (tracker.flat) flatAt = now } else coverLitAt = now
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return
        val now = SystemClock.uptimeMillis()
        val previous = tracker.raw
        val wasFlat = tracker.flat
        val wasClosed = tracker.closed
        if (tracker.feed(value, event.timestamp)) prefs.edit().putString(CAPABILITY_KEY, tracker.capability.name).apply()
        angleAt = now
        if (previous == null) { peak = value; movedFrom = value; movedAt = now; return }
        if (previous == value) return
        // A phone held still for a while starts a fresh reference, so an old maximum can't turn a small move into a fold.
        if (now - movedAt > STALL_MS && closeStartAt < 0) peak = previous
        if (kotlin.math.abs(value - movedFrom) >= MOVE_DEG) { movedFrom = value; movedAt = now }
        if (HingeTracker.crossedHalfway(previous, value)) onHalfway?.invoke()
        if (expanded) {
            when {
                // Reached flat while revealing: learn how long lit → flat takes for this person.
                tracker.flat && !wasFlat && litAt >= 0 && flatAt < 0 -> {
                    flatAt = now
                    learnOpen((now - litAt).toFloat())
                }
                // Started folding from flat: keep One UI from sleeping, and start the fold-away.
                wasFlat && !tracker.flat -> startClosing(now, value)
                // Nearly closed: learn how long the fold takes, and make sure the bridge is up.
                tracker.closed && closeStartAt >= 0 && closedAt < 0 -> {
                    closedAt = now
                    learnClose((now - closeStartAt).toFloat())
                    if (stayAwake) FoldBridgeActivity.start(appContext)
                }
                // Folding that didn't start from flat (e.g. from half-open): only a real drop counts,
                // so sensor jitter or adjusting a propped phone never starts the bridge.
                !tracker.flat && closeStartAt < 0 && peak - value >= MIN_FOLD_DROP_DEG -> {
                    startClosing(now, value)
                    if (tracker.closed) closedAt = now
                }
                // Opened back up before closing.
                closeStartAt >= 0 && value - trough >= REOPEN_DEG -> {
                    closeStartAt = -1L; closedAt = -1L; reopenedAt = now; peak = value
                    FoldBridgeActivity.cancel()
                }
            }
            if (closeStartAt >= 0) trough = minOf(trough, value) else peak = maxOf(peak, value)
        } else {
            when {
                // Starting to open on the cover: blur the whole cover screen.
                wasClosed && !tracker.closed -> { coverOpeningAt = now; onOpeningStarted?.invoke() }
                tracker.closed -> coverOpeningAt = -1L
            }
        }
    }

    private fun startClosing(now: Long, value: Float) {
        closeStartAt = now; closedAt = -1L; reopenedAt = -1L; trough = value
        onClosingStarted?.invoke()
        if (stayAwake) FoldBridgeActivity.start(appContext)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun targetM(now: Long): Float = when {
        waitingForPanel -> if (expanded) START_M_ON_UNFOLD else START_M_ON_COVER

        // Unfold, like iPhone Duo: the rotating (left) half fades from dark and blurred to clear. Recording the
        // Fold8 showed the panel lights only as the hinge is nearly flat, so a hinge-bound fade was over before it
        // was visible. The fade is a steady, fixed-length ease from the moment the panel is lit instead.
        expanded && litAt >= 0 -> {
            val t = ((now - litAt) / UNFOLD_FADE_MS).coerceIn(0f, 1f)
            if (t >= 1f) { litAt = -1L; flatAt = -1L; 0f }
            else {
                val timed = START_M_ON_UNFOLD * (1f - easeInOutSine(t))
                // With a continuous sensor, a hand that's already flat clears sooner; the timed fade still caps it,
                // so holding the phone half open never leaves Home blurred.
                val flatDeg = HingeTracker.FLAT_ENTER_DEG
                val byAngle = (tracker.visual ?: flatDeg).let { a -> if (litAngle >= flatDeg) 0f else ((flatDeg - a) / (flatDeg - litAngle)).coerceIn(0f, 1f) }
                if (continuous) minOf(timed, START_M_ON_UNFOLD * byAngle) else timed
            }
        }

        // Fold: blur builds with the hinge (continuous) or at the learned speed (stepped), complete at closed.
        expanded && closeStartAt >= 0 -> {
            val since = (now - closeStartAt).toFloat()
            val stalled = closedAt < 0 && (if (continuous) now - movedAt > STALL_MS else now - angleAt > predictedCloseMs + STALL_MS)
            when {
                stalled -> { closeStartAt = -1L; peak = tracker.raw ?: 0f; 0f } // deliberately half-open (flex mode): clear
                closedAt >= 0 && now - closedAt > CLOSED_STALL_MS -> { closeStartAt = -1L; closedAt = -1L; 0f } // never stuck dimmed
                closedAt >= 0 -> 1f
                continuous -> easeInOutSine(((HingeTracker.FLAT_ENTER_DEG - (tracker.visual ?: HingeTracker.FLAT_ENTER_DEG)) /
                    (HingeTracker.FLAT_ENTER_DEG - HingeTracker.CLOSED_ENTER_DEG)).coerceIn(0f, 1f)) * HOLD_M_BEFORE_CLOSED
                else -> easeInOutSine((since / predictedCloseMs).coerceIn(0f, 1f)) * HOLD_M_BEFORE_CLOSED
            }
        }

        // Reopened before closing: settle back.
        expanded && reopenedAt >= 0 -> { if (now - reopenedAt > FINISH_MS * 2) reopenedAt = -1L; 0f }

        // Opening from the cover: quick whole-screen blur until the inner display takes over.
        !expanded && coverOpeningAt >= 0 -> {
            if (now - coverOpeningAt > COVER_OPEN_STALL_MS) { coverOpeningAt = -1L; 0f }
            else if (continuous) easeOutCubic(((tracker.visual ?: 0f) / HingeTracker.HALFWAY_DEG).coerceIn(0f, 1f))
            else easeOutCubic(((now - coverOpeningAt) / COVER_OPEN_MS).coerceIn(0f, 1f))
        }

        // Cover after folding: short focus-in.
        !expanded && coverLitAt >= 0 -> {
            val t = ((now - coverLitAt) / COVER_MS).coerceIn(0f, 1f)
            if (t >= 1f) { coverLitAt = -1L; 0f } else START_M_ON_COVER * (1f - easeOutCubic(t))
        }
        else -> 0f
    }

    private fun learnOpen(ms: Float) {
        predictedOpenMs = (predictedOpenMs * .7f + ms.coerceIn(200f, 1400f) * .3f)
        prefs.edit().putFloat("fold_open_ms", predictedOpenMs).apply()
    }

    private fun learnClose(ms: Float) {
        predictedCloseMs = (predictedCloseMs * .7f + ms.coerceIn(250f, 1800f) * .3f)
        prefs.edit().putFloat("fold_close_ms", predictedCloseMs).apply()
    }
}
private fun easeOutCubic(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
private fun easeInOutSine(t: Float): Float = (-(kotlin.math.cos(Math.PI * t) - 1) / 2).toFloat()

@RequiresApi(33)
private class DuoShader {
    private val shader = RuntimeShader(SOURCE)

    fun effect(width: Float, height: Float, m: Float, cover: Boolean, geometry: FoldGeometry): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("axis", if (geometry.horizontal) 1f else 0f)
        shader.setFloatUniform("hingePos", geometry.hingePx)
        shader.setFloatUniform("side", if (geometry.movingAfterHinge) 1f else -1f)
        shader.setFloatUniform("m", m)
        // 72px on a 1600px-wide canvas in the recreation ≈ 4.5% of width; the cover uses a light version.
        shader.setFloatUniform("maxRadius", width * .045f)
        shader.setFloatUniform("cover", if (cover) 1f else 0f)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }

    companion object {
        private const val SOURCE = """
            uniform shader content;
            uniform float2 size;
            uniform float m;
            uniform float maxRadius;
            uniform float cover;
            uniform float axis;     // 0: the hinge runs top to bottom (compare x); 1: side to side (compare y)
            uniform float hingePos; // the hinge along that axis
            uniform float side;     // -1: the moving half (or the cover's hinge edge) is before it; +1: after it

            // 24-tap disk (3 rings) keeps large radii smooth.
            half4 blur(float2 p, float r) {
                if (r < 0.75) return content.eval(p);
                float a = r * 0.33; float b = r * 0.66; float c = r;
                float a7 = a * 0.7071; float b7 = b * 0.7071; float c7 = c * 0.7071;
                half4 sum = content.eval(p) * 0.08;
                sum += (content.eval(p + float2(a, 0.0)) + content.eval(p + float2(-a, 0.0)) + content.eval(p + float2(0.0, a)) + content.eval(p + float2(0.0, -a))
                      + content.eval(p + float2(a7, a7)) + content.eval(p + float2(-a7, a7)) + content.eval(p + float2(a7, -a7)) + content.eval(p + float2(-a7, -a7))) * 0.05;
                sum += (content.eval(p + float2(b, 0.0)) + content.eval(p + float2(-b, 0.0)) + content.eval(p + float2(0.0, b)) + content.eval(p + float2(0.0, -b))
                      + content.eval(p + float2(b7, b7)) + content.eval(p + float2(-b7, b7)) + content.eval(p + float2(b7, -b7)) + content.eval(p + float2(-b7, -b7))) * 0.04;
                sum += (content.eval(p + float2(c, 0.0)) + content.eval(p + float2(-c, 0.0)) + content.eval(p + float2(0.0, c)) + content.eval(p + float2(0.0, -c))
                      + content.eval(p + float2(c7, c7)) + content.eval(p + float2(-c7, c7)) + content.eval(p + float2(c7, -c7)) + content.eval(p + float2(-c7, -c7))) * 0.025;
                return sum;
            }

            half4 main(float2 p) {
                float mc = clamp(m, 0.0, 1.0);
                float mm = mc * mc * (3.0 - 2.0 * mc) * max(1.0, m); // smoothstep (as in the recreation), scaled by intensity
                float coord = axis < 0.5 ? p.x : p.y;
                float extent = axis < 0.5 ? size.x : size.y;
                if (cover > 0.5) {
                    // Outer screen, as on iPhone Duo: blur and darkness grow away from the hinge edge
                    // toward the free edge. Same curves as the inner half.
                    float eo = clamp(side < 0.0 ? coord / extent : (extent - coord) / extent, 0.0, 1.0);
                    half4 co = blur(p, maxRadius * mm * pow(eo, 1.35));
                    float dO = clamp((eo - 0.2) / 0.8, 0.0, 1.0);
                    float ko = 1.0 - min(1.0, 2.0 * mm * pow(dO, 1.35));
                    return half4(co.rgb * ko, co.a);
                }
                // Inner screen: the half with the cover behind it stays sharp; the moving half is blurred and
                // darkened toward its outer edge.
                float e;
                if (side < 0.0) {
                    if (coord >= hingePos) return content.eval(p);
                    e = clamp((hingePos - coord) / max(hingePos, 1.0), 0.0, 1.0);
                } else {
                    if (coord <= hingePos) return content.eval(p);
                    e = clamp((coord - hingePos) / max(extent - hingePos, 1.0), 0.0, 1.0);
                }
                half4 c = blur(p, maxRadius * mm * pow(e, 1.35));
                float d = clamp((e - 0.2) / 0.8, 0.0, 1.0);
                float k = 1.0 - min(1.0, 2.0 * mm * pow(d, 1.35));
                // A soft band of light that leaves the hinge and crosses the half as it clears, brightest mid-way
                // (idea from FoldFX).
                float band = (e - (1.0 - mc)) / 0.1;
                float sweep = exp(-band * band) * 0.55 * mc * (1.0 - mc);
                return half4(c.rgb * k + half3(sweep) * c.a, c.a);
            }
        """
    }
}

/** Share of the morph during which the still picture stays fully visible. */
private const val STILL_HOLD = .55f
private const val MORPH_UNFOLD_MS = 650f
private const val MORPH_FOLD_MS = 420f
/** Inner panel lights around 120–135° on Z Fold: the cover half is still ~50° from flat. */
private const val START_M_ON_UNFOLD = 1f
/** Unfold fade length (the iPhone Duo reveal reads as ~half a second). */
private const val UNFOLD_FADE_MS = 520f
/** Strongest whole-screen dim while folding. */
private const val FOLD_DIM = .6f
private const val HOLD_M_BEFORE_CLOSED = .9f
// Measured on a Galaxy Z Fold8: the hinge reports only 0/90/180° on a 200 ms grid, so "flat" can arrive up to
// 200 ms after the panel is really flat. Finish faster once it does so the reveal doesn't trail the hand.
private const val FINISH_MS = 120f
private const val STALL_MS = 900f
private const val COVER_MS = 380f
/** The cover lights right at closed, where the Duo outer screen is nearly clean: a light settle. */
private const val START_M_ON_COVER = .7f
private const val COVER_OPEN_MS = 220f
private const val COVER_OPEN_STALL_MS = 2_000L
private const val FOLLOW_MS = 28f
private const val MIN_FOLD_DROP_DEG = 20f
private const val CLOSED_STALL_MS = 1_800L
private const val LIT_TIMEOUT_MS = 1_200L
private const val IDLE_POLL_MS = 50L
/** How much smaller the open screen is at the start of the reveal (97%). */
private const val FOLD_SCALE = .03f
private const val MOVE_DEG = 3f
private const val REOPEN_DEG = 15f
private const val CAPABILITY_KEY = "fold_hinge_capability"

/**
 * The fold effect on a preview (Settings): [m] 0 is open and clear, 1 half folded. Same shader, sweep and scale as Home,
 * with the hinge down the middle and the left half moving.
 */
@Composable
internal fun Modifier.foldPreviewEffect(m: () -> Float): Modifier {
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) DuoShader() else null }
    return graphicsLayer {
        val value = m()
        val settle = 1f - FOLD_SCALE * value.coerceIn(0f, 1f)
        scaleX = settle; scaleY = settle
        if (shader != null && Build.VERSION.SDK_INT >= 33) renderEffect = if (value > 0f)
            shader.effect(size.width, size.height, value, cover = false, geometry = FoldGeometry(false, size.width / 2f, false)) else null
    }.then(if (shader == null) Modifier.drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = m().coerceIn(0f, 1f)), .5f to Color.Transparent, startX = 0f, endX = size.width))
    } else Modifier)
}

/** The fold effect's layout: which way the hinge runs, where it is, and which side of it moves. */
internal data class FoldGeometry(val horizontal: Boolean, val hingePx: Float, val movingAfterHinge: Boolean)

/**
 * In the natural orientation (unfolded landscape; cover portrait) the moving half, or the cover's hinge edge, is
 * on the left. Display rotation moves that edge: 90° to the bottom, 180° to the right, 270° to the top. A real
 * hinge from WindowManager gives the exact position; otherwise it's the middle.
 */
internal fun foldGeometry(rotation: Int, hinge: Hinge?, width: Float, height: Float): FoldGeometry {
    val horizontal = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
    val after = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_180
    val middle = if (horizontal) height / 2f else width / 2f
    val position = hinge?.takeIf { it.vertical != horizontal }?.let { (it.startPx + it.endPx) / 2f } ?: middle
    return FoldGeometry(horizontal, position, after)
}
