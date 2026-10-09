package org.duofold.live.root;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import com.topjohnwu.superuser.ipc.RootService;

/** Runs one of Duo's existing Binder helper classes inside libsu's root process. */
public abstract class BaseRootService extends RootService {
    private Binder binder;

    protected abstract Binder create() throws Exception;

    @Override public void onCreate() {
        super.onCreate();
        PrivRuntime.shared = true;
        // Normally unnecessary in an app_process root process; harmless if it is.
        try { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L"); } catch (Throwable ignored) {}
    }

    @Override public IBinder onBind(Intent intent) {
        if (binder == null) {
            try { binder = create(); }
            catch (Exception e) { throw new IllegalStateException("Could not start " + getClass().getSimpleName() + ": " + e, e); }
        }
        return binder;
    }

    /** 16777115 is the "destroy" transaction every helper already handles (formerly sent by Shizuku). */
    @Override public void onDestroy() {
        Binder b = binder;
        binder = null;
        if (b != null) {
            Parcel in = Parcel.obtain(), out = Parcel.obtain();
            try { b.transact(16777115, in, out, 0); } catch (Throwable ignored) {} finally { in.recycle(); out.recycle(); }
        }
        super.onDestroy();
    }
}
