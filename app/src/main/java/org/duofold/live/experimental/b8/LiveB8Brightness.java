package org.duofold.live.experimental.b8;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
/** B8 only: bounded physical brightness reassertion. No settings or global freeze flag. */
final class LiveB8Brightness {
 interface SampleReader {Map<Long,BrightnessSamples.Sample> read(String phase,long timeoutMs)throws Exception;}
 interface Pause {void sleep(long ms)throws InterruptedException;}
 private final SampleReader source;private final Pause pause;
 private volatile long holdUntil,holdStart;private final File dir;private volatile boolean stopped;private Thread worker;private volatile boolean preparing;private final Map<Long,BrightnessSamples.Sample> prepared=new HashMap<>();private volatile BrightnessRefresh refresh;private volatile String preparationFailure;
 LiveB8Brightness(File dir){this(dir,null,Thread::sleep);}
 LiveB8Brightness(File dir,SampleReader source,Pause pause){this.dir=dir;this.source=source==null?this::readSamples:source;this.pause=pause;}
 private synchronized void log(String s){try(FileWriter w=new FileWriter(new File(dir,"physical-brightness-hold.txt"),true)){w.write(SystemClock.elapsedRealtime()+" uptime="+SystemClock.uptimeMillis()+" "+s+"\n");}catch(IOException ignored){}}
 private Map<Long,BrightnessSamples.Sample> readSamples(String phase,long timeoutMs)throws Exception{
  File snapshot=new File(dir,"brightness-source-"+phase+".txt");
  java.lang.Process p=new ProcessBuilder("logcat","-d","-v","monotonic","-t","30000","SurfaceFlinger:I","*:S").redirectErrorStream(true).redirectOutput(snapshot).start();
  if(!p.waitFor(timeoutMs,TimeUnit.MILLISECONDS)){p.destroyForcibly();p.waitFor(100,TimeUnit.MILLISECONDS);throw new IOException("Brightness source timed out before shield");}
  if(p.exitValue()!=0||snapshot.length()>16*1024*1024)throw new IOException("Brightness source unavailable/oversized");
  return BrightnessSamples.parse(new String(Files.readAllBytes(snapshot.toPath()),StandardCharsets.UTF_8));
 }
 String preparationStatus(){return preparing?"pending":preparationFailure==null?"completed":"failed: "+preparationFailure;}
 boolean preparing(){return preparing;}
 void prepareAsync(){preparing=true;Thread t=new Thread(this::prepare,"duo-brightness-prepare");t.setDaemon(true);t.start();}
 void prepare(){
  preparing=true;
  try{
   if(stopped)return;
   Map<Long,BrightnessSamples.Sample> samples=source.read("prepare",2000);
   long[] ids=(long[])Class.forName("android.view.SurfaceControl").getMethod("getPhysicalDisplayIds").invoke(null);
   if(ids.length!=2)throw new IOException("Expected two physical panels");
   Set<Long> missing=new HashSet<>();long now=SystemClock.uptimeMillis();
   for(long id:ids)if(!BrightnessSamples.usable(samples.get(id),now))missing.add(id);
   if(!missing.isEmpty()){
    log("PREPARE missing="+missing+"; requesting temporary brightness refresh before handoff");
    synchronized(this){if(stopped)return;refresh=new BrightnessRefresh(dir);}
    refresh.refresh(missing);
    samples=awaitRestored(ids,missing);
   }
   synchronized(prepared){if(stopped)throw new IOException("Stopped during brightness preparation");prepared.clear();prepared.putAll(samples);}
   log("PREPARE_COMPLETE; per-physical samples cached for this run only");
  }catch(Exception e){preparationFailure=e.toString();log("PREPARE_FAILED "+e+"; switch still requires valid brightness");}
  finally{preparing=false;}
 }
 private Map<Long,BrightnessSamples.Sample> awaitRestored(long[] ids,Set<Long> refreshed)throws Exception{
  // Clearing temporary brightness is asynchronous. A single log snapshot after
  // 250ms can precede the restored hardware command. Retry observation only;
  // never nudge again, accept the probe, or reuse a pre-clear command.
  long deadline=SystemClock.elapsedRealtime()+1200;String pending="no restored commands";
  for(int attempt=1;attempt<=6&&!stopped;attempt++){
   long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<=0)break;
   try{
    Map<Long,BrightnessSamples.Sample> samples=source.read("refreshed",Math.min(remaining,400));
    Set<Long> missing=new HashSet<>();long now=SystemClock.uptimeMillis();
    for(long id:ids){BrightnessSamples.Sample sample=samples.get(id);if(!BrightnessSamples.usable(sample,now)||(refreshed.contains(id)&&sample.ms<refresh.clearedAt(id)))missing.add(id);}
    if(missing.isEmpty()&&!stopped&&SystemClock.elapsedRealtime()<=deadline){log("RESTORE_COMMANDS_READY observations="+attempt+"; fresh post-clear physical commands");return samples;}
    pending="physical="+missing;
   }catch(Exception e){pending=e.toString();}
   log("RESTORE_COMMANDS_PENDING observation="+attempt+" "+pending);
   remaining=deadline-SystemClock.elapsedRealtime();if(remaining>0&&!stopped)pause.sleep(Math.min(125,remaining));
  }
  throw new IOException(stopped?"Stopped awaiting restored brightness":"No fresh restored brightness command within bounded observation: "+pending);
 }
 synchronized void begin(String phase)throws Exception{
  if(stopped)throw new IllegalStateException("Brightness hold stopped");
  if(worker!=null&&worker.isAlive()){holdUntil=Math.min(SystemClock.elapsedRealtime()+2000,holdStart+4000);log("HOLD_EXTEND phase="+phase+" until="+holdUntil+"; same worker, maximum four seconds total");return;}
  if(preparing)throw new IllegalStateException("Brightness preparation still running");
  if(refresh!=null&&preparationFailure!=null)throw new IllegalStateException("Brightness refresh did not complete: "+preparationFailure);
  // Never run logcat after a shield is submitted. Only the validated pre-shield snapshot is used.
  Map<Long,BrightnessSamples.Sample> samples; synchronized(prepared){samples=new HashMap<>(prepared);}
  Class<?> sc=Class.forName("android.view.SurfaceControl");long[] ids=(long[])sc.getMethod("getPhysicalDisplayIds").invoke(null);
  if(ids.length!=2)throw new IllegalStateException("Expected exactly two physical displays");
  Method tokenMethod=sc.getMethod("getPhysicalDisplayToken",long.class),support=sc.getMethod("getDisplayBrightnessSupport",IBinder.class);
  Method setter=sc.getMethod("setDisplayBrightness",IBinder.class,float.class,float.class,float.class,float.class);
  IBinder[] tokens=new IBinder[2];BrightnessSamples.Sample[] values=new BrightnessSamples.Sample[2];
  long now=SystemClock.uptimeMillis();
  for(int i=0;i<2;i++){values[i]=samples.get(ids[i]);if(!BrightnessSamples.usable(values[i],now))throw new IllegalStateException("No recent positive SDR brightness for physical="+ids[i]+"; automatic preparation could not obtain a valid sample; see brightness-refresh.txt");tokens[i]=(IBinder)tokenMethod.invoke(null,ids[i]);if(tokens[i]==null||!Boolean.TRUE.equals(support.invoke(null,tokens[i])))throw new IllegalStateException("Physical brightness API unsupported for "+ids[i]);log("SOURCE phase="+phase+" physical="+ids[i]+" brightness="+values[i].value+" nits="+values[i].nits+" ageMs="+(now-values[i].ms));}
  // The first accepted calls precede the native handoff. No guessed/max brightness fallback.
  write(setter,tokens,values);
  holdStart=SystemClock.elapsedRealtime();holdUntil=holdStart+2000;
  worker=new Thread(()->{int rounds=1;try{while(!stopped&&SystemClock.elapsedRealtime()<holdUntil&&rounds<500){Thread.sleep(8);if(stopped||SystemClock.elapsedRealtime()>=holdUntil)break;write(setter,tokens,values);rounds++;}}catch(Throwable e){log("HOLD_ERROR phase="+phase+" "+e);}finally{log("HOLD_END phase="+phase+" rounds="+rounds+"; no persistent policy/settings changed; system retains control; accepted calls are not pixel proof");}},"physical-brightness-hold");
  worker.setDaemon(true);worker.start();log("HOLD_BEGIN phase="+phase+" boundMs=2000 periodMs=8; may race ColorFade, does not suppress black composition layers");
 }
 private void write(Method setter,IBinder[] tokens,BrightnessSamples.Sample[] values)throws Exception{for(int i=0;i<2;i++){if(stopped)return;BrightnessSamples.Sample s=values[i];if(!Boolean.TRUE.equals(setter.invoke(null,tokens[i],s.value,s.sdr,s.value,s.nits)))throw new IOException("Physical brightness call returned false panel="+i);}}
 void requirePrepared()throws Exception{
  if(preparing||preparationFailure!=null)throw new IOException("Brightness preparation unavailable: "+preparationFailure);
  long[] ids=(long[])Class.forName("android.view.SurfaceControl").getMethod("getPhysicalDisplayIds").invoke(null);
  if(ids.length!=2)throw new IOException("Expected two panels");
  synchronized(prepared){for(long id:ids)if(!BrightnessSamples.usable(prepared.get(id),SystemClock.uptimeMillis()))throw new IOException("No valid prepared brightness for "+id);}
 }
 void close(){stopped=true;BrightnessRefresh r=refresh;if(r!=null)r.close();log("STOP_REQUESTED; no further brightness reassertions");}
}
