package org.duofold.live.root;

import android.os.Binder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * One-shot helper started from the root shell as:
 *   CLASSPATH=base.apk app_process /system/bin org.duofold.live.root.ShellOneShot <BinderClass>
 * It drops from root to the shell identity (uid/gid 2000, the identity Shizuku's ADB mode gave the
 * wallpaper helper), runs ONE transaction against a local instance of the helper class and prints
 * the marshalled reply. No Binder hand-off to the app is needed because the data are plain
 * strings/ints, which is the case for WallpaperSetupService.
 *
 * stdin : "<transactionCode> <base64 marshalled request Parcel>"
 * stdout: "DUO_OK <true|false> <base64 marshalled reply Parcel>"  or  "DUO_ERR <message>"
 */
public final class ShellOneShot {
    private ShellOneShot() {}

    public static void main(String[] args) {
        try {
            android.system.Os.setgid(2000);
            android.system.Os.setuid(2000);
            Looper.prepareMainLooper();
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String line = in.readLine();
            if (line == null) throw new IllegalStateException("No request on stdin");
            String[] parts = line.trim().split(" ", 2);
            int code = Integer.parseInt(parts[0]);
            byte[] raw = Base64.decode(parts.length > 1 ? parts[1] : "", Base64.NO_WRAP);
            Binder service = (Binder) Class.forName(args[0]).getConstructor().newInstance();
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            data.unmarshall(raw, 0, raw.length);
            data.setDataPosition(0);
            boolean handled = service.transact(code, data, reply, 0);
            reply.setDataPosition(0);
            byte[] out = reply.marshall();
            System.out.println("DUO_OK " + handled + " " + Base64.encodeToString(out, Base64.NO_WRAP));
        } catch (Throwable t) {
            Throwable c = t;
            while (c.getCause() != null && c.getCause() != c) c = c.getCause();
            System.out.println("DUO_ERR " + c.getClass().getSimpleName() + ": " + c.getMessage());
        }
        System.out.flush();
        System.exit(0);
    }
}
