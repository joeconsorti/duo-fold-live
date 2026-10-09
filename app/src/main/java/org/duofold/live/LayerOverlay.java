package org.duofold.live;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.IBinder;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;

/**
 * Hosts a view hierarchy on one display WITHOUT an overlay permission or an accessibility service. It must run inside
 * the registered host (PhotoInstrumentation), which holds ACCESS_SURFACE_FLINGER.
 *
 * The content lives in a SurfaceControlViewHost layer that has no input channel, is not focusable and not touchable, so
 * taps always pass through. Two ways to put the layer on a display (SharedPreferences "standalone" / "primary_mode"):
 *   wm   (default) : attached through the window manager (attachAccessibilityOverlayToDisplay). Confirmed working: shows
 *                    on both panels and touch is normal.
 *   free           : placed on the display's layer stack by hand; conservative fallback, shows on the active panel only.
 */
final class LayerOverlay {
    private final Context context;
    private final Display display;
    private final String title;
    private final String mode;
    private SurfaceControlViewHost host;
    private SurfaceControlViewHost.SurfacePackage pkg;
    private SurfaceControl layer;
    private int width, height;
    private boolean open;

    LayerOverlay(Context base, Display display, String title) {
        this.context = base.createDisplayContext(display);
        this.display = display;
        this.title = title;
        String p = base.getSharedPreferences("standalone", 0).getString("primary_mode", "wm");
        this.mode = "free".equals(p) ? "free" : "wm"; // the old "window" renderer was removed: it blocked touches
    }

    Context getContext() { return context; }
    boolean isOpen() { return open; }
    SurfaceControl surface() { return layer; }

    int[] size() {
        Point p = new Point();
        display.getRealSize(p);
        return new int[]{p.x, p.y};
    }

    void show(View view) throws Exception {
        int[] s = size();
        width = s[0];
        height = s[1];
        if (width <= 0 || height <= 0) throw new IllegalStateException("Display has no size");
        showLayer(view);
        RecoveryLog.add("Overlay '" + title + "' attached: " + describe());
    }

    private void showLayer(View view) throws Exception {
        if (context.checkSelfPermission("android.permission.ACCESS_SURFACE_FLINGER") != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Compositor permission not adopted by the registered host");
        host = new SurfaceControlViewHost(context, display, (IBinder) null);
        // The default parameters of setView(view, w, h) have flags = 0: the embedded window then gets an input channel,
        // takes focus and covers the whole screen with a touchable region (seen in dumpsys input as a focused
        // "Embedded{}" window owned by this app). Make it non-touchable, non-focusable and without an input channel.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT);
        try {
            java.lang.reflect.Field features = WindowManager.LayoutParams.class.getField("inputFeatures");
            int noChannel = WindowManager.LayoutParams.class.getField("INPUT_FEATURE_NO_INPUT_CHANNEL").getInt(null);
            features.setInt(lp, features.getInt(lp) | noChannel);
        } catch (Throwable t) {
            RecoveryLog.add("No-input-channel flag unavailable for '" + title + "': " + t);
        }
        try {
            // setView(View, WindowManager.LayoutParams) is hidden in the SDK; the registered host has the exemptions.
            host.getClass().getMethod("setView", View.class, WindowManager.LayoutParams.class).invoke(host, view, lp);
        } catch (Throwable t) {
            RecoveryLog.add("setView(LayoutParams) unavailable for '" + title + "' (" + t + "); using the default window parameters");
            host.setView(view, width, height);
        }
        pkg = host.getSurfacePackage();
        if (pkg == null) throw new IllegalStateException("No surface package");
        layer = (SurfaceControl) pkg.getClass().getMethod("getSurfaceControl").invoke(pkg);
        if (layer == null || !layer.isValid()) throw new IllegalStateException("Overlay surface invalid");
        open = true;
        boolean viaWindowManager = false;
        if ("wm".equals(mode)) {
            try {
                android.app.UiAutomation automation = UiHost.automation();
                if (automation == null) throw new IllegalStateException("No registered host automation");
                DisplayOverlayAttachment attachment = new DisplayOverlayAttachment(automation);
                final SurfaceControl requested = layer;
                String detail = attachment.request(display.getDisplayId(), layer, new android.os.Handler(android.os.Looper.getMainLooper()), code -> {
                    RecoveryLog.add("Display overlay attach result for '" + title + "': " + code + (code == 0 ? " (success)" : ""));
                    if (code == 0 && open && layer == requested) showLayerNow();
                    if (code != 0 && open && layer == requested) {
                        try { place(); RecoveryLog.add("Fell back to a free-standing layer for '" + title + "'"); } catch (Exception e) { RecoveryLog.add("Fallback layer failed: " + e); }
                    }
                });
                viaWindowManager = true;
                showLayerNow();
                RecoveryLog.add("Display overlay requested via window manager: " + detail);
            } catch (Throwable t) {
                Throwable c = t;
                while (c.getCause() != null) c = c.getCause();
                RecoveryLog.add("Display overlay attach unavailable (" + c.getClass().getSimpleName() + ": " + c.getMessage() + "); using a free-standing layer");
            }
        }
        if (!viaWindowManager) place();
    }

    private static int stackOf(Display d) throws Exception {
        return (Integer) Display.class.getMethod("getLayerStack").invoke(d);
    }

    String describe() {
        String where;
        try { where = "layerStack=" + stackOf(display); } catch (Exception e) { where = "layerStack unavailable"; }
        return "mode=" + mode + " display=" + display.getDisplayId() + " " + where + " size=" + width + "x" + height;
    }

    /** A SurfaceControlViewHost root is created hidden: whoever embeds it must show it. */
    private void showLayerNow() {
        try {
            if (layer == null || !layer.isValid()) return;
            try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                t.setVisibility(layer, true);
                t.apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Free-standing placement: on the display's layer stack, above everything, no input. */
    private void place() throws Exception {
        int stack = stackOf(display);
        if (display.getDisplayId() != 0) {
            // A second panel that shares the primary's layer stack would paint over the active screen.
            android.hardware.display.DisplayManager dm = context.getSystemService(android.hardware.display.DisplayManager.class);
            Display primary = dm == null ? null : dm.getDisplay(0);
            if (primary != null && stackOf(primary) == stack)
                throw new IllegalStateException("Display " + display.getDisplayId() + " shares layer stack " + stack + " with the primary display; not drawing the second-panel layer");
        }
        try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
            SurfaceControl.Transaction.class.getMethod("setLayerStack", SurfaceControl.class, int.class).invoke(t, layer, stack);
            t.setLayer(layer, Integer.MAX_VALUE);
            t.setVisibility(layer, true);
            t.apply();
        }
    }

    /** Follows rotation / panel size changes without rebuilding the hierarchy. */
    void resize() {
        if (!open || host == null) return;
        try {
            int[] s = size();
            if (s[0] <= 0 || s[1] <= 0) return;
            if (s[0] != width || s[1] != height) {
                width = s[0];
                height = s[1];
                try {
                    host.getClass().getMethod("relayout", int.class, int.class).invoke(host, width, height);
                } catch (Throwable t) { // not in this SDK's public surface: resize through the layout parameters instead
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSPARENT);
                    host.getClass().getMethod("relayout", WindowManager.LayoutParams.class).invoke(host, lp);
                }
            }
            if ("free".equals(mode)) place();
        } catch (Exception ignored) {
        }
    }

    void close() {
        open = false;
        try {
            if (layer != null && layer.isValid()) {
                try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                    t.setVisibility(layer, false).reparent(layer, null).apply();
                }
            }
        } catch (Throwable ignored) {
        }
        try { if (pkg != null) pkg.release(); } catch (Throwable ignored) {}
        try { if (host != null) host.release(); } catch (Throwable ignored) {}
        pkg = null;
        host = null;
        layer = null;
    }
}
