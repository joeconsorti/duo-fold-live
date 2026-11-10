package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class B8FrameSubmissionTest {
    @Test public void slowCommitsCoalesceMotionAndImmediateReversalToNewestPose() {
        B8FrameSubmission gate = new B8FrameSubmission();
        long ticket = gate.begin(1000);
        float newest = 90;
        for (float angle : new float[]{80, 60, 30, 50, 100, 140}) {
            newest = angle;
            assertFalse(B8RenderPolicy.shouldSubmit(true, true, true, gate.pending(), 100, false));
        }
        assertEquals(B8FrameSubmission.Completion.CURRENT, gate.complete(ticket));
        assertTrue(B8RenderPolicy.shouldSubmit(true, B8RenderPolicy.needsAnimation(90, newest), true, gate.pending(), 1, false));
        assertEquals(140, newest, 0); // Next draw reads the newest pose, with no sample queue.
        gate.begin(1100);
        assertFalse(B8RenderPolicy.shouldSubmit(true, true, false, gate.pending(), 300, true));
    }

    @Test public void remapCannotOpenAnotherSubmissionBeforeOldCallbackDrains() {
        B8FrameSubmission gate = new B8FrameSubmission();
        long old = gate.begin(1000);
        gate.invalidate(); // Display identity, layout and surface callbacks may all invalidate.
        gate.invalidate();
        assertTrue(gate.pending());
        try { gate.begin(1010); fail("remap must not queue behind the pending frame"); }
        catch (IllegalStateException expected) { }
        assertEquals(B8FrameSubmission.Completion.INVALIDATED, gate.complete(old));
        long current = gate.begin(1020);
        assertEquals(B8FrameSubmission.Completion.OBSOLETE, gate.complete(old));
        assertTrue(gate.pending());
        assertEquals(B8FrameSubmission.Completion.CURRENT, gate.complete(current));
    }

    @Test public void missingCallbackHasBoundedRetryAndCannotAcknowledgeReplacement() {
        B8FrameSubmission gate = new B8FrameSubmission();
        long old = gate.begin(1000);
        assertFalse(gate.expire(1500));
        assertTrue(gate.expire(1501));
        long replacement = gate.begin(1501);
        assertEquals(B8FrameSubmission.Completion.OBSOLETE, gate.complete(old));
        assertTrue(gate.pending());
        assertEquals(99, gate.age(1600));
        assertEquals(B8FrameSubmission.Completion.CURRENT, gate.complete(replacement));
        assertFalse(gate.pending());
    }

    @Test public void idleAndBackwardClockDoNotExpireWork() {
        B8FrameSubmission gate = new B8FrameSubmission();
        assertFalse(gate.expire(9000));
        gate.begin(10000);
        assertFalse(gate.expire(9000));
        assertEquals(0, gate.age(9000));
    }

    @Test public void destroyedSurfaceDoesNotDelayReplacementOrAcknowledgeItsFrame() {
        B8FrameSubmission gate = new B8FrameSubmission();
        long old = gate.begin(1000);
        gate.surfaceDestroyed();
        assertFalse(gate.pending());
        long replacement = gate.begin(1001); // No 500 ms wait for a destroyed producer.
        assertEquals(B8FrameSubmission.Completion.OBSOLETE, gate.complete(old));
        assertTrue(gate.pending());
        assertEquals(B8FrameSubmission.Completion.CURRENT, gate.complete(replacement));
    }
}
