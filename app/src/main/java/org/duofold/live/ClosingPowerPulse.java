package org.duofold.live;
import android.os.*;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
/** Opt-in, single direct power request at the closing endpoint; never delays state release. */
final class ClosingPowerPulse {
 private volatile long inputUntil;
 private Handler handler;
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private Object display,power,states,global;
 private Class<?> powerApi,stateApi;
 private Method requestPower;
 private long deadline,restoreAt,lastSample,maxGap;
 private boolean active,attempted,restorePending;
 private int restoreRetries;
 private String physical="",previous="";
 void update(boolean enabled,float angle,boolean fresh,boolean unlocked){
  inputUntil=enabled&&fresh&&unlocked&&angle==0?SystemClock.elapsedRealtime()+ClosingPowerPolicy.INPUT_LEASE_MS:0;
 }
 private synchronized void log(String s){events.addLast(SystemClock.elapsedRealtime()+": "+s);while(events.size()>36)events.removeFirst();}
 synchronized String report(){return "Closing cover direct-power experiment (software observations):\n"+(events.isEmpty()?"Not armed":String.join("\n",events));}
 synchronized void arm(){
  if(SystemClock.elapsedRealtime()>=inputUntil)return;
  if(handler==null){HandlerThread thread=new HandlerThread("Duo-closing-power");thread.start();handler=new Handler(thread.getLooper());}
  final long requestedAt=SystemClock.elapsedRealtime();
  handler.post(()->{
   if(active||restorePending){log("SKIP: previous experiment still active");return;}
   if(SystemClock.elapsedRealtime()>=inputUntil){log("SKIP: closing input no longer valid");return;}
   try{
    init();Object info=displayInfo();
    if(info==null){log("SKIP: D0 missing");return;}
    physical=String.valueOf(info.getClass().getField("uniqueId").get(info));
    int w=info.getClass().getField("logicalWidth").getInt(info),h=info.getClass().getField("logicalHeight").getInt(info);
    // Confirm the already-primary cover, allowing rotation. Never target the inner panel.
    if(Math.min(w,h)!=1248||Math.max(w,h)!=1972){log("SKIP: D0 is not the verified cover geometry");return;}
    deadline=requestedAt+ClosingPowerPolicy.WATCH_MS;active=true;attempted=false;previous="";lastSample=0;maxGap=0;
    log("ARM after closing release decision; D0="+physical+"; watch=1200 ms; one ON attempt; restore=250 ms");
    handler.post(poll);
   }catch(Exception e){log("UNAVAILABLE: "+root(e));}
  });
 }
 private static Object service(String name,String api)throws Exception{
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(api+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
 }
 private void init()throws Exception{
  DeviceCompatibility.requireEligible(Build.MODEL,Build.VERSION.SDK_INT);
  if(!"SM-F971U".equals(Build.MODEL))throw new IllegalStateException("Direct closing power experiment is verified for SM-F971U only");
  if(global==null)global=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  if(display==null){display=service("display","android.hardware.display.IDisplayManager");requestPower=Class.forName("android.hardware.display.IDisplayManager").getMethod("requestDisplayPower",int.class,int.class);}
  if(power==null){powerApi=Class.forName("android.os.IPowerManager");power=service("power",powerApi.getName());}
  if(states==null){stateApi=Class.forName("android.hardware.devicestate.IDeviceStateManager");states=service("device_state",stateApi.getName());}
 }
 private Object displayInfo()throws Exception{return global.getClass().getMethod("getDisplayInfo",int.class).invoke(global,0);}
 private int stateId(Object info,String field)throws Exception{Object s=info.getClass().getField(field).get(info);return (int)s.getClass().getMethod("getIdentifier").invoke(s);}
 private final Runnable poll=new Runnable(){public void run(){
  if(!active)return;
  long now=SystemClock.elapsedRealtime();if(lastSample>0)maxGap=Math.max(maxGap,now-lastSample);lastSample=now;
  long identity=Binder.clearCallingIdentity();
  try{
   if(now>=inputUntil||now>=deadline){finish(now>=deadline?"watch deadline":"input disabled/stale/reopened/locked");return;}
   Object d=displayInfo();
   if(d==null||!physical.equals(String.valueOf(d.getClass().getField("uniqueId").get(d)))){finish("physical panel changed or disappeared");return;}
   boolean interactive=(boolean)powerApi.getMethod("isInteractive").invoke(power);
   if(!interactive){finish("device no longer interactive");return;}
   int ds=d.getClass().getField("state").getInt(d);
   Object info=stateApi.getMethod("getDeviceStateInfo").invoke(states);
   int current=stateId(info,"currentState"),base=stateId(info,"baseState");
   String sample="D0 state="+ds+" current="+current+" base="+base+" attempted="+attempted;
   if(!sample.equals(previous)){previous=sample;log(sample);}
   if(ClosingPowerPolicy.mayPulse(SystemClock.elapsedRealtime(),deadline,inputUntil,attempted,interactive,true,current,base,ds)){
    attempted=true;restorePending=true;restoreRetries=0;restoreAt=SystemClock.elapsedRealtime()+ClosingPowerPolicy.PULSE_MS;
    // Schedule cleanup before the Binder call; UNKNOWN reapplies the framework's current request.
    handler.postDelayed(restore,ClosingPowerPolicy.PULSE_MS);
    long start=SystemClock.elapsedRealtime();
    Object accepted=requestPower.invoke(display,0,2);
    log("requestDisplayPower(D0, ON) returned="+accepted+"; call="+(SystemClock.elapsedRealtime()-start)+" ms; not optical proof");
   }
   handler.postDelayed(this,10);
  }catch(Exception e){log("FAILED: "+root(e));finish("request/sample failure");}
  finally{Binder.restoreCallingIdentity(identity);}
 }};
 private void finish(String reason){active=false;handler.removeCallbacks(poll);restorePolicy(reason);log("FINISH: "+reason+"; attempted="+attempted+"; maximum sample gap="+maxGap+" ms");}
 private final Runnable restore=()->restorePolicy("250 ms pulse deadline");
 private void restorePolicy(String reason){
  if(!restorePending)return;
  long identity=Binder.clearCallingIdentity();
  try{
   Object accepted=requestPower.invoke(display,0,0);
   restorePending=false;handler.removeCallbacks(restore);log("requestDisplayPower(D0, UNKNOWN/current policy) returned="+accepted+"; "+reason+"; elapsed since ON request="+(SystemClock.elapsedRealtime()-(restoreAt-ClosingPowerPolicy.PULSE_MS))+" ms");
  }catch(Exception e){log("POLICY RESTORE FAILED: "+root(e));
   handler.removeCallbacks(restore);if(restoreRetries++<3)handler.postDelayed(restore,100);
   // Pending failure blocks subsequent experiments; never issue another ON until cleanup succeeds.
  }
  finally{Binder.restoreCallingIdentity(identity);}
 }
 private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
