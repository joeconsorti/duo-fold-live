package com.mccal.folio

import kotlin.math.PI

internal data class FoldMotionRotation(
    val xDegrees: Double = 0.0,
    val yDegrees: Double = 0.0,
    val zDegrees: Double = 0.0,
)

private data class FoldMotionAxes(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0)

internal enum class FoldMotionIntegrationResult {
    BASELINE,
    INTEGRATED,
    STALE,
    OUT_OF_ORDER,
    GAP_SKIPPED,
    PAUSED,
}

/** Integrates one gyroscope. The result is a relative sensor estimate, never a hinge angle. */
internal class FoldMotionIntegrator(
    private val maximumGapNs: Long = 250_000_000L,
) {
    var rotation = FoldMotionRotation()
        private set
    private var accumulatedRadians = FoldMotionAxes()

    private var foregroundBoundaryNs = Long.MAX_VALUE
    private var previousTimestampNs: Long? = null
    private var paused = true

    fun beginForeground(boundaryNs: Long) {
        foregroundBoundaryNs = boundaryNs
        previousTimestampNs = null
    }

    fun setPaused(value: Boolean) {
        if (paused != value) previousTimestampNs = null
        paused = value
    }

    fun reset() {
        rotation = FoldMotionRotation()
        accumulatedRadians = FoldMotionAxes()
        previousTimestampNs = null
    }

    fun restore(rotation: FoldMotionRotation) {
        this.rotation = rotation
        accumulatedRadians = FoldMotionAxes(
            rotation.xDegrees * PI / 180.0,
            rotation.yDegrees * PI / 180.0,
            rotation.zDegrees * PI / 180.0,
        )
        previousTimestampNs = null
    }

    fun add(timestampNs: Long, xRadiansPerSecond: Float, yRadiansPerSecond: Float, zRadiansPerSecond: Float): FoldMotionIntegrationResult {
        if (timestampNs < foregroundBoundaryNs) return FoldMotionIntegrationResult.STALE
        if (paused) return FoldMotionIntegrationResult.PAUSED
        val previous = previousTimestampNs
        if (previous == null) {
            previousTimestampNs = timestampNs
            return FoldMotionIntegrationResult.BASELINE
        }
        val deltaNs = timestampNs - previous
        if (deltaNs <= 0L) return FoldMotionIntegrationResult.OUT_OF_ORDER
        previousTimestampNs = timestampNs
        if (deltaNs > maximumGapNs) return FoldMotionIntegrationResult.GAP_SKIPPED

        val seconds = deltaNs / 1_000_000_000.0
        val x = xRadiansPerSecond * seconds
        val y = yRadiansPerSecond * seconds
        val z = zRadiansPerSecond * seconds
        accumulatedRadians = FoldMotionAxes(
            accumulatedRadians.x + x,
            accumulatedRadians.y + y,
            accumulatedRadians.z + z,
        )
        rotation = FoldMotionRotation(
            accumulatedRadians.x * 180.0 / PI,
            accumulatedRadians.y * 180.0 / PI,
            accumulatedRadians.z * 180.0 / PI,
        )
        return FoldMotionIntegrationResult.INTEGRATED
    }
}
