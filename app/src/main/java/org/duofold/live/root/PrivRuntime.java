package org.duofold.live.root;

/**
 * Privileged helper classes (AngleReader, HostLauncher, ...) used to own a whole Shizuku process and
 * called System.exit(0) to end it. Under libsu several of them share ONE root process, so an exit()
 * from one helper would kill all the others. In shared mode exit becomes a no-op; the root service
 * wrapper's onDestroy() performs the cleanup instead. Outside shared mode (the one-shot shell helper)
 * it behaves exactly as before.
 */
public final class PrivRuntime {
    private PrivRuntime() {}
    static volatile boolean shared;
    public static void exit(int code) { if (!shared) System.exit(code); }
}
