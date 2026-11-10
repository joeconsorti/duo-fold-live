package org.duofold.live;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Explicitly armed diagnostics only. One off-path bounded dump; no captures, settings or wake. */
final class RotationDiagnosticProbe implements AutoCloseable {
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"duo-rotation-observer");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
 private final AtomicBoolean busy=new AtomicBoolean();
 private volatile int epoch;private volatile Process process;
 private volatile String report="";
 String report(){return report;}
 void cancel(){epoch++;Process p=process;if(p!=null)p.destroyForcibly();}
 void request(String phase){
  if(!busy.compareAndSet(false,true))return;
  final int generation=epoch;final long requested=SystemClock.elapsedRealtime();
  worker.execute(()->{
   File file=null;String result;
   try{
    if(generation!=epoch)return;
    file=File.createTempFile("duo-rotation-observer-",".tmp",new File("/data/local/tmp"));android.system.Os.chmod(file.getPath(),0600);
    Process p=new ProcessBuilder("dumpsys","-t","1","window","displays").redirectErrorStream(true).redirectOutput(file).start();process=p;
    if(generation!=epoch){p.destroyForcibly();return;}
    if(!p.waitFor(1500,TimeUnit.MILLISECONDS))throw new IOException("window policy observation timed out");
    if(p.exitValue()!=0||file.length()>524288)throw new IOException("window policy observation unavailable or oversized");
    result=RotationDumpEvidence.filter(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
   }catch(Exception failure){result="UNAVAILABLE "+failure.getClass().getSimpleName();}
   finally{Process p=process;if(p!=null)p.destroyForcibly();process=null;if(file!=null)file.delete();busy.set(false);}
   if(generation==epoch)report=requested+" ROTATION_POLICY_PROBE "+phase+" completed="+SystemClock.elapsedRealtime()+" "+result+"; asynchronous software snapshot, no sensor-response or optical proof";
  });
 }
 public void close(){cancel();worker.shutdownNow();}
}
