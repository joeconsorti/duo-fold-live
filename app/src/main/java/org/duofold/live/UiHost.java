package org.duofold.live;

import android.os.Handler;
import android.os.Looper;

/**
 * Tracks the root-launched "registered host" (PhotoInstrumentation): an Android-managed instrumentation
 * inside Duo's own process that holds shell-level compositor permissions. The fold animation is drawn into
 * a compositor layer created by that host, so Duo needs neither the overlay permission nor an enabled
 * accessibility service.
 */
public final class UiHost {
    private UiHost() {}
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean ready;
    private static volatile android.app.UiAutomation automation;

    public static void setAutomation(android.app.UiAutomation a) { automation = a; }
    static android.app.UiAutomation automation() { return automation; }

    public static boolean ready() { return ready; }

    /** Called by the instrumentation once the compositor permissions are adopted. */
    public static void connected() {
        ready = true;
        MAIN.post(() -> {
            StandaloneService host = StandaloneService.Companion.getInstance();
            if (host != null) host.restart();
        });
    }

    /** Called when the instrumentation ends; the permissions (and with them the layer) are gone. */
    public static void disconnected() {
        ready = false;
        MAIN.post(() -> {
            StandaloneService host = StandaloneService.Companion.getInstance();
            if (host != null) host.hostLost();
        });
    }
}
