package org.duofold.live;
import android.os.*;
import android.content.ComponentName;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
/** One bounded transition session. All hidden API calls execute under the Shizuku shell identity. */
final class ConcurrentController {
 private Object manager,owned;private Class<?> requestType,callbackType;private Method request,cancel;
 private boolean primaryInner,contentInner,blocked,routeAttempted,bootstrapUsed,bootstrapping;private int innerId=-1,outerId=-1;
 private long started,endpointSince,pendingSince,readyLostAt,mappingDeadline;
 private ComponentName expected;private int destination;private boolean pendingInner;
 private TaskDisplayRouter router;
 private String nativeFailure="";
 private boolean nativeRetried;
 synchronized boolean canMirrorSecondary(){return false;}
 synchronized boolean secondaryHasNativeContent(){return owned!=null && !primaryInner && contentInner;}
 private FixedTaskRoute fixedRoute;private boolean taskRequested,taskVerified,routeUnlocked;private long routePollAt,restoreAt,panelsReadySince;private String fixedSecondary="";
 private boolean cameraSession;private String fixedPrimary="";
 private final ScreenshotWindowTrace windows=new ScreenshotWindowTrace();
 private long afterReleaseAt;private int afterReleaseStage;
 private final ArrayDeque<String> samples=new ArrayDeque<>();
 private long sampledAt;private String panelSample="";private boolean capturedTasks;
 private void log(String text){samples.addLast(SystemClock.elapsedRealtime()+": "+text);while(samples.size()>90)samples.removeFirst();}
 synchronized String trace(){return "Screenshot panel trace (software states, not optical proof):\n"+String.join("\n",samples)+"\n"+windows.report();}
 private void samplePanels(float angle,boolean windowReady,boolean ready,boolean force){
  long now=SystemClock.elapsedRealtime();if(!force&&now-sampledAt<100)return;sampledAt=now;
  try{
   Object dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
   StringBuilder line=new StringBuilder();boolean secondOn=false;
   for(int id=0;id<=1;id++){
    Object d=dm.getClass().getMethod("getDisplayInfo",int.class).invoke(dm,id);
    if(d==null){line.append(" display").append(id).append("=missing");continue;}
    Class<?> c=d.getClass();int state=c.getField("state").getInt(d);if(id==1)secondOn=state==2;
    line.append(" display").append(id).append(" physical=").append(c.getField("uniqueId").get(d)).append(" state=").append(state)
     .append(" size=").append(c.getField("logicalWidth").get(d)).append("x").append(c.getField("logicalHeight").get(d));
   }
   String next=line.toString();
   if(force||!next.equals(panelSample)){panelSample=next;log("angle="+angle+" prepared="+windowReady+" secondaryReady="+ready+next);}
   if(secondOn&&owned!=null&&!capturedTasks){capturedTasks=true;logNativeTasks();windows.capture("secondary ON");}
  }catch(Exception e){if(force)log("Display sample failed: "+e);}
 }
 private void logNativeTasks(){
  try{
   Object atm=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
   for(int display=0;display<=1;display++){
    List<?> roots=(List<?>)Class.forName("android.app.IActivityTaskManager").getMethod("getAllRootTaskInfosOnDisplay",int.class).invoke(atm,display);
    log("Tasks display="+display+" roots="+roots.size());
    for(Object root:roots){
     String config=String.valueOf(root.getClass().getField("configuration").get(root));
     int bounds=config.indexOf("mBounds=");int end=bounds<0?-1:config.indexOf(')',bounds);
     log("D"+display+" task="+root.getClass().getField("taskId").get(root)+" top="+root.getClass().getField("topActivity").get(root)+" "+(end>bounds?config.substring(bounds,end+1):"bounds unavailable"));
    }
   }
  }catch(Exception e){log("Incoming task diagnostics unavailable: "+e);}
 }
 String status="Dual-screen mode ready";
 synchronized boolean active(){return owned!=null;}
 private void init()throws Exception{
  if(manager!=null)return;
  DeviceCompatibility.requireEligible(Build.MODEL, Build.VERSION.SDK_INT);
  Class<?> type=Class.forName("android.hardware.devicestate.DeviceStateManager");
  Object candidate=type.getConstructor().newInstance();
  for(Object state:(List<?>)type.getMethod("getSupportedDeviceStates").invoke(candidate)){
   String name=(String)state.getClass().getMethod("getName").invoke(state);int id=(int)state.getClass().getMethod("getIdentifier").invoke(state);
   if(name.equals("CONCURRENT_INNER_DEFAULT"))innerId=id;if(name.equals("CONCURRENT_OUTER_DEFAULT"))outerId=id;
  }
  if(innerId<0||outerId<0)throw new IllegalStateException("Concurrent states unavailable");
  requestType=Class.forName("android.hardware.devicestate.DeviceStateRequest");callbackType=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
  request=type.getMethod("requestState",requestType,Executor.class,callbackType);cancel=type.getMethod("cancelStateRequest");manager=candidate;
 }
 private String physicalPrimary()throws Exception{return physicalId(0);}
 private String physicalId(int id)throws Exception{
  Object dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  Object info=dm.getClass().getMethod("getDisplayInfo",int.class).invoke(dm,id);
  return String.valueOf(info.getClass().getField("uniqueId").get(info));
 }
 private void begin(boolean inner,long now)throws Exception{
  init();
  if(cameraSession){afterReleaseAt=0;if(fixedRoute==null)fixedRoute=new FixedTaskRoute();log(fixedRoute.prepare());logNativeTasks();windows.capture("before request");}
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
  Object service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
  Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(service);
  Object current=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
  if(!current.getClass().getMethod("getIdentifier").invoke(current).equals(base.getClass().getMethod("getIdentifier").invoke(base)))throw new IllegalStateException("Existing display override; stop other test apps first");
  if(cameraSession){taskRequested=false;taskVerified=false;panelsReadySince=0;routePollAt=0;fixedPrimary=physicalPrimary();fixedSecondary=physicalId(1);capturedTasks=false;log("REQUEST after committed outgoing screenshot; retain physical primary="+fixedPrimary+"; source="+(inner?"inner":"cover"));samplePanels(Float.NaN,true,false,true);}
  setConcurrent(inner,now); // Captured outgoing panel becomes the secondary Presentation.
 }
 private void setConcurrent(boolean inner,long now)throws Exception{
  nativeFailure="";nativeRetried=false;primaryInner=contentInner=inner;started=now;readyLostAt=now;endpointSince=0;mappingDeadline=now+1800;
  Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,inner?innerId:outerId);
  if(cameraSession&&inner)builder.getClass().getMethod("setFlags",int.class).invoke(builder,4);
  Object next=builder.getClass().getMethod("build").invoke(builder);owned=next;
  Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,m,args)->{
   switch(m.getName()){
    case "hashCode":return System.identityHashCode(proxy);
    case "equals":return proxy==args[0];
    case "toString":return "DuoConcurrentRequest";
    case "onRequestCanceled":synchronized(this){if(owned==next){owned=null;blocked=true;status="Concurrent request canceled by Android";if(cameraSession){log(status);samplePanels(Float.NaN,false,false,true);afterReleaseAt=SystemClock.elapsedRealtime();afterReleaseStage=0;windows.capture("Android canceled");}}}
   }return null;
  });
  request.invoke(manager,next,(Executor)Runnable::run,callback);
  status="Waiting for second-panel Presentation (state "+(inner?innerId:outerId)+")";
 }
 private boolean panelsReady()throws Exception{
  Object dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  for(int id=0;id<2;id++){
   Object d=dm.getClass().getMethod("getDisplayInfo",int.class).invoke(dm,id);if(d==null)return false;
   Class<?> c=d.getClass();if(c.getField("state").getInt(d)!=2)return false;
   String physical=String.valueOf(c.getField("uniqueId").get(d));
   if(!physical.equals(id==0?fixedPrimary:fixedSecondary))return false;
   int w=c.getField("logicalWidth").getInt(d),h=c.getField("logicalHeight").getInt(d);
   if(w<=0||h<=0||(Math.min(w,h)/(float)Math.max(w,h)>.7f)!=(id==0?primaryInner:!primaryInner))return false;
  }return true;
 }
 private void returnTask(){
  if(fixedRoute==null||!fixedRoute.pending())return;
  try{log(fixedRoute.restore(routeUnlocked));}catch(Exception e){log("Task return pending: "+e);}
 }
 synchronized void update(float angle,boolean fresh,boolean unlocked,boolean primaryIsInner,boolean secondaryReady,int frozenSource,float openThreshold,boolean cameraStartup,boolean windowPrepared){
  long token=Binder.clearCallingIdentity();long now=SystemClock.elapsedRealtime();
  try{
   routeUnlocked=unlocked;
   if(owned==null&&fixedRoute!=null&&fixedRoute.pending()){
    if(now-restoreAt>=1000){restoreAt=now;returnTask();}
    if(fixedRoute.pending()){status="Waiting to return exact routed app";return;}
   }
   if(afterReleaseAt>0){
    long elapsed=now-afterReleaseAt;
    if(elapsed>=(afterReleaseStage==0?200:1200)){
     log("AFTER RELEASE +"+elapsed+" ms");samplePanels(angle,windowPrepared,secondaryReady,true);logNativeTasks();windows.capture("after release +"+elapsed+"ms");
     if(++afterReleaseStage>=2)afterReleaseAt=0;
    }
   }
   if(owned!=null && cameraSession!=cameraStartup){if(cameraSession)log("RELEASE: screenshot mode changed");releaseInternal();blocked=true;return;}
   if(!unlocked){if(cameraSession&&owned!=null)log("RELEASE: locked, screen off, or host disabled");releaseInternal();blocked=false;bootstrapUsed=false;bootstrapping=false;return;}
   if(!fresh){
    if(bootstrapping && owned!=null && now-started<3500)return;
    if(cameraSession&&owned!=null)log("RELEASE: stale hinge angle");releaseInternal();bootstrapping=false;
    status="Waiting for fresh angle and outgoing capture";
    return;
   }
   bootstrapping=false;
   if(FoldThreshold.endpoint(angle,openThreshold)){
    if(endpointSince==0)endpointSince=now;
    if(angle>=FoldThreshold.sanitize(openThreshold) || now-endpointSince>=350){if(cameraSession&&owned!=null){log("RELEASE: endpoint angle="+angle);samplePanels(angle,windowPrepared,secondaryReady,true);}releaseInternal();blocked=false;}return;
   }
   endpointSince=0;
   if(owned==null){
    if(!blocked&&(cameraStartup?ScreenshotStartupPolicy.mayStart(primaryIsInner,angle,openThreshold):FoldThreshold.canStart(angle,openThreshold))){
     if(cameraStartup){
      if(ScreenshotStartupPolicy.canBegin(primaryIsInner,frozenSource,windowPrepared)){
       cameraSession=true;begin(ScreenshotStartupPolicy.keepInnerPrimary(frozenSource),now);
       status="Window-first screenshot: keeping outgoing primary; waiting for secondary ON";
      }else status="Screenshot startup waiting for outgoing frame COMMIT and attached destination window";
     }else{cameraSession=false;if(FreezePolicy.canSwitch(primaryIsInner,frozenSource))begin(FreezePolicy.targetInner(frozenSource),now);else status="Waiting for outgoing frame before switching displays";}
    }return;
   }
   if(cameraSession){
    samplePanels(angle,windowPrepared,secondaryReady,false);
    if(!fixedPrimary.equals(physicalPrimary()))throw new IllegalStateException("Fixed primary changed during task route");
    boolean ready=windowPrepared&&secondaryReady&&panelsReady();
    if(!ready){panelsReadySince=0;taskVerified=false;if(now-started>2500)throw new IllegalStateException("Fixed panels or screenshot lost readiness");status="Waiting for fixed panels and committed outgoing screenshot";return;}
    if(panelsReadySince==0)panelsReadySince=now;
    if(now-panelsReadySince<120){status="Checking stable fixed-panel readiness";return;}
    if(!fixedRoute.selected()){status="Home/system screen not routed — test inside a fullscreen app";}
    else if(!taskRequested){taskRequested=true;log(fixedRoute.move());windows.capture("app route requested");status="App route requested; waiting for D1 placement and focus";}
    else if(now-routePollAt>=100){
     routePollAt=now;
     if(fixedRoute.placedAndFocused()){
      if(!taskVerified){taskVerified=true;log("TASK PLACED + FOCUSED on D1; fixed panels ON; optical continuity not verified");logNativeTasks();windows.capture("app placed and focused");}
      status="Fixed panels: outgoing screenshot + incoming app (plain diagnostic)";
     }else{taskVerified=false;status="Waiting for selected app placement and focus on D1";if(now-started>3000)throw new IllegalStateException("App did not reach focused D1 task");}
    }
    if(now-started>=30000){log("RELEASE: 30-second limit");releaseInternal();blocked=true;status="Fixed-panel test reached time limit";return;}
    return;
   }
   if(primaryIsInner!=primaryInner && now<mappingDeadline)return;
   if(primaryIsInner!=primaryInner)throw new IllegalStateException("Primary physical display changed during session");
   // Hold one inner-primary session for the entire overlap; do not move tasks or swap at 90 degrees.
   status=secondaryReady?(primaryInner?"Inner live + frozen cover":"Cover live + frozen inner"):"Waiting for outgoing-panel freeze Presentation";
  }catch(Exception e){Throwable root=e;while(root.getCause()!=null)root=root.getCause();if(cameraSession)log("RELEASE: "+root);releaseInternal();blocked=true;status="Concurrent mode error: "+root.getMessage();}
  finally{Binder.restoreCallingIdentity(token);}
 }
 private void releaseInternal(){
  returnTask();
  if(owned!=null){try{if(cameraSession){logNativeTasks();windows.capture("before release");afterReleaseAt=SystemClock.elapsedRealtime();afterReleaseStage=0;}cancel.invoke(manager);owned=null;status="Normal display control restored";}catch(Exception e){status="Display release pending";}}
 }
 synchronized void release(){long token=Binder.clearCallingIdentity();try{if(cameraSession&&owned!=null)log("RELEASE: controller disabled or stopped");releaseInternal();blocked=false;bootstrapUsed=false;bootstrapping=false;}finally{Binder.restoreCallingIdentity(token);}}
}
