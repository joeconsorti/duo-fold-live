package org.duofold.live;

import android.content.Context;

/** Single place that says whether the registered host must run for the fold animation. */
public final class OverlayPolicy {
    private OverlayPolicy() {}
    public static boolean wanted(Context c) {
        return c.getSharedPreferences("standalone", 0).getBoolean("enabled", false);
    }
}
