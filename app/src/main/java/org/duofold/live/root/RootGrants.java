package org.duofold.live.root;

import android.content.Context;
import android.content.SharedPreferences;
import com.topjohnwu.superuser.Shell;

/**
 * One-time system setup done with root, so the manual Android permission steps disappear.
 *
 * Phase 2: Duo no longer uses an accessibility service or the overlay permission for its animation, and the
 * manifest no longer declares either. This class therefore also REMOVES the accessibility entry that the
 * phase-1 build added to Settings.Secure. The only appop still granted is SYSTEM_ALERT_WINDOW, for the two
 * 1x1 "angle anchor" windows in LiveAngles (the manifest declares the permission again because the app-op alone
 * was not honoured); root sets the app-op, so no manual step is needed.
 */
final class RootGrants {
    private RootGrants() {}

    static void applyOnce(Context app, int versionCode) {
        SharedPreferences p = app.getSharedPreferences("root_access", Context.MODE_PRIVATE);
        if (p.getInt("grants3_applied_vc", 0) == versionCode || !p.getBoolean("auto_grants", true)) return;
        String pkg = app.getPackageName();
        String svc = pkg + "/org.duofold.live.StandaloneService";
        String removeA11y =
            "cur=\"$(settings get secure enabled_accessibility_services)\"; " +
            "case \"$cur\" in *" + svc + "*) " +
            "new=\"$(echo \"$cur\" | sed -e 's|" + svc + "||' -e 's|::*|:|g' -e 's|^:||' -e 's|:$||')\"; " +
            "if [ -z \"$new\" ]; then settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0; " +
            "else settings put secure enabled_accessibility_services \"$new\"; fi ;; esac";
        Shell.cmd(
            "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow",
            "pm grant " + pkg + " android.permission.POST_NOTIFICATIONS",
            "cmd deviceidle whitelist +" + pkg,
            removeA11y
        ).submit(r -> p.edit().putInt("grants3_applied_vc", versionCode).apply());
    }
}
