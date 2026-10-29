package org.duofold.live;

/** Main-thread buffer pacing. A remap invalidates evidence, not an outstanding
 * submission. Commit callbacks pace new work; they are not buffer-release or
 * visible-pixel evidence. */
final class B8FrameSubmission {
    enum Completion { CURRENT, INVALIDATED, OBSOLETE }
    static final long TIMEOUT_MS = 500;
    private long serial, ticket, epoch, submittedEpoch, submittedAt;

    boolean pending() { return ticket != 0; }
    long age(long now) { return pending() ? Math.max(0, now - submittedAt) : 0; }
    void invalidate() { epoch++; }

    // An actually destroyed Surface has no producer to pace. Its callback may
    // never arrive; it must neither delay nor acknowledge the replacement.
    void surfaceDestroyed() { ticket = 0; invalidate(); }

    long begin(long now) {
        if (pending()) throw new IllegalStateException("Buffer submission already pending");
        ticket = ++serial;
        submittedAt = now;
        submittedEpoch = epoch;
        return ticket;
    }

    Completion complete(long completedTicket) {
        if (completedTicket == 0 || completedTicket != ticket) return Completion.OBSOLETE;
        ticket = 0;
        return submittedEpoch == epoch ? Completion.CURRENT : Completion.INVALIDATED;
    }

    boolean expire(long now) {
        if (!pending() || now - submittedAt <= TIMEOUT_MS) return false;
        ticket = 0;
        invalidate();
        return true;
    }
}
