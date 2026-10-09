package org.duofold.live.root;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.util.Base64;
import com.topjohnwu.superuser.Shell;
import java.util.ArrayList;
import java.util.List;

/**
 * App-side stand-in for the Shizuku user service of WallpaperSetupService. Every transaction is
 * forwarded to a short-lived shell-identity process (see ShellOneShot). Must be called from a
 * background thread (SetupActivity already does that).
 */
final class WallpaperSetupProxy extends Binder {
    private final String apk;
    private final String target;

    WallpaperSetupProxy(Context app, String targetClass) {
        this.apk = app.getPackageCodePath();
        this.target = targetClass;
    }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(target); return true; }
        if (code == 16777115) return true; // nothing persistent to destroy
        try {
            String b64 = Base64.encodeToString(data.marshall(), Base64.NO_WRAP);
            String cmd = "echo '" + code + " " + b64 + "' | CLASSPATH='" + apk
                + "' app_process /system/bin --nice-name=duofold_shell_helper org.duofold.live.root.ShellOneShot " + target;
            List<String> out = new ArrayList<>(), err = new ArrayList<>();
            Shell.cmd(cmd).to(out, err).exec();
            for (String line : out) {
                if (line.startsWith("DUO_OK ")) {
                    String[] p = line.split(" ", 3);
                    byte[] bytes = Base64.decode(p.length > 2 ? p[2] : "", Base64.NO_WRAP);
                    Parcel t = Parcel.obtain();
                    try {
                        t.unmarshall(bytes, 0, bytes.length);
                        t.setDataPosition(0);
                        reply.appendFrom(t, 0, t.dataSize());
                    } finally { t.recycle(); }
                    return Boolean.parseBoolean(p[1]);
                }
                if (line.startsWith("DUO_ERR ")) return fail(reply, line.substring(8));
            }
            return fail(reply, "No answer from shell helper. stdout=" + out + " stderr=" + err);
        } catch (Throwable t) {
            return fail(reply, t.toString());
        }
    }

    private static boolean fail(Parcel reply, String message) {
        reply.writeException(new IllegalStateException("Root shell helper: " + message));
        return true;
    }
}
