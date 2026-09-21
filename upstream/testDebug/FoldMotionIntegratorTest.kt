package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldMotionIntegratorTest {
    @Test fun rejectsCachedSampleBeforeForegroundBoundary() {
        val integrator = runningIntegrator(boundaryNs = 1_000L)

        assertEquals(FoldMotionIntegrationResult.STALE, integrator.add(999L, 1f, 0f, 0f))
        assertEquals(FoldMotionIntegrationResult.BASELINE, integrator.add(1_000L, 1f, 0f, 0f))
        assertEquals(FoldMotionRotation(), integrator.rotation)
    }

    @Test fun outOfOrderSampleDoesNotMoveBaselineBackwards() {
        val integrator = runningIntegrator()
        integrator.add(1_000_000_000L, 1f, 0f, 0f)

        assertEquals(FoldMotionIntegrationResult.OUT_OF_ORDER, integrator.add(900_000_000L, 50f, 0f, 0f))
        assertEquals(FoldMotionIntegrationResult.INTEGRATED, integrator.add(1_100_000_000L, 1f, 0f, 0f))
        assertEquals(Math.toDegrees(0.1), integrator.rotation.xDegrees, 0.0001)
    }

    @Test fun longGapIsSkippedBeforeIntegrationResumes() {
        val integrator = runningIntegrator()
        integrator.add(1_000_000_000L, 1f, 0f, 0f)

        assertEquals(FoldMotionIntegrationResult.GAP_SKIPPED, integrator.add(1_400_000_000L, 20f, 0f, 0f))
        assertEquals(FoldMotionIntegrationResult.INTEGRATED, integrator.add(1_500_000_000L, 1f, 0f, 0f))
        assertEquals(Math.toDegrees(0.1), integrator.rotation.xDegrees, 0.0001)
    }

    @Test fun restoredRotationStartsWithFreshTimestampBaseline() {
        val integrator = FoldMotionIntegrator().apply {
            restore(FoldMotionRotation(12.0, -4.0, 3.0))
            beginForeground(9_000_000_000L)
            setPaused(false)
        }

        assertEquals(FoldMotionIntegrationResult.BASELINE, integrator.add(9_500_000_000L, 100f, 100f, 100f))
        assertEquals(FoldMotionRotation(12.0, -4.0, 3.0), integrator.rotation)
    }

    private fun runningIntegrator(boundaryNs: Long = 0L) = FoldMotionIntegrator().apply {
        beginForeground(boundaryNs)
        setPaused(false)
    }
}
