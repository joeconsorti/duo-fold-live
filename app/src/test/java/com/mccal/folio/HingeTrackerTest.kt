package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HingeTrackerTest {
    private fun HingeTracker.play(angles: List<Float>, stepMillis: Long) =
        angles.forEachIndexed { i, a -> feed(a, (i + 1) * stepMillis * 1_000_000L) }

    @Test fun `a Fold8-style stepped stream stays stepped and is never smoothed`() {
        val tracker = HingeTracker(HingeCapability.STEPPED)
        listOf(0f, 90f, 180f, 90f, 0f).forEachIndexed { i, a ->
            tracker.feed(a, (i + 1) * 200_000_000L)
            assertEquals(a, tracker.visual)
        }
        assertEquals(HingeCapability.STEPPED, tracker.capability)
    }

    @Test fun `one odd reading doesn't make a sensor continuous, a few different ones do`() {
        val tracker = HingeTracker(HingeCapability.STEPPED)
        assertFalse(tracker.feed(37f, 1_000_000L))
        assertEquals(HingeCapability.STEPPED, tracker.capability)
        tracker.feed(52f, 2_000_000L)
        assertTrue(tracker.feed(66f, 3_000_000L))
        assertEquals(HingeCapability.CONTINUOUS, tracker.capability)
    }

    @Test fun `a phone without a hinge sensor learns it has one from its first reading`() {
        val tracker = HingeTracker(HingeCapability.POSTURE_ONLY)
        tracker.feed(180f, 1L)
        assertEquals(HingeCapability.STEPPED, tracker.capability)
    }

    @Test fun `smoothing depends on elapsed time, not on how fast the sensor reports`() {
        fun smoothedAfter100ms(stepMillis: Long): Float {
            val tracker = HingeTracker(HingeCapability.CONTINUOUS)
            tracker.feed(0f, 1L)
            val samples = (100 / stepMillis).toInt()
            tracker.play(List(samples) { 90f }, stepMillis)
            return tracker.visual!!
        }
        // 30 Hz and 120 Hz reach nearly the same smoothed angle after the same 100 ms.
        assertEquals(smoothedAfter100ms(33), smoothedAfter100ms(8), 6f)
        assertTrue(smoothedAfter100ms(8) in 60f..89f)
    }

    @Test fun `flat and closed use separate enter and exit angles so hovering doesn't flicker`() {
        val tracker = HingeTracker(HingeCapability.CONTINUOUS)
        tracker.feed(176f, 1L); assertTrue(tracker.flat)
        tracker.feed(170f, 2L); assertTrue(tracker.flat)
        tracker.feed(174f, 3L); assertTrue(tracker.flat)
        tracker.feed(164f, 4L); assertFalse(tracker.flat)
        tracker.feed(172f, 5L); assertFalse(tracker.flat)
        tracker.feed(4f, 6L); assertTrue(tracker.closed)
        tracker.feed(10f, 7L); assertTrue(tracker.closed)
        tracker.feed(13f, 8L); assertFalse(tracker.closed)
    }

    @Test fun `the halfway tick fires once per pass`() {
        // Stepped: opening ticks at 0→90 only; closing at 180→90 only.
        assertTrue(HingeTracker.crossedHalfway(0f, 90f))
        assertFalse(HingeTracker.crossedHalfway(90f, 180f))
        assertTrue(HingeTracker.crossedHalfway(180f, 90f))
        assertFalse(HingeTracker.crossedHalfway(90f, 0f))
        // Continuous: jitter around 90° doesn't tick again.
        assertTrue(HingeTracker.crossedHalfway(95f, 89f))
        assertFalse(HingeTracker.crossedHalfway(89f, 91f))
        assertFalse(HingeTracker.crossedHalfway(91f, 87f))
    }
}
