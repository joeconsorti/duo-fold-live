package org.duofold.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HingeTrackerTest {
    private fun HingeTracker.play(angles: List<Float>, stepMillis: Long) =
        angles.forEachIndexed { i, a -> feed(a, (i + 1) * stepMillis * 1_000_000L) }

    @Test fun `a Fold8 style stepped stream stays stepped and is never smoothed`() {
        val tracker = HingeTracker(HingeCapability.STEPPED)
        listOf(0f, 90f, 180f, 90f, 0f).forEachIndexed { i, a ->
            tracker.feed(a, (i + 1) * 200_000_000L)
            assertEquals(a, tracker.visual)
        }
        assertEquals(HingeCapability.STEPPED, tracker.capability)
    }

    @Test fun `one odd reading does not make a sensor continuous`() {
        val tracker = HingeTracker(HingeCapability.STEPPED)
        assertFalse(tracker.feed(37f, 1_000_000L))
        assertEquals(HingeCapability.STEPPED, tracker.capability)
        tracker.feed(52f, 2_000_000L)
        assertTrue(tracker.feed(66f, 3_000_000L))
        assertEquals(HingeCapability.CONTINUOUS, tracker.capability)
    }

    @Test fun `small near endpoint jitter cannot classify a stepped sensor as continuous`() {
        val tracker = HingeTracker(HingeCapability.STEPPED)
        listOf(3f, 4f, 5f, 6f, 7f, 8f).forEachIndexed { i, angle ->
            tracker.feed(angle, (i + 1L) * 10_000_000L)
        }
        assertEquals(HingeCapability.STEPPED, tracker.capability)
    }

    @Test fun `continuous classification requires distinct readings with useful spread`() {
        val tracker = HingeTracker(HingeCapability.STEPPED)
        tracker.feed(20f, 10_000_000L)
        tracker.feed(35f, 20_000_000L)
        assertEquals(HingeCapability.STEPPED, tracker.capability)
        assertTrue(tracker.feed(50f, 30_000_000L))
        assertEquals(HingeCapability.CONTINUOUS, tracker.capability)
    }

    @Test fun `a phone without a hinge sensor learns from its first valid reading`() {
        val tracker = HingeTracker(HingeCapability.POSTURE_ONLY)
        tracker.feed(180f, 1L)
        assertEquals(HingeCapability.STEPPED, tracker.capability)
    }

    @Test fun `smoothing depends on elapsed time not sensor report rate`() {
        fun smoothedAfter100ms(stepMillis: Long): Float {
            val tracker = HingeTracker(HingeCapability.CONTINUOUS)
            tracker.feed(0f, 1L)
            val samples = (100 / stepMillis).toInt()
            tracker.play(List(samples) { 90f }, stepMillis)
            return tracker.visual!!
        }
        assertEquals(smoothedAfter100ms(33), smoothedAfter100ms(8), 6f)
        assertTrue(smoothedAfter100ms(8) in 60f..89f)
    }

    @Test fun `flat and closed use separate enter and exit angles`() {
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

    @Test fun `invalid and nonmonotonic sensor events are ignored without state changes`() {
        val tracker = HingeTracker(HingeCapability.CONTINUOUS)
        tracker.feed(10f, 100_000_000L)
        tracker.feed(40f, 200_000_000L)
        val raw = tracker.raw
        val visual = tracker.visual
        val flat = tracker.flat
        val closed = tracker.closed

        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 181f)) {
            assertFalse(tracker.feed(bad, 300_000_000L))
            assertEquals(raw, tracker.raw)
            assertEquals(visual, tracker.visual)
            assertEquals(flat, tracker.flat)
            assertEquals(closed, tracker.closed)
        }
        assertFalse(tracker.feed(120f, 150_000_000L))
        assertFalse(tracker.feed(120f, 200_000_000L))
        assertFalse(tracker.feed(120f, 0L))
        assertEquals(raw, tracker.raw)
        assertEquals(visual, tracker.visual)
    }

    @Test fun `the halfway tick fires once per pass`() {
        assertTrue(HingeTracker.crossedHalfway(0f, 90f))
        assertFalse(HingeTracker.crossedHalfway(90f, 180f))
        assertTrue(HingeTracker.crossedHalfway(180f, 90f))
        assertFalse(HingeTracker.crossedHalfway(90f, 0f))
        assertTrue(HingeTracker.crossedHalfway(95f, 89f))
        assertFalse(HingeTracker.crossedHalfway(89f, 91f))
        assertFalse(HingeTracker.crossedHalfway(91f, 87f))
    }
}
