package org.duofold.live

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * What a phone's public hinge sensor really reports. Android doesn't require `TYPE_HINGE_ANGLE` to be continuous:
 * a Galaxy Z Fold8 reports only 0°, 90° and 180° to apps, even though Samsung's own components get finer readings.
 */
internal enum class HingeCapability {
    /** Real in-between angles while the hinge moves (e.g. 12°, 17°, 25°…). */
    CONTINUOUS,
    /** A few fixed steps, like the Fold8's 0/90/180. */
    STEPPED,
    /** No hinge sensor: only the fold posture and which display is on. */
    POSTURE_ONLY,
}

/**
 * Turns raw hinge readings into the fold's logical state and a picture-ready angle.
 *
 * State (flat, closed) comes from the raw angle with separate enter and exit thresholds, so a continuous sensor
 * hovering near a threshold can't flicker and nothing waits on a filter. Only a proven continuous stream is smoothed,
 * with a time-based filter that behaves the same at any sensor rate; smoothing 0→90→180 steps would invent angles the
 * hinge never reported.
 */
internal class HingeTracker(capability: HingeCapability) {
    var capability = capability; private set
    var raw: Float? = null; private set
    /** The angle to draw from: smoothed on continuous sensors, otherwise the raw reading. */
    var visual: Float? = null; private set
    var flat = false; private set
    var closed = false; private set

    private val inBetween = HashSet<Int>()
    private var lastNanos = 0L

    /** Feeds one reading ([timestampNanos] from the sensor event). True when the capability was just learned. */
    fun feed(angle: Float, timestampNanos: Long): Boolean {
        val before = capability
        if (capability == HingeCapability.POSTURE_ONLY) capability = HingeCapability.STEPPED
        if (capability != HingeCapability.CONTINUOUS && STEPS.none { abs(angle - it) < STEP_TOLERANCE_DEG }) {
            // A few different in-between readings, not one odd sample, before trusting the stream as continuous.
            inBetween += angle.roundToInt()
            if (inBetween.size >= CONTINUOUS_SAMPLES) capability = HingeCapability.CONTINUOUS
        }
        val previousVisual = visual
        visual = if (capability == HingeCapability.CONTINUOUS && previousVisual != null && timestampNanos > lastNanos && lastNanos > 0) {
            val alpha = 1f - exp(-((timestampNanos - lastNanos) / 1e9f) / SMOOTHING_SECONDS)
            previousVisual + alpha * (angle - previousVisual)
        } else angle
        lastNanos = timestampNanos
        raw = angle
        flat = if (flat) angle >= FLAT_EXIT_DEG else angle >= FLAT_ENTER_DEG
        closed = if (closed) angle <= CLOSED_EXIT_DEG else angle <= CLOSED_ENTER_DEG
        return capability != before && capability == HingeCapability.CONTINUOUS
    }

    companion object {
        const val FLAT_ENTER_DEG = 175f
        const val FLAT_EXIT_DEG = 165f
        const val CLOSED_ENTER_DEG = 5f
        const val CLOSED_EXIT_DEG = 12f
        const val HALFWAY_DEG = 90f
        private const val HALFWAY_BAND_DEG = 4f
        private const val STEP_TOLERANCE_DEG = 2f
        private const val CONTINUOUS_SAMPLES = 3
        private const val SMOOTHING_SECONDS = .07f
        private val STEPS = floatArrayOf(0f, 90f, 180f)

        /**
         * True once per pass through 90°: leaving one side for the middle band or the other side. Step sensors land
         * exactly on 90°, so opening counts at 0→90 and closing at 180→90, never again on the next step.
         */
        fun crossedHalfway(previous: Float, current: Float): Boolean {
            fun side(a: Float) = when { a < HALFWAY_DEG - HALFWAY_BAND_DEG -> -1; a > HALFWAY_DEG + HALFWAY_BAND_DEG -> 1; else -> 0 }
            return side(previous) != 0 && side(previous) != side(current)
        }
    }
}
