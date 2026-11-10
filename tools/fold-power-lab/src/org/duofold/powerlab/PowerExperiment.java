package org.duofold.powerlab;
import android.os.*;
import android.content.Context;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit token-owned experiments. No arbitrary Binder transactions or persistent settings. */
final class PowerExperiment {
 private final File dir;
 private final java.util.function.Consumer<String> snapshotter;
 private final ThreadPoolExecutor snapshots=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1));
 private volatile long lastBothSample;
 private volatile int expectedState=-1,sampledState=-2;
 private final ScheduledExecutorService timers=Executors.newScheduledThreadPool(3);
 private final ScheduledExecutorService actions=Executors.newSingleThreadScheduledExecutor();
 private final IBinder[] tokens={new Binder(),new Binder(),new Binder()};
 private final boolean[] pending={false,false,false};
 private volatile boolean stopped,cleaned=true,bothOn,renewDisabled;
 private Object display,stateManager;private Method setter,globalSetter,stateCancel;
 private boolean stateOwned;private PowerManager.WakeLock wake;
 private boolean legacy;private long start;private ExperimentConfig config;private int generation,desired,retries,switchCount;private Thread sampler;
 PowerExperiment(File dir,java.util.function.Consumer<String> snapshotter){this.dir=dir;this.snapshotter=snapshotter;}
 static Object service(String name,String api)throws Exception{
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(api+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
 }
 private synchronized void log(String s){try(FileWriter f=new FileWriter(new File(dir,"power-experiment.txt"),true)){f.write(SystemClock.elapsedRealtime()+" +"+(SystemClock.elapsedRealtime()-start)+"ms "+s+"\n");}catch(IOException ignored){}}
 void start(int mode){
  ExperimentConfig c=new ExperimentConfig();c.duration=10;c.initial=0;c.alternate=false;
  if(mode==6)c.target=1;if(mode==7)c.target=2;if(mode==8)c.target=3;if(mode==9)c.wake=3;
  if(mode>=10){c.initial=(mode==10||mode==12)?1:2;c.order=1;c.settle=1000;if(mode>=12)c.target=3;}
  legacy=true;start(c);log("LEGACY preset="+mode);
 }
 void start(ExperimentConfig c){
  c.validate();config=c;start=SystemClock.elapsedRealtime();
  log("START UID="+android.os.Process.myUid()+" "+c.describe().replace('\n',' '));
  timers.schedule(this::close,c.duration,TimeUnit.SECONDS);
  timers.schedule(()->{if(!cleaned){android.os.Process.killProcess(android.os.Process.myPid());}},c.duration+5,TimeUnit.SECONDS);
  timers.schedule(()->timers.shutdown(),c.duration+6,TimeUnit.SECONDS);
  sampler=new Thread(this::sample,"power-samples");sampler.start();
  actions.execute(()->{
   try{
    if(c.initial!=0||c.target!=0||c.wake!=0)checkConflict();
    if(c.wake!=0)acquireWake();
    if(c.order==0)applyOn();
    if(c.initial!=0)requestState(c.initial,"INITIAL");
    if(legacy&&c.order==1)applyOn();
    if(stopped)return;
    actions.schedule(()->{
     if(stopped)return;
     if(c.order==1&&!legacy)applyOn();
     if(c.renewal>0&&c.target!=0)actions.scheduleWithFixedDelay(this::applyOn,c.renewal,c.renewal,TimeUnit.MILLISECONDS);
     if(c.alternate)gate(SystemClock.elapsedRealtime()+3000);
    },c.settle,TimeUnit.MILLISECONDS);
   }catch(Throwable e){log("INITIAL REJECTED "+root(e));close();}
  });
 }
 private void checkConflict()throws Exception{
  Object states=service("device_state","android.hardware.devicestate.IDeviceStateManager");
  Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(states);
  Object current=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
  if(!current.getClass().getMethod("getIdentifier").invoke(current).equals(base.getClass().getMethod("getIdentifier").invoke(base)))throw new IllegalStateException("Another device-state request is active. Disable Duo and retry.");
 }
 private void gate(long deadline){
  if(stopped)return;
  if(bothOn&&sampledState==expectedState&&SystemClock.elapsedRealtime()-lastBothSample<=250){log("BOTH_ON_GATE passed: two sampled physical IDs ON/committed ON; no optical proof");actions.schedule(this::switchNext,config.period,TimeUnit.MILLISECONDS);return;}
  if(SystemClock.elapsedRealtime()>=deadline){log("BOTH_ON_GATE timeout: sequence aborted, no primary swaps performed");close();return;}
  actions.schedule(()->gate(deadline),100,TimeUnit.MILLISECONDS);
 }
 private void switchNext(){
  if(stopped||switchCount>=config.switches)return;
  if(SystemClock.elapsedRealtime()-start>=config.duration*1000L-config.period){log("SEQUENCE ended: reserve final dwell before cleanup");return;}
  try{int next=desired==1?2:1;switchCount++;requestState(next,"SWITCH "+switchCount);}
  catch(Throwable e){log("SWITCH REJECTED "+root(e));close();return;}
  if(!stopped)actions.schedule(this::switchNext,config.period,TimeUnit.MILLISECONDS);
 }
 private synchronized void requestState(int primary,String why)throws Exception{
  if(stopped)return;
  Class<?> mc=Class.forName("android.hardware.devicestate.DeviceStateManager");
  if(stateManager==null){stateManager=mc.getConstructor().newInstance();stateCancel=mc.getMethod("cancelStateRequest");}
  int id=-1;String target=primary==1?"CONCURRENT_INNER_DEFAULT":"CONCURRENT_OUTER_DEFAULT";
  for(Object s:(List<?>)mc.getMethod("getSupportedDeviceStates").invoke(stateManager))if(target.equals(s.getClass().getMethod("getName").invoke(s)))id=(int)s.getClass().getMethod("getIdentifier").invoke(s);
  if(id<0)throw new IllegalStateException("Unsupported named state "+target);
  Class<?> rc=Class.forName("android.hardware.devicestate.DeviceStateRequest"),cc=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
  Object builder=rc.getMethod("newBuilder",int.class).invoke(null,id);
  if(config.flags!=0)builder.getClass().getMethod("setFlags",int.class).invoke(builder,config.flags);
  Object built=builder.getClass().getMethod("build").invoke(builder);
  final int gen=++generation;desired=primary;expectedState=id;
  Object cb=Proxy.newProxyInstance(cc.getClassLoader(),new Class<?>[]{cc},(proxy,m,args)->{
   if(m.getName().equals("hashCode"))return System.identityHashCode(proxy);
   if(m.getName().equals("equals"))return proxy==args[0];
   if(m.getName().equals("toString"))return "FoldPowerLabCallback#"+gen;
   synchronized(PowerExperiment.this){
    boolean current=gen==generation;log("DEVICE_STATE "+m.getName()+" generation="+gen+" current="+current+" stopped="+stopped+"; callback supplies no cancellation reason");
    if(current&&!stopped&&m.getName().equals("onRequestCanceled")){
     stateOwned=false;
     if(legacy)return null;
     if(config.cancellation==1&&retries<3){retries++;log("RETRY queued "+retries+"/3");actions.schedule(()->{try{ synchronized(PowerExperiment.this){if(!stopped&&gen==generation)requestState(desired,"CANCEL_RETRY");}}catch(Throwable e){log("RETRY rejected "+root(e));close();}},500,TimeUnit.MILLISECONDS);}
     else timers.execute(()->{synchronized(PowerExperiment.this){if(!stopped&&gen==generation){log("CURRENT REQUEST CANCELED: stop sequence");close();}}});
    }
   }return null;
  });
  boolean previousOwned=stateOwned;stateOwned=true;cleaned=false;long begin=SystemClock.elapsedRealtime();
  log(why+" REQUEST generation="+gen+" target="+target+" id="+id+" flags="+config.flags);
  try{mc.getMethod("requestState",rc,Executor.class,cc).invoke(stateManager,built,(Executor)Runnable::run,cb);}catch(Exception e){if(definiteRejection(e))stateOwned=previousOwned;throw e;}
  log(why+" ACCEPTED generation="+gen+" callMs="+(SystemClock.elapsedRealtime()-begin)+"; activation/pixels separate");
  if(config.snapshots)try{snapshots.execute(()->{log("SNAPSHOT begin generation="+gen);snapshotter.accept("request-"+gen);log("SNAPSHOT end generation="+gen);});}catch(RejectedExecutionException e){log("SNAPSHOT skipped: previous dumps still queued");}
 }
 private synchronized void applyOn(){
  if(stopped||config.target==0||renewDisabled)return;
  try{
   if(display==null)display=service("display","android.hardware.display.IDisplayManager");
   Class<?> dc=Class.forName("android.hardware.display.IDisplayManager");
   int oemArgument=10000; // Keep the value accepted in 0.2.0; do not infer its semantics.
   // Final OEM integer is logged verbatim. App cleanup/watchdog enforce our bound;
   // acceptance does not verify the OEM's interpretation of that integer.
   if(config.backend==1){
    if(globalSetter==null)globalSetter=dc.getMethod("setDisplayStateOverride",IBinder.class,int.class,int.class);
    invokeOn(2,globalSetter,tokens[2],2,oemArgument);
    log("GLOBAL_ON accepted args=(token,2,"+oemArgument+"); OEM final integer semantics unverified");
   }else{
    if(setter==null)setter=dc.getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);
    for(int id=0;id<2;id++)if(config.target==3||id==config.target-1){invokeOn(id,setter,tokens[id],2,id,oemArgument);log("ON accepted args=(token,2,"+id+","+oemArgument+"); logical ID may remap; OEM final integer semantics unverified");}
   }
  }catch(Throwable e){renewDisabled=true;log("ON REJECTED/UNAVAILABLE; renewals disabled; remaining recipe continues: "+root(e));}
 }
 private void invokeOn(int token,Method method,Object... args)throws Exception{
  boolean previouslyOwned=pending[token];cleaned=false;pending[token]=true;
  try{method.invoke(display,args);}catch(Exception e){if(definiteRejection(e))pending[token]=previouslyOwned;throw e;}
 }
 private static boolean definiteRejection(Throwable e){Throwable r=root(e);return r instanceof SecurityException||r instanceof IllegalArgumentException||r instanceof UnsupportedOperationException;}
 private synchronized void acquireWake(){
  if(stopped)return;
  try{
   Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("currentActivityThread").invoke(null);if(thread==null)thread=at.getMethod("systemMain").invoke(null);
   Context context=(Context)at.getMethod("getSystemContext").invoke(thread);PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
   int level=new int[]{0,PowerManager.PARTIAL_WAKE_LOCK,PowerManager.SCREEN_DIM_WAKE_LOCK,PowerManager.SCREEN_BRIGHT_WAKE_LOCK,PowerManager.FULL_WAKE_LOCK}[config.wake];
   log("WAKE level="+level+" reportedSupported="+pm.isWakeLockLevelSupported(level));
   wake=pm.newWakeLock(level,"FoldPowerLab:bounded");cleaned=false;wake.acquire(config.duration*1000L);log("WAKE acquired held="+wake.isHeld());
  }catch(Throwable e){log("WAKE REJECTED/UNAVAILABLE "+root(e));}
 }
 void close(){
  stopped=true;actions.shutdownNow();snapshots.shutdownNow();
  synchronized(this){
   try{
    if(wake!=null&&wake.isHeld())wake.release();
    for(int id=0;id<3;id++)if(pending[id]){
     try{if(id==2)globalSetter.invoke(display,tokens[2],0,-1);else setter.invoke(display,tokens[id],0,id,-1);pending[id]=false;log("RELEASE accepted token="+id);}
     catch(Throwable e){log("RELEASE pending token="+id+" "+root(e));}
    }
    if(stateOwned){try{stateCancel.invoke(stateManager);stateOwned=false;log("DEVICE_STATE released");}catch(Throwable e){log("DEVICE_STATE release pending "+root(e));}}
    cleaned=!stateOwned&&!pending[0]&&!pending[1]&&!pending[2]&&(wake==null||!wake.isHeld());
    log("STOP elapsed="+(SystemClock.elapsedRealtime()-start)+"ms cleanupComplete="+cleaned+" switches="+switchCount+" retries="+retries);
    if(!cleaned&&!timers.isShutdown())timers.schedule(this::close,250,TimeUnit.MILLISECONDS);
   }catch(Throwable e){log("CLEANUP error "+root(e));}
  }
 }
 void awaitSampler()throws InterruptedException{if(!snapshots.awaitTermination(6,TimeUnit.SECONDS))throw new IllegalStateException("Snapshot worker did not stop");if(sampler!=null){sampler.join(1500);if(sampler.isAlive())throw new IllegalStateException("Sampler blocked; report incomplete");}}
 private void sample(){
  long previous=SystemClock.elapsedRealtime(),maxGap=0;int count=0;
  Map<String,Integer> last=new HashMap<>();Map<String,Integer> off=new TreeMap<>();
  try(BufferedWriter out=new BufferedWriter(new FileWriter(new File(dir,"power-samples.txt")))){
   Object dm=service("display","android.hardware.display.IDisplayManager");Class<?> dc=Class.forName("android.hardware.display.IDisplayManager");
   Method ids=dc.getMethod("getDisplayIds",boolean.class),di=dc.getMethod("getDisplayInfo",int.class);
   Object pm=service("power","android.os.IPowerManager");Method interactive=Class.forName("android.os.IPowerManager").getMethod("isInteractive");
   Object sm=service("device_state","android.hardware.devicestate.IDeviceStateManager");Method si=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo");
   while(!stopped&&SystemClock.elapsedRealtime()-start<config.duration*1000L){
    long begin=SystemClock.elapsedRealtime(),gap=begin-previous;previous=begin;maxGap=Math.max(maxGap,gap);
    StringBuilder line=new StringBuilder("t="+begin+" offsetMs="+(begin-start)+" gapMs="+gap+" interactive="+interactive.invoke(pm));
    Object info=si.invoke(sm);Object current=info.getClass().getField("currentState").get(info);sampledState=(int)current.getClass().getMethod("getIdentifier").invoke(current);for(String field:new String[]{"baseState","currentState"})try{line.append(" ").append(field).append("=").append(info.getClass().getField(field).get(info));}catch(Throwable e){line.append(" ").append(field).append(" unavailable");}
    Set<String> lit=new HashSet<>();
    for(int id:(int[])ids.invoke(dm,false)){
     Object d=di.invoke(dm,id);line.append(" D").append(id);if(d==null){line.append(" missing");continue;}
     for(String field:new String[]{"uniqueId","state","committedState","logicalWidth","logicalHeight","rotation","layerStack"})try{line.append(" ").append(field).append("=").append(d.getClass().getField(field).get(d));}catch(Throwable ignored){}
     String key=String.valueOf(d.getClass().getField("uniqueId").get(d));int state=d.getClass().getField("state").getInt(d),committed=d.getClass().getField("committedState").getInt(d);
     if(state==2&&committed==2)lit.add(key);
     Integer old=last.put(key,state);if(state==1&&(old==null||old!=1))off.put(key,off.getOrDefault(key,0)+1);
    }
    bothOn=lit.size()>=2;lastBothSample=SystemClock.elapsedRealtime();line.append(" queryMs=").append(SystemClock.elapsedRealtime()-begin);out.write(line.toString());out.newLine();out.flush();count++;
    Thread.sleep(Math.max(1,config.sample-(SystemClock.elapsedRealtime()-begin)));
   }
   out.write("END samples="+count+" maximumGapMs="+maxGap+"; sampled software states, not optical blackout\n");
   try(FileWriter summary=new FileWriter(new File(dir,"sample-summary.txt"))){summary.write("samples="+count+" maximumGapMs="+maxGap+"\nObserved OFF entries by physical ID="+off+"\nCounts include initial acquisition, exclude cleanup after sampler stops. Absent logical displays are not counted as OFF. Consult timestamped samples and action log; this is not optical blackout duration.\n");}
  }catch(Throwable e){bothOn=false;log("SAMPLER unavailable/error "+root(e));}
 }
 static String inventory(){StringBuilder b=new StringBuilder("Discovery only. Listed methods are NOT automatically invoked. Permission and effect unverified.\n");for(String name:new String[]{"android.hardware.display.IDisplayManager","android.os.IPowerManager","android.hardware.devicestate.IDeviceStateManager","android.view.IWindowManager","android.view.SurfaceControl","android.hardware.SensorManager"}){b.append("\nCLASS ").append(name).append('\n');try{for(Method m:Class.forName(name).getDeclaredMethods())b.append(m).append('\n');}catch(Throwable e){b.append(root(e)).append('\n');}}return b.toString();}
 private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
