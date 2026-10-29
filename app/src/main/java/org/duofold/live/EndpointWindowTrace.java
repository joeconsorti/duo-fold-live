package org.duofold.live;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/* loaded from: classes4.dex */
final class EndpointWindowTrace {
    private static final AtomicBoolean dumping = new AtomicBoolean();
    private volatile int generation;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() { // from class: org.duofold.live.EndpointWindowTrace$$ExternalSyntheticLambda3
        @Override // java.util.concurrent.ThreadFactory
        public final Thread newThread(Runnable runnable) {
            return EndpointWindowTrace.lambda$new$0(runnable);
        }
    });
    private final ArrayDeque<String> events = new ArrayDeque<>();

    EndpointWindowTrace() {
    }

    static /* synthetic */ Thread lambda$new$0(Runnable r) {
        Thread t = new Thread(r, "Duo-endpoint-window");
        t.setDaemon(true);
        return t;
    }

    private synchronized void log(String s) {
        this.events.addLast(SystemClock.elapsedRealtime() + ": " + s);
        while (this.events.size() > 24) {
            this.events.removeFirst();
        }
    }

    synchronized String report() {
        return "App window after remap (sampled readiness, not first-frame/optical timing):\n" + String.join("\n", this.events);
    }

    void start(final int task, final long remap, final long endpoint) {
        final int epoch = this.generation + 1;
        this.generation = epoch;
        if (task < 0) {
            log("No exact fullscreen task selected; readiness not measured");
        } else {
            this.worker.execute(new Runnable() { // from class: org.duofold.live.EndpointWindowTrace$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    EndpointWindowTrace.this.lambda$start$1(epoch, remap, task, endpoint);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$start$1(int epoch, long remap, int task, long endpoint) {
        String state;
        long deadline;
        if (epoch != this.generation) {
            return;
        }
        log("WATCH task=" + task + "; remap observed=" + remap + "; endpoint=" + endpoint);
        long lastEnd = remap;
        String previous = "";
        for (long deadline2 = 2000 + remap; epoch == this.generation && SystemClock.elapsedRealtime() < deadline2; deadline2 = deadline) {
            long began = SystemClock.elapsedRealtime();
            try {
                state = EndpointWindowState.summarize(dump(), task);
            } catch (Exception e) {
                state = "UNAVAILABLE: " + e.getClass().getSimpleName();
            }
            long ended = SystemClock.elapsedRealtime();
            if (epoch != this.generation) {
                return;
            }
            if (state.equals(previous)) {
                deadline = deadline2;
            } else {
                deadline = deadline2;
                log("task=" + task + " " + state + "; dump interval=" + began + ".." + ended + "; previous sample ended=" + lastEnd);
                previous = state;
            }
            if (state.startsWith("READY")) {
                log("First READY observation finished +" + (ended - remap) + " ms after observed remap; endpoint=" + endpoint + (endpoint > 0 ? "; READY dump finished +" + (ended - endpoint) + " ms after endpoint" : "; endpoint not yet observed") + "; actual readiness may precede sample");
                return;
            }
            lastEnd = ended;
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (epoch == this.generation) {
            log("Window watch ended without READY; task=" + task);
        }
    }

    static String dump() throws Exception {
        final IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
        if(service==null)throw new IOException("window service unavailable");
        if(!dumping.compareAndSet(false,true))throw new IOException("Previous window dump still active");
        final ParcelFileDescriptor[] pipe;
        try{pipe=ParcelFileDescriptor.createPipe();}catch(IOException e){dumping.set(false);throw e;}
        final StringBuilder out=new StringBuilder();
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread reader=new Thread(()->{
            try(java.io.BufferedReader in=new java.io.BufferedReader(new java.io.InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]),java.nio.charset.StandardCharsets.UTF_8))){
                String line;while((line=in.readLine())!=null){if(out.length()+line.length()>1000000)throw new IOException("window dump truncated");out.append(line).append('\n');}
            }catch(Throwable e){failure.compareAndSet(null,e);}
        },"Duo-window-dump-reader");
        Thread writer=new Thread(()->{
            try{service.dump(pipe[1].getFileDescriptor(),new String[]{"windows"});}catch(Throwable e){failure.compareAndSet(null,e);}
            finally{try{pipe[1].close();}catch(IOException ignored){}dumping.set(false);}
        },"Duo-window-dump-writer");
        reader.setDaemon(true);writer.setDaemon(true);reader.start();writer.start();
        try{reader.join(500);if(reader.isAlive())throw new IOException("window dump timed out");if(failure.get()!=null)throw new IOException("window dump failed",failure.get());return out.toString();}
        finally{pipe[0].close();}
    }
}
