package org.duofold.live;

/* loaded from: classes4.dex */
final class B8RenderPolicy {
    B8RenderPolicy() {
    }

    // One queued draw consumes the latest angle. Never queue a frame per sensor reply.
    static long frameDelay(long now, long lastDraw) {
        return lastDraw <= 0 || now < lastDraw ? 0 : Math.max(0,17-(now-lastDraw));
    }

    static long movingGap(long now,long lastDraw,boolean wasMoving) {
        return wasMoving && lastDraw>0 && now>=lastDraw ? now-lastDraw : 0;
    }

    // Before acquiring a new buffer: an uncertain mapping must retain the old one.
    static boolean matchesPanelShape(int shortSide, int longSide, int width, int height) {
        return shortSide > 0 && longSide > 0 && Math.min(width,height) == shortSide
                && Math.max(width,height) == longSide;
    }

    static boolean canAcquire(boolean coherent, boolean rootExcluded, boolean childExcluded) {
        return coherent && rootExcluded && childExcluded;
    }

    // Stable screenshot content does not expire. Surface/mapping/image checks remain in the caller.
    static boolean commitCurrent(boolean animated, long committedAt, long now) {
        return committedAt > 0 && now >= committedAt && (!animated || now - committedAt <= 250);
    }

    static boolean committedPoseCurrent(boolean animated,float committedAngle,float target,long committedAt,long now) {
        if(animated&&(!Float.isFinite(committedAngle)||!Float.isFinite(target)))return false;
        return commitCurrent(animated&&needsAnimation(committedAngle,target),committedAt,now);
    }

    static boolean shouldSubmit(boolean animated, boolean angleChanged, boolean valid,
                                boolean pending, long commitAge, boolean primaryCheck) {
        // Coalesce hinge updates until the previous buffer's callback retires.
        // Drawing through a pending commit can stall lock/post on the UI thread
        // and makes a transaction's associated frame ambiguous.
        if (pending) return false;
        return !valid || (animated && angleChanged) || (primaryCheck && commitAge >= 150);
    }

    static boolean needsAnimation(float visual, float target) {
        return Float.isFinite(visual) && Float.isFinite(target) && Math.abs(visual - target) >= 0.01f;
    }
}
