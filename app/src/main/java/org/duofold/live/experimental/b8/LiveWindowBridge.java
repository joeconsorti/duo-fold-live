package org.duofold.live.experimental.b8;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Mirrors WMS windowing layers, never the compositor's ColorFade roots.
 * This is a visual bypass experiment, not a ColorFade-disable mechanism. */
final class LiveWindowBridge {
 private final File dir;private final boolean freeze;
 private final List<Object> surfaces=new ArrayList<>();
 private final Map<String,Object> physicalContainers=new HashMap<>();private final Map<String,Integer> physicalStacks=new HashMap<>();
 private final ScheduledExecutorService watchdog=Executors.newScheduledThreadPool(2);
 private Class<?> sc,tc;private volatile boolean committed,submitted,closed;
 private int cleanupAttempts;private boolean revealSubmitted;
 LiveWindowBridge(File dir){this(dir,false);}
 LiveWindowBridge(File dir,boolean freeze){this.dir=dir;this.freeze=freeze;}
 private synchronized void log(String s){try(FileWriter w=new FileWriter(new File(dir,"live-window-bridge.txt"),true)){w.write(SystemClock.elapsedRealtime()+" "+s+"\n");}catch(IOException ignored){}}
 private static Object call(Object o,String name,Class<?>[] types,Object...args)throws Exception{return o.getClass().getMethod(name,types).invoke(o,args);}
 synchronized boolean ready(){return BridgePlan.canPromote(submitted,committed,closed);}
 synchronized boolean isClosed(){return closed;}
 synchronized void arm()throws Exception{
  if(closed||submitted||!surfaces.isEmpty())throw new IllegalStateException("Bridge already used");
  // Independent of the task/promotion executor; remove even when its tick loop stalls.
  watchdog.schedule(this::close,7,TimeUnit.SECONDS);
  // Separate watchdog thread can exit this worker even if a Binder call holds our monitor.
  watchdog.schedule(()->{if(!closed)android.os.Process.killProcess(android.os.Process.myPid());},9,TimeUnit.SECONDS);
  sc=Class.forName("android.view.SurfaceControl");tc=Class.forName("android.view.SurfaceControl$Transaction");
  Object transaction=null;
  try{
   Object wm=PowerExperiment.service("window","android.view.IWindowManager"),dm=PowerExperiment.service("display","android.hardware.display.IDisplayManager");
   Class<?> wc=Class.forName("android.view.IWindowManager"),dc=Class.forName("android.hardware.display.IDisplayManager");
   transaction=tc.getConstructor().newInstance();
   for(int id=0;id<2;id++){
    Object info=dc.getMethod("getDisplayInfo",int.class).invoke(dm,id);
    if(info==null)throw new IllegalStateException("D"+id+" absent");
    int stack=info.getClass().getField("layerStack").getInt(info);
    Object mirror=sc.getDeclaredConstructor().newInstance();surfaces.add(mirror);
    if(!Boolean.TRUE.equals(wc.getMethod("mirrorDisplay",int.class,sc).invoke(wm,id,mirror)))throw new IllegalStateException("WMS mirrorDisplay rejected D"+id);
    if(!Boolean.TRUE.equals(call(mirror,"isValid",new Class<?>[]{})))throw new IllegalStateException("Invalid mirror D"+id);
    Object builder=Class.forName("android.view.SurfaceControl$Builder").getConstructor().newInstance();
    call(builder,"setName",new Class<?>[]{String.class},"FoldPowerLabBridge_D"+id);
    call(builder,"setContainerLayer",new Class<?>[]{});
    call(builder,"setHidden",new Class<?>[]{boolean.class},true);
    Object container=call(builder,"build",new Class<?>[]{});surfaces.add(container);
    call(transaction,"setLayerStack",new Class<?>[]{sc,int.class},container,stack);
    call(transaction,"setLayer",new Class<?>[]{sc,int.class},container,2147483646);
    call(transaction,"reparent",new Class<?>[]{sc,sc},mirror,container);
    call(transaction,"show",new Class<?>[]{sc},mirror);
    call(transaction,"show",new Class<?>[]{sc},container);
    if(freeze){freezeFrame(transaction,mirror,container,info,id);
     String physical=String.valueOf(info.getClass().getField("uniqueId").get(info));physicalContainers.put(physical,container);physicalStacks.put(physical,stack);
    }
    log("PREPARED D"+id+" physical="+info.getClass().getField("uniqueId").get(info)+" stack="+stack+" z=2147483646; firmware ColorFade z=1073741825; no transform/crop; window subtree only; no input window");
   }
   Class<?> listener=Class.forName("android.view.SurfaceControl$TransactionCommittedListener");
   Object callback=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(proxy,method,args)->{
    if(method.getName().equals("onTransactionCommitted")){synchronized(this){if(!closed){committed=true;log("ARM_COMMIT callback; not pixel proof");}}return null;}
    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
    if(method.getName().equals("equals"))return proxy==args[0];
    return "FoldPowerLabBridgeCommit";
   });
   call(transaction,"addTransactionCommittedListener",new Class<?>[]{Executor.class,listener},(Executor)Runnable::run,callback);
   submitted=true;call(transaction,"apply",new Class<?>[]{});
   log("ARM_SUBMITTED; waiting for commit; seven-second removal watchdog armed");
  }catch(Exception e){log("ARM_REJECTED "+root(e));close();throw e;}
  finally{if(transaction!=null)try{call(transaction,"close",new Class<?>[]{});}catch(Exception ignored){}}
 }
 /** Follow physical panels when Android exchanges logical layer stacks. */
 synchronized void followPhysicalStacks(){
  if(!freeze||closed||revealSubmitted)return;Object transaction=null;
  try{
   Object dm=PowerExperiment.service("display","android.hardware.display.IDisplayManager");Class<?> dc=Class.forName("android.hardware.display.IDisplayManager");
   Map<String,Integer> observed=new HashMap<>();
   for(int id=0;id<2;id++){Object info=dc.getMethod("getDisplayInfo",int.class).invoke(dm,id);if(info!=null)observed.put(String.valueOf(info.getClass().getField("uniqueId").get(info)),info.getClass().getField("layerStack").getInt(info));}
   // Ambiguous transient mapping must not put two shields on the same stack.
   if(new HashSet<Integer>(observed.values()).size()!=observed.size())return;
   for(String physical:physicalContainers.keySet()){
    Integer stack=observed.get(physical);if(stack==null)stack=-1; // Detach a vanished panel's shield.
    if(stack.equals(physicalStacks.get(physical)))continue;
    if(transaction==null)transaction=tc.getConstructor().newInstance();
    call(transaction,"setLayerStack",new Class<?>[]{sc,int.class},physicalContainers.get(physical),stack);
   }
   if(transaction!=null){call(transaction,"apply",new Class<?>[]{});
    for(String physical:physicalContainers.keySet())physicalStacks.put(physical,observed.containsKey(physical)?observed.get(physical):-1);
    log("FROZEN_RETARGET_SUBMITTED "+physicalStacks+"; sampled mapping, not atomic with Android remap");
   }
  }catch(Exception e){log("FROZEN_RETARGET_PENDING "+root(e));}
  finally{if(transaction!=null)try{call(transaction,"close",new Class<?>[]{});}catch(Exception ignored){}}
 }
 /** Optional display screenshot, excluding every layer owned by this bridge. */
 private void freezeFrame(Object transaction,Object mirror,Object container,Object info,int id)throws Exception{
  Object shot=null,buffer=null;long began=SystemClock.elapsedRealtime();
  try{
   int w=info.getClass().getField("logicalWidth").getInt(info),h=info.getClass().getField("logicalHeight").getInt(info);
   Class<?> rect=Class.forName("android.graphics.Rect");Object crop=rect.getConstructor(int.class,int.class,int.class,int.class).newInstance(0,0,w,h);
   Class<?> wmType=Class.forName("android.view.IWindowManager");Object wm=PowerExperiment.service("window","android.view.IWindowManager");
   Object excluded=Array.newInstance(sc,surfaces.size());for(int i=0;i<surfaces.size();i++)Array.set(excluded,i,surfaces.get(i));
   DisplayCaptureApi api=DisplayCaptureApi.resolve(Class::forName,wmType,rect,excluded.getClass());
   Object b=api.constructor.newInstance();call(b,"setSourceCrop",new Class<?>[]{rect},crop);call(b,"setFrameScale",new Class<?>[]{float.class},1f);
   call(b,"setExcludeLayers",new Class<?>[]{excluded.getClass()},excluded);
   log("CAPTURE_BEGIN D"+id+" physical="+info.getClass().getField("uniqueId").get(info)+" backend="+api.name+" timeout=500ms excluded="+surfaces.size());
   try(CaptureCompletion<Object> completion=new CaptureCompletion<>(LiveWindowBridge::releaseShot)){
    Object callback=api.statusCallback?(java.util.function.ObjIntConsumer<Object>)completion::accept:(java.util.function.Consumer<Object>)(item->completion.accept(item,0));
    Object listener=api.listenerConstructor.newInstance(callback);
    try{api.capture.invoke(wm,id,call(b,"build",new Class<?>[]{}),listener);shot=completion.await(500);}
    finally{java.lang.ref.Reference.reachabilityFence(callback);java.lang.ref.Reference.reachabilityFence(listener);}
   }
   buffer=call(shot,"getHardwareBuffer",new Class<?>[]{});
   if(buffer==null)throw new IllegalStateException("No frozen buffer");
   if(Boolean.TRUE.equals(call(shot,"containsSecureLayers",new Class<?>[]{})))throw new IllegalStateException("Capture contains protected layers");
   Object dm=PowerExperiment.service("display","android.hardware.display.IDisplayManager");
   Object after=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,id);
   if(after==null||!sameCaptureDisplay(info,after))throw new IllegalStateException("Physical display/geometry/power changed during capture");
   log("CAPTURE_READY D"+id+" elapsed="+(SystemClock.elapsedRealtime()-began)+"ms; screenshot returned, not app freshness or optical proof");
   int bw=(int)call(buffer,"getWidth",new Class<?>[]{}),bh=(int)call(buffer,"getHeight",new Class<?>[]{});
   if(bw!=w||bh!=h)throw new IllegalStateException("Frozen buffer geometry mismatch");
   Object builder=Class.forName("android.view.SurfaceControl$Builder").getConstructor().newInstance();
   call(builder,"setName",new Class<?>[]{String.class},"FoldPowerLabFrozen_D"+id);
   call(builder,"setBufferSize",new Class<?>[]{int.class,int.class},w,h);
   call(builder,"setHidden",new Class<?>[]{boolean.class},true);
   Object layer=call(builder,"build",new Class<?>[]{});surfaces.add(layer);
   call(transaction,"reparent",new Class<?>[]{sc,sc},layer,container);
   call(transaction,"setLayer",new Class<?>[]{sc,int.class},layer,1);
   call(transaction,"setBuffer",new Class<?>[]{sc,Class.forName("android.hardware.HardwareBuffer")},layer,buffer);
   call(transaction,"setColorSpace",new Class<?>[]{sc,Class.forName("android.graphics.ColorSpace")},layer,call(shot,"getColorSpace",new Class<?>[]{}));
   call(transaction,"show",new Class<?>[]{sc},layer);
   log("FROZEN_FRAME D"+id+" "+w+"x"+h+"; static shield until reveal, capture is not pixel readiness proof");
  }finally{if(buffer!=null)try{call(buffer,"close",new Class<?>[]{});}catch(Exception ignored){}}
 }
 /** Explicit visibility transaction before destruction, to test stale composition/damage. */
 synchronized void reveal(){
  if(closed||revealSubmitted)return;
  revealSubmitted=true;Object transaction=null;
  try{
   transaction=tc.getConstructor().newInstance();
   for(Object surface:surfaces)if(Boolean.TRUE.equals(call(surface,"isValid",new Class<?>[]{}))){
    call(transaction,"setAlpha",new Class<?>[]{sc,float.class},surface,0f);
    call(transaction,"hide",new Class<?>[]{sc},surface);
   }
   addCommit(transaction,()->{
    synchronized(this){
     log("REVEAL_COMMIT closed="+closed+"; mirrors hidden; callback not pixel proof");
     if(!closed&&!watchdog.isShutdown())watchdog.schedule(this::close,250,TimeUnit.MILLISECONDS);
    }
   });
   call(transaction,"apply",new Class<?>[]{});
   log("REVEAL_SUBMITTED alpha=0 hidden; remove 250ms after callback; original hard watchdog remains");
  }catch(Exception e){log("REVEAL_REJECTED "+root(e));close();}
  finally{if(transaction!=null)try{call(transaction,"close",new Class<?>[]{});}catch(Exception ignored){}}
 }
 private void addCommit(Object transaction,Runnable action)throws Exception{
  Class<?> listener=Class.forName("android.view.SurfaceControl$TransactionCommittedListener");
  Object callback=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(proxy,method,args)->{
   if(method.getName().equals("onTransactionCommitted")){action.run();return null;}
   if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
   if(method.getName().equals("equals"))return proxy==args[0];
   return "FoldPowerLabRevealCommit";
  });
  call(transaction,"addTransactionCommittedListener",new Class<?>[]{Executor.class,listener},(Executor)Runnable::run,callback);
 }
 synchronized void close(){
  if(closed)return;committed=false;Object transaction=null;
  try{
   if(!surfaces.isEmpty()){
    transaction=tc.getConstructor().newInstance();
    for(Object surface:surfaces)if(Boolean.TRUE.equals(call(surface,"isValid",new Class<?>[]{})))call(transaction,"remove",new Class<?>[]{sc},surface);
    try{addCommit(transaction,()->log("REMOVE_COMMIT owned layers removed; callback not pixel proof"));}catch(Exception e){log("REMOVE_COMMIT listener unavailable "+root(e));}
    call(transaction,"apply",new Class<?>[]{});
    log("REMOVE_SUBMITTED owned mirrors and containers; not compositor commit/pixel proof");
    for(Object surface:surfaces)try{call(surface,"release",new Class<?>[]{});}catch(Exception e){log("Handle release: "+root(e));}
   }
   surfaces.clear();closed=true;watchdog.shutdownNow();log("CLOSED; no persistent setting changed");
  }catch(Exception e){
   log("REMOVE_PENDING "+root(e));
   if(++cleanupAttempts>=10){log("Removal retries exhausted; terminating own shell worker");android.os.Process.killProcess(android.os.Process.myPid());}
   else if(!watchdog.isShutdown())watchdog.schedule(this::close,200,TimeUnit.MILLISECONDS);
  }finally{if(transaction!=null)try{call(transaction,"close",new Class<?>[]{});}catch(Exception ignored){}}
 }
 private static boolean sameCaptureDisplay(Object before,Object after)throws Exception{
  if(!Objects.equals(before.getClass().getField("uniqueId").get(before),after.getClass().getField("uniqueId").get(after)))return false;
  for(String f:new String[]{"logicalWidth","logicalHeight","rotation","layerStack"})if(before.getClass().getField(f).getInt(before)!=after.getClass().getField(f).getInt(after))return false;
  return after.getClass().getField("state").getInt(after)==2&&after.getClass().getField("committedState").getInt(after)==2;
 }
 private static void releaseShot(Object shot){try{Object b=call(shot,"getHardwareBuffer",new Class<?>[]{});if(b!=null)call(b,"close",new Class<?>[]{});}catch(Exception ignored){}}
 private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
