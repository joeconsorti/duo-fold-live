package org.duofold.live.wallpaperlayer;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import org.duofold.live.BuiltInPanel;
import org.duofold.live.wallpaperlayer.LayerHost;
import org.duofold.live.wallpaperlayer.PanelPhotos;

/* loaded from: classes3.dex */
public class LayerHost extends Binder {
    public static final String TOKEN = "org.duofold.live.wallpaperlayer.Host";
    PanelPhotos album;
    Bitmap bitmap;
    Context context;
    volatile String details;
    final DisplayManager.DisplayListener displayListener;
    DisplayManager displayManager;
    volatile String failureTrace;
    volatile HomePhotoLayer homePhoto;
    final ProbeJournal journal;
    boolean listening;
    final Handler main;
    NativePhotoLayer nativePhoto;
    String nativeStatus;
    volatile String operation;
    int owner;
    final HashMap<Integer, Panel> panels;
    int retainedChanges;
    volatile boolean running;
    volatile String state;
    final Runnable tick;
    Consumer<String> transitionSink;
    int type;
    volatile long until;
    boolean useNative;
    int windowAdds;

    void displayChanged() {
        if (this.running) {
            try {
                refresh();
            } catch (Throwable e) {
                recordFailure(e);
                stop("Display update failed: " + reason(e));
            }
        }
    }

    void trace(String event) {
        WakePreparationTest.record(event);
        this.journal.phase(event);
        if (this.transitionSink != null) {
            this.transitionSink.accept(event);
        }
    }

    public LayerHost() {
        this.main = new Handler(Looper.getMainLooper());
        this.panels = new HashMap<>();
        this.state = "Idle";
        this.details = "";
        this.operation = "Idle";
        this.failureTrace = "";
        this.journal = new ProbeJournal();
        this.nativeStatus = "Legacy window renderer";
        this.displayListener = new DisplayManager.DisplayListener() { // from class: org.duofold.live.wallpaperlayer.LayerHost.1
            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayAdded(int id) {
                LayerHost.this.displayChanged();
            }

            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayChanged(int id) {
                LayerHost.this.displayChanged();
            }

            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayRemoved(int id) {
                LayerHost.this.displayChanged();
            }
        };
        this.owner = -1;
        this.tick = new Runnable() { // from class: org.duofold.live.wallpaperlayer.LayerHost.2
            @Override // java.lang.Runnable
            public void run() {
                if (LayerHost.this.running) {
                    try {
                        LayerHost.this.refresh();
                        LayerHost.this.main.postDelayed(this, 500L);
                    } catch (Throwable e) {
                        LayerHost.this.recordFailure(e);
                        LayerHost.this.stop("Stopped after error: " + LayerHost.reason(e));
                    }
                }
            }
        };
        attachInterface(null, TOKEN);
    }

    public LayerHost(Context registeredContext) {
        this();
        WallpaperProbeSurfaces.host=this;
        this.context = registeredContext;
    }

    @Override // android.os.Binder
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == 1598968902) {
            reply.writeString(TOKEN);
            return true;
        }
        if (code == 16777115) {
            this.main.post(new Runnable() { // from class: org.duofold.live.wallpaperlayer.LayerHost$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    LayerHost.this.lambda$onTransact$0();
                }
            });
            return true;
        }
        data.enforceInterface(TOKEN);
        synchronized (this) {
            int uid = Binder.getCallingUid();
            if (this.owner < 0) {
                this.owner = uid;
            }
            if (this.owner != uid) {
                throw new SecurityException("Wrong caller");
            }
        }
        if (code == 1) {
            final ParcelFileDescriptor fd = (ParcelFileDescriptor) data.readTypedObject(ParcelFileDescriptor.CREATOR);
            if (fd == null) {
                throw new IllegalArgumentException("Photo missing");
            }
            this.main.post(new Runnable() { // from class: org.duofold.live.wallpaperlayer.LayerHost$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    LayerHost.this.start(fd);
                }
            });
            reply.writeNoException();
            reply.writeString("Starting photo layer. Press Home after the status updates.");
            return true;
        }
        if (code == 2) {
            reply.writeNoException();
            reply.writeString(report());
            return true;
        }
        if (code != 3) {
            return super.onTransact(code, data, reply, flags);
        }
        this.main.post(new Runnable() { // from class: org.duofold.live.wallpaperlayer.LayerHost$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                LayerHost.this.lambda$onTransact$2();
            }
        });
        reply.writeNoException();
        reply.writeString("Removing layer…");
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onTransact$0() {
        stop("Host ended");
        System.exit(0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onTransact$2() {
        stop("Stopped; original wallpaper unchanged");
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: start, reason: merged with bridge method [inline-methods] */
    public void start(ParcelFileDescriptor fd) {
        stop("Previous layer cleaned up");
        this.journal.begin();
        this.failureTrace = "";
        this.operation = "Startup";
        try {
            try {
                this.journal.phase("Check registered app context");
                if (this.context == null) {
                    throw new IllegalStateException("Registered instrumentation context required");
                }
                this.operation = "Registered window host";
                this.journal.phase("Check INTERNAL_SYSTEM_WINDOW permission");
                if (this.context.checkSelfPermission("android.permission.INTERNAL_SYSTEM_WINDOW") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Shell lacks INTERNAL_SYSTEM_WINDOW");
                }
                this.journal.phase("Resolve Samsung window type");
                this.type = WindowManager.LayoutParams.class.getField("TYPE_COVER_SCREEN_BASE").getInt(null);
                if (this.type != 2620) {
                    throw new IllegalStateException("Samsung wallpaper-depth type differs: " + this.type);
                }
                this.journal.phase("Decode selected photo");
                this.bitmap = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
                if (this.bitmap == null) {
                    throw new IOException("Cannot decode photo");
                }
                this.until = 0L;
                this.running = true;
                this.details = "";
                this.state = "Custom wallpaper running — no time limit";
                this.journal.phase("Start retained wallpaper windows");
                this.windowAdds = 0;
                this.retainedChanges = 0;
                this.displayManager = (DisplayManager) this.context.getSystemService(Context.DISPLAY_SERVICE);
                this.displayManager.registerDisplayListener(this.displayListener, this.main);
                this.listening = true;
                refresh();
                this.main.post(this.tick);
                fd.close();
            } catch (Throwable e) {
                try {
                    recordFailure(e);
                    stop("Unavailable: " + reason(e));
                    fd.close();
                } catch (Throwable th) {
                    try {
                        fd.close();
                    } catch (IOException e2) {
                    }
                    throw th;
                }
            }
        } catch (IOException e3) {
        }
    }

    void activateNative() {
        NativePhotoLayer candidate = null;
        try {
            candidate = new NativePhotoLayer(this.context, this.main, new Consumer() { // from class: org.duofold.live.wallpaperlayer.LayerHost$$ExternalSyntheticLambda3
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    LayerHost.this.lambda$activateNative$3((String) obj);
                }
            }, new Consumer() { // from class: org.duofold.live.wallpaperlayer.LayerHost$$ExternalSyntheticLambda4
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    LayerHost.this.lambda$activateNative$4((Throwable) obj);
                }
            });
            candidate.album = this.album;
            candidate.start(this.bitmap);
            this.nativePhoto = candidate;
            this.nativeStatus = "Native compositor + retained wallpaper windows (below apps)";
            trace("Native photo active; retaining wallpaper windows through sleep/unlock");
        } catch (Throwable e) {
            if (candidate != null) {
                candidate.close();
            }
            recordFailure(e);
            this.nativeStatus = "Legacy fallback: " + reason(e);
            this.journal.phase(this.nativeStatus);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$activateNative$3(String s) {
        this.journal.phase(s);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: fallbackNative, reason: merged with bridge method [inline-methods] */
    public void lambda$activateNative$4(Throwable error) {
        recordFailure(error);
        if (this.nativePhoto != null) {
            this.nativePhoto.close();
            this.nativePhoto = null;
        }
        this.nativeStatus = "Legacy fallback: " + reason(error);
        this.journal.phase(this.nativeStatus);
        displayChanged();
    }

    void screenEvent(String action) {
        if (this.running) {
            KeyguardManager keyguard = (KeyguardManager) this.context.getSystemService(KeyguardManager.class);
            trace("Screen event " + action + "; keyguardLocked=" + keyguard.isKeyguardLocked() + "; photo windows=" + this.panels.size() + "; native photo=" + (this.nativePhoto != null) + "; Home bridge=" + (this.homePhoto != null) + "; pre-unlock test=" + WakePreparationTest.enabled(this.context));
            if (this.homePhoto != null) {
                this.homePhoto.screenEvent(action);
            }
            if ("android.intent.action.SCREEN_ON".equals(action) || "android.intent.action.USER_PRESENT".equals(action)) {
                if (this.nativePhoto != null) {
                    this.nativePhoto.invalidateGeometry();
                }
                if (this.homePhoto != null) {
                    this.homePhoto.invalidateGeometry();
                }
                displayChanged();
                for (Panel panel : this.panels.values()) {
                    panel.view.setTag(action + " at " + SystemClock.elapsedRealtime());
                    panel.view.requestLayout();
                    panel.view.postInvalidateOnAnimation();
                }
            }
        }
    }

    void updateAlbum(PanelPhotos next) {
        PanelPhotos old = this.album;
        this.album = next;
        if (this.nativePhoto != null) {
            this.nativePhoto.album = next;
            this.nativePhoto.refreshDisplays();
        }
        if (this.homePhoto != null) {
            this.homePhoto.album = next;
            this.homePhoto.refreshGeometry();
        }
        for (Panel panel : this.panels.values()) {
            panel.view.invalidate();
        }
        if (old != null) {
            old.close();
        }
    }

    void updatePhoto(Bitmap photo) {
        this.bitmap = photo;
        if (this.homePhoto != null) {
            this.homePhoto.setPhoto(photo);
        }
        if (this.nativePhoto != null) {
            try {
                this.nativePhoto.setPhoto(photo);
            } catch (Throwable e) {
                lambda$activateNative$4(e);
            }
        }
        for (Panel panel : this.panels.values()) {
            panel.view.invalidate();
        }
    }

    void recordFailure(Throwable e) {
        this.journal.fail(this.operation + ": " + reason(e));
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        this.failureTrace = w.toString();
    }

    static String reason(Throwable e) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (e != null && i < 8) {
            if (i > 0) {
                b.append(" <- ");
            }
            b.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            i++;
            e = e.getCause();
        }
        return b.toString();
    }

    void refresh() {
        DisplayManager dm;
        Display[] displayArr;
        int i;
        if (this.homePhoto != null) {
            this.homePhoto.refreshGeometry();
        }
        if (this.nativePhoto != null) {
            this.nativePhoto.refreshDisplays();
        }
        this.operation = "Enumerate displays";
        DisplayManager dm2 = (DisplayManager) this.context.getSystemService(Context.DISPLAY_SERVICE);
        Set<Integer> keep = new HashSet<>();
        Display[] displays = dm2.getDisplays();
        int length = displays.length;
        int i2 = 0;
        while (i2 < length) {
            Display d = displays[i2];
            if (d.getDisplayId() != 0 && d.getDisplayId() != 1) {
                dm = dm2;
                displayArr = displays;
                i = length;
            } else if (d.isValid()) {
                Display.Mode mode = d.getMode();
                Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight());
                Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight());
                if (BuiltInPanel.accepts(d)) {
                    int id = d.getDisplayId();
                    keep.add(Integer.valueOf(id));
                    String key = mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + "/" + d.getRotation();
                    Panel old = this.panels.get(Integer.valueOf(id));
                    if (old != null) {
                        if (old.key.equals(key)) {
                            dm = dm2;
                        } else {
                            old.key = key;
                            this.retainedChanges++;
                            old.view.requestLayout();
                            old.view.invalidate();
                            dm = dm2;
                            trace("Retained photo window on display " + id + " (" + key + ")");
                        }
                        displayArr = displays;
                        i = length;
                    } else {
                        dm = dm2;
                        this.operation = "Create photo window context on display " + id;
                        displayArr = displays;
                        this.journal.phase("Create photo window context on display " + id + " (" + key + ")");
                        Context wc = this.context.createDisplayContext(d).createWindowContext(this.type, null);
                        WindowManager wm = (WindowManager) wc.getSystemService(Context.WINDOW_SERVICE);
                        View view = new AnonymousClass3(wc, id);
                        view.setImportantForAccessibility(4);
                        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, this.type, 18350360, -1);
                        lp.packageName = this.context.getPackageName();
                        lp.setTitle("Duo persistent wallpaper photo");
                        lp.windowAnimations = 0;
                        lp.rotationAnimation = 3;
                        lp.setFitInsetsTypes(0);
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                        this.operation = "Attach photo window on display " + id;
                        i = length;
                        this.journal.phase("Attach photo window on display " + id);
                        wm.addView(view, lp);
                        this.windowAdds++;
                        this.journal.phase("Attached display " + id);
                        this.panels.put(Integer.valueOf(id), new Panel(wm, view, key));
                    }
                } else {
                    dm = dm2;
                    displayArr = displays;
                    i = length;
                }
            } else {
                dm = dm2;
                displayArr = displays;
                i = length;
            }
            i2++;
            dm2 = dm;
            displays = displayArr;
            length = i;
        }
        Iterator it = new ArrayList(this.panels.keySet()).iterator();
        while (it.hasNext()) {
            Integer id2 = (Integer) it.next();
            if (!keep.contains(id2)) {
                this.panels.remove(id2).remove();
            }
        }
        this.details = "Window type " + this.type + "; attached panels " + this.panels.keySet() + ". Attachment does not prove correct visual placement.";
    }

    /* renamed from: org.duofold.live.wallpaperlayer.LayerHost$3, reason: invalid class name */
    class AnonymousClass3 extends View {
        final Paint p;
        boolean traceNextDraw;
        final /* synthetic */ int val$id;

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        AnonymousClass3(Context arg0, int i) {
            super(arg0);
            this.val$id = i;
            this.traceNextDraw = true;
            this.p = new Paint(3);
        }

        @Override // android.view.View
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            this.traceNextDraw = true;
            LayerHost.this.trace("Display " + this.val$id + " window visibility=" + visibility);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onAttachedToWindow$0(int id, String reason) {
            LayerHost.this.trace("Display " + id + " photo frame committed; " + reason);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onAttachedToWindow$1(final int id, final String reason) {
            LayerHost.this.main.post(new Runnable() { // from class: org.duofold.live.wallpaperlayer.LayerHost$3$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    LayerHost.AnonymousClass3.this.lambda$onAttachedToWindow$0(id, reason);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ boolean lambda$onAttachedToWindow$2(final int id) {
            if (!this.traceNextDraw && getTag() == null) {
                return true;
            }
            final String reason = getTag() == null ? "attach/resize/visibility" : String.valueOf(getTag());
            if (!isHardwareAccelerated()) {
                return true;
            }
            getViewTreeObserver().registerFrameCommitCallback(new Runnable() { // from class: org.duofold.live.wallpaperlayer.LayerHost$3$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    LayerHost.AnonymousClass3.this.lambda$onAttachedToWindow$1(id, reason);
                }
            });
            return true;
        }

        @Override // android.view.View
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            LayerHost.this.trace("Display " + this.val$id + " view attached");
            ViewTreeObserver viewTreeObserver = getViewTreeObserver();
            final int i = this.val$id;
            viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() { // from class: org.duofold.live.wallpaperlayer.LayerHost$3$$ExternalSyntheticLambda0
                @Override // android.view.ViewTreeObserver.OnPreDrawListener
                public final boolean onPreDraw() {
                    boolean lambda$onAttachedToWindow$2;
                    lambda$onAttachedToWindow$2 = LayerHost.AnonymousClass3.this.lambda$onAttachedToWindow$2(i);
                    return lambda$onAttachedToWindow$2;
                }
            });
        }

        @Override // android.view.View
        protected void onDetachedFromWindow() {
            LayerHost.this.trace("Display " + this.val$id + " view detached");
            super.onDetachedFromWindow();
        }

        @Override // android.view.View
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            this.traceNextDraw = true;
            LayerHost.this.trace("Display " + this.val$id + " resize " + oldw + "x" + oldh + " -> " + w + "x" + h);
            invalidate();
        }

        @Override // android.view.View
        protected void onConfigurationChanged(Configuration config) {
            super.onConfigurationChanged(config);
            requestLayout();
            invalidate();
        }

        @Override // android.view.View
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            PanelPhotos.Photo selected = LayerHost.this.album == null ? null : LayerHost.this.album.forDisplay(getDisplay(), getWidth(), getHeight());
            Bitmap b = selected == null ? LayerHost.this.bitmap : selected.bitmap;
            if (b == null || b.isRecycled()) {
                return;
            }
            float scale = Math.max(getWidth() / (float)b.getWidth(), getHeight() / (float)b.getHeight());
            float w = b.getWidth() * scale;
            float h = b.getHeight() * scale;
            if (selected != null) {
                c.drawBitmap(b, selected.crop(getWidth(), getHeight()), new Rect(0, 0, getWidth(), getHeight()), this.p);
            } else {
                c.drawBitmap(b, (Rect) null, new RectF((getWidth() - w) / 2.0f, (getHeight() - h) / 2.0f, (getWidth() + w) / 2.0f, (getHeight() + h) / 2.0f), this.p);
            }
            if (this.traceNextDraw || getTag() != null) {
                String reason = String.valueOf(getTag());
                setTag(null);
                this.traceNextDraw = false;
                LayerHost.this.trace("Display " + this.val$id + " photo draw callback " + getWidth() + "x" + getHeight() + "; " + reason);
            }
        }
    }

    void removePanels() {
        for (Panel p : this.panels.values()) {
            p.remove();
        }
        this.panels.clear();
    }

    void stop(String message) {
        if (this.homePhoto != null) {
            this.homePhoto.close();
            this.homePhoto = null;
        }
        if (this.nativePhoto != null) {
            this.nativePhoto.close();
            this.nativePhoto = null;
        }
        this.journal.stopped(message);
        this.running = false;
        this.main.removeCallbacks(this.tick);
        if (this.listening && this.displayManager != null) {
            this.displayManager.unregisterDisplayListener(this.displayListener);
            this.listening = false;
        }
        removePanels();
        if (this.album != null) {
            this.album.close();
            this.album = null;
        }
        this.bitmap = null;
        this.state = message;
        this.until = 0L;
    }

    String report() {
        SystemClock.elapsedRealtime();
        return "Duo Wallpaper Layer 0.13\n" + Build.MODEL + " / Android " + Build.VERSION.RELEASE + "\n" + this.state + "\n" + this.journal.report() + "\nRenderer: " + (this.nativePhoto != null ? this.nativePhoto.rendererDescription() : this.nativeStatus) + "\nHome transition photo: " + (this.homePhoto == null ? "Unavailable / not started" : this.homePhoto.status + "; " + this.homePhoto.geometryReport()) + "\nCurrent operation: " + this.operation + "\n" + this.failureTrace + "\n" + (this.nativePhoto != null ? "Native surfaces active; retained wallpaper windows=" + this.panels.keySet() : this.details) + "\nDuration: " + (this.running ? "Until disabled or host ends" : "Stopped") + "\nWindow attaches: " + this.windowAdds + "; retained configuration changes: " + this.retainedChanges + "\nRegistered window host UID: " + Process.myUid() + "\nWallpaper diagnostic angle polling: removed; animation reader owns angle acquisition.\nRenderer leaves home wallpaper bindings and physical display states unchanged. Separate lock-photo action is reported by the app.";
    }

    static class Panel {
        String key;
        final View view;
        final WindowManager wm;

        Panel(WindowManager w, View v, String k) {
            this.wm = w;
            this.view = v;
            this.key = k;
        }

        void remove() {
            try {
                this.wm.removeViewImmediate(this.view);
            } catch (Exception e) {
            }
        }
    }
}
