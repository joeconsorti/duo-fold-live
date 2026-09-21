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
 String status="Dual-screen mode ready";
 synchronized boolean active(){return owned!=null;}
 private void init()throws Exception{
  if(manager!=null)return;
  if(!"SM-F971U".equals(Build.MODEL))throw new IllegalStateException("Untested model");
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
 private void begin(boolean inner,long now)throws Exception{
  init();
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
  Object service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
  Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(service);
  Object current=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
  if(!current.getClass().getMethod("getIdentifier").invoke(current).equals(base.getClass().getMethod("getIdentifier").invoke(base)))throw new IllegalStateException("Existing display override; stop other test apps first");
  setConcurrent(inner,now); // Captured outgoing panel becomes the secondary Presentation.
 }
 private void setConcurrent(boolean inner,long now)throws Exception{
  nativeFailure="";nativeRetried=false;primaryInner=contentInner=inner;started=now;readyLostAt=now;endpointSince=0;mappingDeadline=now+1800;
  Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,inner?innerId:outerId);
  Object next=builder.getClass().getMethod("build").invoke(builder);owned=next;
  Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,m,args)->{
   switch(m.getName()){
    case "hashCode":return System.identityHashCode(proxy);
    case "equals":return proxy==args[0];
    case "toString":return "DuoConcurrentRequest";
    case "onRequestCanceled":synchronized(this){if(owned==next){owned=null;blocked=true;status="Concurrent request canceled by Android";}}
   }return null;
  });
  request.invoke(manager,next,(Executor)Runnable::run,callback);
  status="Waiting for second-panel Presentation (state "+(inner?innerId:outerId)+")";
 }
 synchronized void update(float angle,boolean fresh,boolean unlocked,boolean primaryIsInner,boolean secondaryReady,int frozenSource,float openThreshold){
  long token=Binder.clearCallingIdentity();long now=SystemClock.elapsedRealtime();
  try{
   if(!unlocked){releaseInternal();blocked=false;bootstrapUsed=false;bootstrapping=false;return;}
   if(!fresh){
    if(bootstrapping && owned!=null && now-started<3500)return;
    releaseInternal();bootstrapping=false;
    status="Waiting for fresh angle and outgoing capture";
    return;
   }
   bootstrapping=false;
   if(FoldThreshold.endpoint(angle,openThreshold)){
    if(endpointSince==0)endpointSince=now;
    if(angle>=FoldThreshold.sanitize(openThreshold) || now-endpointSince>=350){releaseInternal();blocked=false;}return;
   }
   endpointSince=0;
   if(owned==null){if(!blocked&&FoldThreshold.canStart(angle,openThreshold)){if(FreezePolicy.canSwitch(primaryIsInner,frozenSource))begin(FreezePolicy.targetInner(frozenSource),now);else status="Waiting for outgoing frame before switching displays";}return;}
   if(primaryIsInner!=primaryInner && now<mappingDeadline)return;
   if(primaryIsInner!=primaryInner)throw new IllegalStateException("Primary physical display changed during session");
   // Hold one inner-primary session for the entire overlap; do not move tasks or swap at 90 degrees.
   status=secondaryReady?(primaryInner?"Inner live + frozen cover":"Cover live + frozen inner"):"Waiting for outgoing-panel freeze Presentation";
  }catch(Exception e){Throwable root=e;while(root.getCause()!=null)root=root.getCause();releaseInternal();blocked=true;status="Concurrent mode error: "+root.getMessage();}
  finally{Binder.restoreCallingIdentity(token);}
 }
 private void releaseInternal(){
  if(owned!=null){try{cancel.invoke(manager);owned=null;status="Normal display control restored";}catch(Exception e){status="Display release pending";}}
 }
 synchronized void release(){long token=Binder.clearCallingIdentity();try{releaseInternal();blocked=false;bootstrapUsed=false;bootstrapping=false;}finally{Binder.restoreCallingIdentity(token);}}
}
