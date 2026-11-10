package org.duofold.live.experimental.b8;

import android.os.SystemClock;
import java.util.function.BooleanSupplier;

/** A bounded check after owned animation layers have been removed. */
final class LiveB8EndpointCheck {
    private final LiveB8Power power;
    private final LiveB8Task task;
    final int base;
    private final String physical;
    private final long deadline;
    private long next, observedAt;
    private String previous = "";
    private boolean repaired;
    boolean finished;

    LiveB8EndpointCheck(LiveB8Power power, LiveB8Task task, int base, String physical, long now) {
        this.power = power;
        this.task = task;
        this.base = base;
        this.physical = physical;
        deadline = now + 500;
    }

    private boolean nativeEndpoint(BooleanSupplier eligible) {
        try {
            return SystemClock.elapsedRealtime() < deadline && eligible.getAsBoolean()
                    && power.state(true) == base && power.state(false) == base
                    && eligible.getAsBoolean();
        } catch (Exception unavailable) {
            return false;
        }
    }

    String tick(BooleanSupplier eligible) throws Exception {
        long now = SystemClock.elapsedRealtime();
        if (!eligible.getAsBoolean()) {
            finished = true;
            return "SKIPPED new motion, lock or stale input";
        }
        if (now >= deadline) {
            finished = true;
            return "UNRESOLVED " + previous;
        }
        if (now < next) return "";
        next = now + 50;
        if (power.state(true) != base) {
            previous = "native posture settling";
            return "";
        }
        int current = power.state(false);
        Object display = power.info(0);
        if (display == null) {
            previous = "primary unavailable";
            return "";
        }
        String actual = LiveB8Power.physical(display);
        if (LiveB8Power.number(display, "state") != 2
                || LiveB8Power.number(display, "committedState") != 2) {
            previous = "primary power settling";
            return "";
        }
        String issue = current != base ? "STATE " + current
                : !physical.equals(actual) ? "PANEL " + actual : task.endpointLayout(physical);
        if (!eligible.getAsBoolean() || power.state(true) != base) {
            finished = true;
            return "SKIPPED posture changed during check";
        }
        if (issue.isEmpty()) {
            finished = true;
            return repaired ? "REPAIR_VERIFIED state and layout; pixels unmeasured"
                    : "OK native state, panel and layout; pixels unmeasured";
        }
        if (!issue.equals(previous)) {
            previous = issue;
            observedAt = now;
            return "";
        }
        if (repaired || now - observedAt < 50) return "";
        if (SystemClock.elapsedRealtime() >= deadline) {
            finished = true;
            return "UNRESOLVED check budget elapsed";
        }
        // A different state owner or unresolved physical remap is not authority to
        // force a new display mode. Only release our client's known last request.
        if (current != base) {
            if (!power.releaseEndpointOverride(base, current, eligible)) {
                finished = true;
                return "UNRESOLVED foreign or changed state";
            }
        } else if (!physical.equals(actual)) {
            finished = true;
            return "UNRESOLVED physical panel mismatch after native release";
        } else if (task.observesNativePlacement()) {
            // Give Android the remaining observation budget. Do not report a
            // successful correction or "layout changed" when none was attempted.
            return "";
        } else if (!task.repairEndpointLayout(physical, issue, () -> nativeEndpoint(eligible))) {
            finished = true;
            return "SKIPPED layout changed before correction";
        }
        repaired = true;
        return "REPAIR_SUBMITTED " + issue + "; readback pending";
    }
}
