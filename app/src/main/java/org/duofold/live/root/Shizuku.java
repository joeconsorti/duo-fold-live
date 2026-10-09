package org.duofold.live.root;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Root-backed stand-in for the small slice of the Shizuku client API that Duo Fold Live uses
 * (same class name and method signatures, so the rest of the code only needed its import changed).
 * Nothing here talks to Shizuku. "Binder alive" means "root has been granted", and a "user service"
 * is a libsu RootService that hosts the very same Binder class Shizuku used to host.
 */
public final class Shizuku {
    private Shizuku() {}

    public interface OnBinderReceivedListener { void onBinderReceived(); }
    public interface OnBinderDeadListener { void onBinderDead(); }
    public interface OnRequestPermissionResultListener { void onRequestPermissionResult(int requestCode, int grantResult); }

    public static final class UserServiceArgs {
        final ComponentName component;
        boolean daemon;
        public UserServiceArgs(ComponentName component) { this.component = component; }
        public UserServiceArgs daemon(boolean value) { daemon = value; return this; }
        public UserServiceArgs processNameSuffix(String suffix) { return this; }
        public UserServiceArgs version(int version) { return this; }
    }

    private static final String ANGLE = "org.duofold.live.AngleReader";
    private static final String HOST = "org.duofold.live.wallpaperlayer.HostLauncher";
    private static final String SETTINGS = "org.duofold.live.FoldSettingsService";
    private static final String DISPLAY = "org.duofold.live.DisplaySettingsService";
    private static final String WALLPAPER_SETUP = "org.duofold.live.WallpaperSetupService";
    private static final String CAMERA = "org.duofold.live.CameraContinuityService";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Binder ALIVE = new Binder();
    private static final List<OnBinderReceivedListener> RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<OnBinderDeadListener> DEAD = new CopyOnWriteArrayList<>();
    private static final List<OnRequestPermissionResultListener> RESULTS = new CopyOnWriteArrayList<>();
    private static final java.util.Map<String, Persist> PERSIST = new java.util.HashMap<>();

    private static volatile Context app;
    private static volatile int state; // 0 unknown, 1 root granted, 2 denied

    /** Call once from Application.onCreate. Probes silently only if root was granted before. */
    public static void init(Context context) {
        if (app != null) return;
        app = context.getApplicationContext();
        try { Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(20)); } catch (Throwable ignored) {}
        if (prefs().getBoolean("granted", false)) probe(0, false);
    }

    private static SharedPreferences prefs() { return app.getSharedPreferences("root_access", Context.MODE_PRIVATE); }

    private static void probe(int requestCode, boolean notify) {
        new Thread(() -> {
            boolean ok;
            try { ok = Shell.getShell().isRoot(); } catch (Throwable t) { ok = false; }
            if (!ok) { try { Shell.getShell().close(); } catch (Throwable ignored) {} } // allow a later retry
            final boolean granted = ok;
            state = granted ? 1 : 2;
            prefs().edit().putBoolean("granted", granted).apply();
            MAIN.post(() -> {
                if (notify) for (OnRequestPermissionResultListener l : RESULTS) l.onRequestPermissionResult(requestCode, granted ? 0 : -1);
                if (granted) {
                    for (OnBinderReceivedListener l : RECEIVED) l.onBinderReceived();
                    try { RootGrants.applyOnce(app, org.duofold.live.BuildConfig.VERSION_CODE); } catch (Throwable ignored) {}
                }
            });
        }, "duo-root-probe").start();
    }

    // ---- Shizuku-shaped status API -------------------------------------------------------------

    public static boolean pingBinder() { return state == 1; }
    public static boolean isPreV11() { return false; }
    public static int getVersion() { return 13; }
    /** Root uid. The wallpaper helper drops to shell (2000) itself; see ShellOneShot. */
    public static int getUid() { return state == 1 ? 0 : -1; }
    public static int checkSelfPermission() { return state == 1 ? 0 : -1; }
    public static boolean shouldShowRequestPermissionRationale() { return state == 2; }
    public static IBinder getBinder() { return state == 1 ? ALIVE : null; }
    public static void requestPermission(int requestCode) { probe(requestCode, true); }

    public static void addBinderReceivedListenerSticky(OnBinderReceivedListener l) {
        RECEIVED.add(l);
        if (state == 1) MAIN.post(l::onBinderReceived);
    }
    public static void removeBinderReceivedListener(OnBinderReceivedListener l) { RECEIVED.remove(l); }
    public static void addBinderDeadListener(OnBinderDeadListener l) { DEAD.add(l); }
    public static void removeBinderDeadListener(OnBinderDeadListener l) { DEAD.remove(l); }
    public static void addRequestPermissionResultListener(OnRequestPermissionResultListener l) { RESULTS.add(l); }
    public static void removeRequestPermissionResultListener(OnRequestPermissionResultListener l) { RESULTS.remove(l); }

    // ---- "User services" ------------------------------------------------------------------------

    /**
     * Helpers that are cheap to keep (settings writes, display inspection, the wallpaper host) stay bound,
     * so the shared root process stays warm and the frequent short requests do not respawn su every time.
     * AngleReader is released on unbind, exactly as Shizuku's remove=true did, because unbinding is what
     * stops its logcat reader and releases its rotation hold.
     */
    private static final class Persist implements ServiceConnection {
        final Intent intent;
        final ComponentName name;
        IBinder binder;
        boolean binding;
        final List<ServiceConnection> pending = new ArrayList<>(), active = new ArrayList<>();
        Persist(Intent intent, ComponentName name) { this.intent = intent; this.name = name; }
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            binder = b; binding = false;
            List<ServiceConnection> now = new ArrayList<>(pending); pending.clear(); active.addAll(now);
            for (ServiceConnection c : now) c.onServiceConnected(name, b);
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            binder = null; binding = false;
            List<ServiceConnection> gone = new ArrayList<>(active); active.clear();
            try { RootService.unbind(this); } catch (Throwable ignored) {}
            for (ServiceConnection c : gone) c.onServiceDisconnected(name);
        }
    }

    private static Class<? extends RootService> serviceFor(String cls) {
        switch (cls) {
            case ANGLE: return AngleReaderRootService.class;
            case HOST: return HostLauncherRootService.class;
            case SETTINGS: return FoldSettingsRootService.class;
            case DISPLAY: return DisplaySettingsRootService.class;
            case CAMERA: return CameraContinuityRootService.class;
            default: throw new IllegalArgumentException("No root service for " + cls);
        }
    }

    public static void bindUserService(UserServiceArgs args, ServiceConnection connection) {
        if (app == null) throw new IllegalStateException("Root layer not initialised");
        Runnable job = () -> {
            String cls = args.component.getClassName();
            if (WALLPAPER_SETUP.equals(cls)) { // one-shot, shell identity, no root service needed
                connection.onServiceConnected(args.component, new WallpaperSetupProxy(app, cls));
                return;
            }
            Intent intent = new Intent(app, serviceFor(cls));
            if (args.daemon) intent.addCategory(RootService.CATEGORY_DAEMON_MODE);
            if (SETTINGS.equals(cls) || DISPLAY.equals(cls) || HOST.equals(cls)) {
                Persist p = PERSIST.get(cls);
                if (p == null) { p = new Persist(intent, args.component); PERSIST.put(cls, p); }
                if (p.binder != null && p.binder.isBinderAlive()) {
                    p.active.add(connection);
                    connection.onServiceConnected(args.component, p.binder);
                } else {
                    p.pending.add(connection);
                    if (!p.binding) { p.binding = true; RootService.bind(p.intent, p); }
                }
            } else {
                RootService.bind(intent, connection);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) job.run(); else MAIN.post(job);
    }

    public static void unbindUserService(UserServiceArgs args, ServiceConnection connection, boolean remove) {
        Runnable job = () -> {
            String cls = args.component.getClassName();
            if (WALLPAPER_SETUP.equals(cls)) return;
            Persist p = PERSIST.get(cls);
            if (p != null) { p.pending.remove(connection); p.active.remove(connection); return; }
            try { RootService.unbind(connection); } catch (Throwable ignored) {}
        };
        if (Looper.myLooper() == Looper.getMainLooper()) job.run(); else MAIN.post(job);
    }
}
