package org.duofold.live.experimental.b8;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
/** B8 only: bounded physical brightness reassertion. No settings or global freeze flag. */
final class LiveB8Brightness {
 interface SampleReader {Map<Long,BrightnessSamples.Sample> read(String phase,long timeoutMs)throws Exception;}
 interface Pause {void sleep(long ms)throws InterruptedException;}
 private final SampleReader source;private final BrightnessLogSource logSource;private final Pause pause;private volatile boolean passive,homePreparation;
 private final Object writeLock=new Object();private long[] heldIds;private long tailPhysical=-1;
 private volatile long holdUntil,holdStart;private final File dir;private volatile boolean stopped;private Thread worker;private volatile boolean preparing;private final Map<Long,BrightnessSamples.Sample> prepared=new HashMap<>();private volatile LiveB8BrightnessRefresh refresh;private volatile String preparationFailure;
 LiveB8Brightness(File dir){this(dir,null,Thread::sleep);}
 LiveB8Brightness(File dir,SampleReader source,Pause pause){this.dir=dir;this.logSource=source==null?new BrightnessLogSource(dir):null;this.source=source==null?this::readSamples:source;this.pause=pause;}
 private synchronized void log(String s){try(FileWriter w=new FileWriter(new File(dir,"physical-brightness-hold.txt"),true)){w.write(SystemClock.elapsedRealtime()+" uptime="+SystemClock.uptimeMillis()+" "+s+"\n");}catch(IOException ignored){}}
 private Map<Long,BrightnessSamples.Sample> readSamples(String phase,long timeoutMs)throws Exception{
  long started=SystemClock.elapsedRealtime();
  try{
   Map<Long,BrightnessSamples.Sample> result=logSource.read(phase,timeoutMs);
   log("SOURCE_READ phase="+phase+" tail="+BrightnessLogSource.TAIL_LINES+" elapsedMs="+(SystemClock.elapsedRealtime()-started)+" panels="+result.keySet());
   return result;
  }catch(Exception e){log("SOURCE_READ_FAILED phase="+phase+" tail="+BrightnessLogSource.TAIL_LINES+" elapsedMs="+(SystemClock.elapsedRealtime()-started)+" "+e);throw e;}
 }
 String preparationStatus(){return preparing?"pending":preparationFailure==null?"completed":"failed: "+preparationFailure;}
 boolean preparing(){return preparing;}
 private volatile boolean refreshAllowed=true;
 // Start only the read while the secondary panel wakes. Any temporary
 // brightness refresh still waits for the controller's both-ON observation.
 void prepareEarlyAsync(){refreshAllowed=false;prepareAsync();}
 void allowRefresh(){refreshAllowed=true;}
 void prepareAsync(){preparing=true;Thread t=new Thread(this::prepare,"duo-brightness-prepare");t.setDaemon(true);t.start();}
 void preparePassiveAsync(){passive=true;prepareAsync();}
 // Home may prepare the original B8 refresh, but never wait on a slow logcat
 // command for two seconds. The controller cancels preparation before remapping.
 void prepareHomeAsync(){homePreparation=true;prepareAsync();}
 void preparePassive(){passive=true;prepare();}
 void prepare(){
  preparing=true;
  try{
   if(stopped)return;
   Map<Long,BrightnessSamples.Sample> samples=source.read("prepare",passive||homePreparation?400:2000);
   long[] ids=(long[])Class.forName("android.view.SurfaceControl").getMethod("getPhysicalDisplayIds").invoke(null);
   if(ids.length!=2)throw new IOException("Expected two physical panels");
   Set<Long> missing=new HashSet<>();long now=SystemClock.uptimeMillis();
   for(long id:ids)if(!BrightnessSamples.usable(samples.get(id),now))missing.add(id);
   if(!missing.isEmpty()){
    if(passive)throw new IOException("Passive samples unavailable for "+missing+"; native brightness policy retained; no temporary probe");
    long readyDeadline=SystemClock.elapsedRealtime()+2000;
    while(!refreshAllowed&&!stopped&&SystemClock.elapsedRealtime()<readyDeadline)pause.sleep(16);
    if(stopped||!refreshAllowed)throw new IOException("Brightness refresh canceled or panels not ready");
    log("PREPARE missing="+missing+"; requesting temporary brightness refresh before handoff");
    synchronized(this){if(stopped)return;refresh=new LiveB8BrightnessRefresh(dir);}
    refresh.refresh(missing);
    // The primary refresh may update its follower too. Even a previously
    // valid primary sample must now be replaced by its post-clear command.
    Set<Long> refreshed=refresh.refreshedPhysical();
    for(long id:ids)if(!refreshed.contains(id))throw new IOException("Refresh did not cover physical panel "+id);
    samples=awaitRestored(ids,refreshed);
   }
   synchronized(prepared){if(stopped)throw new IOException("Stopped during brightness preparation");prepared.clear();prepared.putAll(samples);}
   log("PREPARE_COMPLETE; per-physical samples cached for this run only");
  }catch(Exception e){preparationFailure=e.toString();log("PREPARE_FAILED "+e+(passive?"; optional hold skipped":"; switch still requires valid brightness"));}
  finally{if(logSource!=null)logSource.close();preparing=false;}
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
 // For an ordinary app remap only. Physical tokens stay with their panel across the D0/D1 exchange.
 private static final class PanelWrites {
  int accepted,rejected;
  long firstReject=-1,lastAccepted=-1,maxGap,firstRecovery=-1;
  void record(boolean ok,long now){
   if(!ok){rejected++;if(firstReject<0)firstReject=now;return;}
   if(firstReject>=0&&firstRecovery<0)firstRecovery=now;
   if(lastAccepted>=0)maxGap=Math.max(maxGap,now-lastAccepted);
   lastAccepted=now;accepted++;
  }
  public String toString(){return "accepted="+accepted+" rejected="+rejected+" firstRejectAt="+firstReject+
    " firstRecoveryAt="+firstRecovery+" lastAcceptedAt="+lastAccepted+" maxAcceptedGapMs="+maxGap;}
 }
 private String outgoingPhysical="";
 private volatile int outgoingIndex=-1;
 private static final int OUTGOING_WINDOW_MS=320;
 private int extraWrites;
 private final PanelWrites[] writes={new PanelWrites(),new PanelWrites()};
 void beginAppHandoff(String physical)throws Exception{
  synchronized(writeLock){
   outgoingPhysical=physical;extraWrites=0;
   writes[0]=new PanelWrites();writes[1]=new PanelWrites();
  }
  begin("live-handoff");
 }
 String flashReport(){
  synchronized(writeLock){
   if(outgoingPhysical.isEmpty())return "";
   StringBuilder report=new StringBuilder("outgoing=").append(outgoingPhysical).append(" holdStartAt=").append(holdStart).append(" extraAttempts=").append(extraWrites);
   if(heldIds==null)return report.append(" hold never armed").toString();
   for(int i=0;i<2;i++)report.append(" physical=").append(heldIds[i]).append(" [").append(writes[i]).append("]");
   return report.append("; brightness API results only").toString();
  }
 }
 synchronized void begin(String phase)throws Exception{
  if(!phase.equals("live-handoff"))synchronized(writeLock){outgoingIndex=-1;}
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
  synchronized(writeLock){
   heldIds=ids.clone();tailPhysical=-1;outgoingIndex=-1;
   if(phase.equals("live-handoff"))for(int i=0;i<2;i++)if(outgoingPhysical.equals("local:"+ids[i]))outgoingIndex=i;
  }
  write(setter,tokens,values,true,false);
  holdStart=SystemClock.elapsedRealtime();holdUntil=holdStart+2000;
  worker=new Thread(()->{
   int rounds=1;
   try{
    while(!stopped&&SystemClock.elapsedRealtime()<holdUntil&&rounds<500){
     long nextPair=SystemClock.elapsedRealtime()+8;
     if(outgoingIndex>=0&&SystemClock.elapsedRealtime()-holdStart<OUTGOING_WINDOW_MS){
      Thread.sleep(4);
      write(setter,tokens,values,false,true);
     }
     long remaining=nextPair-SystemClock.elapsedRealtime();
     if(remaining>0)Thread.sleep(remaining);
     if(stopped||SystemClock.elapsedRealtime()>=holdUntil)break;
     write(setter,tokens,values,false,false);rounds++;
    }
   }catch(Throwable e){log("HOLD_ERROR phase="+phase+" "+e);}
   finally{log("HOLD_END phase="+phase+" rounds="+rounds+"; accepted calls are not pixel proof");}
  },"physical-brightness-hold");
  worker.setDaemon(true);worker.start();log("HOLD_BEGIN phase="+phase+" boundMs=2000 pairPeriodMs=8");
 }
 private void write(Method setter,IBinder[] tokens,BrightnessSamples.Sample[] values,boolean priming,boolean outgoingOnly)throws Exception{
  synchronized(writeLock){
   long now=SystemClock.elapsedRealtime();
   if(stopped||(!priming&&now>=holdUntil))return;
   boolean appHandoff=outgoingIndex>=0;
   if(outgoingOnly){
    if(!appHandoff||now-holdStart>=OUTGOING_WINDOW_MS||extraWrites>=OUTGOING_WINDOW_MS/4)return;
    extraWrites++;
   }
   int first=appHandoff?outgoingIndex:0;
   for(int n=0;n<(outgoingOnly?1:2);n++){
    int i=n==0?first:1-first;
    if(stopped)return;
    BrightnessSamples.Sample sample=values[i];
    if(tailPhysical>=0&&sample.id!=tailPhysical)continue;
    boolean ok;
    try{
     ok=Boolean.TRUE.equals(setter.invoke(null,tokens[i],sample.value,sample.sdr,sample.value,sample.nits));
    }catch(InvocationTargetException e){
     // An unavailable display during exchange can reject its write. Keep the peer alive.
     if(!appHandoff||priming||!(e.getCause() instanceof IllegalStateException))throw e;
     ok=false;
    }
    if(appHandoff)writes[i].record(ok,SystemClock.elapsedRealtime());
    if(!ok&&(priming||!appHandoff))throw new IOException("Physical brightness call returned false panel="+i);
   }
  }
 }
 /** Narrow an existing writer to the native cover; never start or revive one at CLOSED. */
 boolean retainCoverTail(String physical){
  if(physical==null||!physical.matches("local:[0-9]+"))return false;
  final long id;try{id=Long.parseLong(physical.substring(6));}catch(NumberFormatException e){return false;}
  synchronized(writeLock){
   long now=SystemClock.elapsedRealtime();boolean known=false;
   if(heldIds!=null)for(long held:heldIds)if(held==id)known=true;
   if(stopped||!known||worker==null||!worker.isAlive()||now>=holdUntil)return false;
   tailPhysical=id;holdUntil=Math.min(now+400,holdStart+4000);
  }
  log("COVER_TAIL physical="+id+" boundMs=400; existing writer only; inner writes stopped; no power-mode change");
  return true;
 }
 void requirePrepared()throws Exception{
  if(preparing||preparationFailure!=null)throw new IOException("Brightness preparation unavailable: "+preparationFailure);
  long[] ids=(long[])Class.forName("android.view.SurfaceControl").getMethod("getPhysicalDisplayIds").invoke(null);
  if(ids.length!=2)throw new IOException("Expected two panels");
  synchronized(prepared){for(long id:ids)if(!BrightnessSamples.usable(prepared.get(id),SystemClock.uptimeMillis()))throw new IOException("No valid prepared brightness for "+id);}
 }
 void close(){
  // A write that passed the stopped check must finish BEFORE teardown returns.
  // Otherwise a late positive command can land after Android's final power policy.
  synchronized(writeLock){stopped=true;}
  if(logSource!=null)logSource.close();
  LiveB8BrightnessRefresh r;synchronized(this){r=refresh;}
  if(r!=null)r.close();log("STOP_REQUESTED; no further brightness reassertions");
 }
}
