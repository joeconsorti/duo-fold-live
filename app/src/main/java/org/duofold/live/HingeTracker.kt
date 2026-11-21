package org.duofold.live

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * What a phone's public hinge sensor reports. Some foldables expose only stepped
 * posture values; others provide continuous angles. Capability is learned from
 * a sequence of distinct, meaningfully separated in-between readings.
 *
 * This tracker is a sensor adapter; the privileged Samsung wallpaper-angle
 * reader remains the active production source until handset validation proves
 * a public SensorManager source is suitable on the target firmware.
 */
internal enum class HingeCapability {
    CONTINUOUS,
    STEPPED,
    POSTURE_ONLY,
}

internal class HingeTracker(capability: HingeCapability) {
    var capability = capability; private set
    var raw: Float? = null; private set
    /** Smoothed only after a continuous stream has been established. */
    var visual: Float? = null; private set
    var flat = false; private set
    var closed = false; private set

    private val inBetween = HashSet<Int>()
    private var lastNanos = 0L

    /**
     * Feed one Android sensor event. Invalid, duplicate-time and out-of-order
     * events are ignored completely so they cannot rewind the visual pose,
     * alter endpoint latches, or help classify a stepped sensor as continuous.
     *
     * Returns true only when a stepped/posture-only source is newly classified
     * as continuous.
     */
    fun feed(angle: Float, timestampNanos: Long): Boolean {
        if (!angle.isFinite() || angle < MIN_ANGLE_DEG || angle > MAX_ANGLE_DEG ||
            timestampNanos <= 0L || (lastNanos > 0L && timestampNanos <= lastNanos)
        ) return false

        val before = capability
        if (capability == HingeCapability.POSTURE_ONLY) capability = HingeCapability.STEPPED

        if (capability != HingeCapability.CONTINUOUS &&
            STEPS.none { abs(angle - it) < STEP_TOLERANCE_DEG }
        ) {
            inBetween += angle.roundToInt()
            val spread = (inBetween.maxOrNull() ?: 0) - (inBetween.minOrNull() ?: 0)
            if (inBetween.size >= CONTINUOUS_SAMPLES && spread >= CONTINUOUS_SPREAD_DEG) {
                capability = HingeCapability.CONTINUOUS
            }
        }

        val previousVisual = visual
        visual = if (capability == HingeCapability.CONTINUOUS &&
            previousVisual != null && lastNanos > 0L
        ) {
            val elapsedSeconds = (timestampNanos - lastNanos) / 1e9f
            val alpha = 1f - exp(-elapsedSeconds / SMOOTHING_SECONDS)
            previousVisual + alpha * (angle - previousVisual)
        } else {
            angle
        }

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
        private const val MIN_ANGLE_DEG = 0f
        private const val MAX_ANGLE_DEG = 180f
        private const val HALFWAY_BAND_DEG = 4f
        private const val STEP_TOLERANCE_DEG = 2f
        private const val CONTINUOUS_SAMPLES = 3
        private const val CONTINUOUS_SPREAD_DEG = 12
        private const val SMOOTHING_SECONDS = .07f
        private val STEPS = floatArrayOf(0f, 90f, 180f)

        /** True once per pass through 90°, including stepped sensors. */
        fun crossedHalfway(previous: Float, current: Float): Boolean {
            fun side(a: Float) = when {
                a < HALFWAY_DEG - HALFWAY_BAND_DEG -> -1
                a > HALFWAY_DEG + HALFWAY_BAND_DEG -> 1
                else -> 0
            }
            return side(previous) != 0 && side(previous) != side(current)
        }
    }
}
