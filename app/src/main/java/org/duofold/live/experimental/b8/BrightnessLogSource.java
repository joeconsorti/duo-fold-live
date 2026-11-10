package org.duofold.live.experimental.b8;

import android.os.SystemClock;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One short-lived command stream per preparation. Receiving a hardware command
 * must not depend on logcat exiting. Historical and incomplete records cannot
 * authorize a write; post-clear validation remains in LiveB8Brightness. */
final class BrightnessLogSource implements LiveB8Brightness.SampleReader, AutoCloseable {
 static final int TAIL_LINES=1;
 private static final int MAX_LINE=16384, MAX_BYTES=4*1024*1024;
 private static final long POLL_NS=TimeUnit.MILLISECONDS.toNanos(5);
 interface Launcher {Process start(List<String> command)throws IOException;}
 private final Launcher launcher;
 private final Object lock=new Object();
 private final Map<Long,BrightnessSamples.Sample> latest=new HashMap<>();
 private Process active;
 private boolean closed,partial;
 private IOException failure;
 private long startedUptime;
 private final byte[] batch=new byte[2048];
 private final StringBuilder line=new StringBuilder();
 private int bytes;
 private long revision,returnedRevision=-1;

 BrightnessLogSource(File dir){this(dir,command->new ProcessBuilder(command).redirectErrorStream(true).start());}
 BrightnessLogSource(File dir,Launcher launcher){this.launcher=launcher;}
 private void start()throws IOException {
  if(active!=null)return;
  startedUptime=SystemClock.uptimeMillis();
  // -T follows new records; -t/-d waited for a dump to exit on every retry.
  // Ignore even this one historical record unless it belongs to this preparation.
  active=launcher.start(Arrays.asList("logcat","-v","monotonic","-T",
    Integer.toString(TAIL_LINES),"SurfaceFlinger:I","*:S"));
  final Process child=active;
  Thread reader=new Thread(()->consume(child),"duo-brightness-commands");reader.setDaemon(true);reader.start();
  Thread expiry=new Thread(()->{
   try{if(!child.waitFor(6,TimeUnit.SECONDS)){
    synchronized(lock){if(!closed){failure=new IOException("Brightness stream lifetime exceeded");lock.notifyAll();}}
    child.destroyForcibly();
   }}catch(InterruptedException e){Thread.currentThread().interrupt();}
  },"duo-brightness-command-expiry");expiry.setDaemon(true);expiry.start();
 }
 // Caller holds lock. Drain complete available batches before publishing any
 // cached sample, so an already-received OFF record cannot sit behind a positive.
 private boolean drain(Process child)throws IOException {
  if(!child.isAlive())throw new IOException("Brightness command stream ended before preparation completed");
  InputStream input=child.getInputStream();int available;int budget=65536;
  while(budget>0&&(available=input.available())>0){
   int count=input.read(batch,0,Math.min(batch.length,available));
   if(count<0)throw new EOFException("Brightness command stream closed");
   budget-=count;bytes+=count;if(bytes>MAX_BYTES)throw new IOException("Brightness stream byte bound exceeded");
   for(int i=0;i<count;i++){
    char ch=(char)(batch[i]&255);
    if(ch=='\n'){
     for(BrightnessSamples.Sample sample:BrightnessSamples.parse(line.toString()).values()){
      if(sample.ms<startedUptime)continue;
      BrightnessSamples.Sample previous=latest.get(sample.id);
      if(previous==null&&latest.size()>=8)throw new IOException("Unexpected physical-panel count");
      // Keep OFF/invalid commands too: they veto an older positive value.
      if(previous==null||sample.ms>=previous.ms){latest.put(sample.id,sample);revision++;}
     }
     line.setLength(0);
    }else{line.append(ch);if(line.length()>MAX_LINE)throw new IOException("Brightness record too long");}
   }
  }
  partial=line.length()>0;
  return !partial&&input.available()==0;
 }
 private void consume(Process child){
  try{
   while(true){
    synchronized(lock){if(closed)return;drain(child);lock.notifyAll();}
    Thread.sleep(5);
   }
  }catch(Exception e){
   synchronized(lock){if(!closed){failure=e instanceof IOException?(IOException)e:new IOException(e);lock.notifyAll();}}
   child.destroyForcibly();
  }finally{try{child.getInputStream().close();}catch(IOException ignored){}}
 }
 public Map<Long,BrightnessSamples.Sample> read(String phase,long timeoutMs)throws Exception {
  if(timeoutMs<=0)throw new IOException("Brightness observation deadline expired");
  long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.min(timeoutMs,phase.equals("prepare")?150:400));
  synchronized(lock){
   if(closed)throw new IOException("Brightness source closed");
   start();
   while(true){
    if(closed)throw new IOException("Brightness observation canceled");
    if(failure!=null)throw failure;
    long now=System.nanoTime();
    boolean drained=drain(active);
    if(drained&&((!latest.isEmpty()&&revision!=returnedRevision)||now>=deadline)){returnedRevision=revision;return new HashMap<>(latest);}
    if(now>=deadline)throw new IOException("Brightness stream has incomplete or undrained commands");
    TimeUnit.NANOSECONDS.timedWait(lock,Math.min(POLL_NS,deadline-now));
   }
  }
 }
 public void close(){
  Process child;
  synchronized(lock){closed=true;child=active;lock.notifyAll();}
  if(child!=null)child.destroyForcibly();
 }
}
