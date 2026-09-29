package org.duofold.live;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
/** A process-owned request: Android releases it if this Shizuku process dies. */
final class CoverHandoff {
 private Object manager,owned; private Method cancel,request; private Class<?> requestType,callbackType;
 private int coverId=-1,innerId=-1; private boolean innerHeld=false; private final HandoffPolicy policy=new HandoffPolicy();
 private final NativeContinuityProbe nativeProbe=new NativeContinuityProbe();
 private TaskDisplayRouter liveRouter;
 private boolean liveSession,liveSourceInner,livePlaced,liveCommitted,liveRepair,liveBlocked;
 private long liveRequestedAt,livePlacedAt;
 private float liveLastAngle=Float.NaN;
 private String liveStatus="Developer live handoff idle";
 synchronized boolean probeNative(){return nativeProbe.nativeVisible();}
 synchronized boolean livePreviewBridge(){return liveSession&&owned!=null&&!livePlaced&&!liveCommitted;}
 synchronized boolean liveNativeVisible(){return liveSession&&livePlaced&&!liveCommitted;}
 synchronized String liveReport(){return liveStatus;}
 private final ContinuityProbePolicy probe=new ContinuityProbePolicy();
 synchronized boolean probeHolding(){return probe.holding();}
 synchronized String probeStatus(){return probe.status+"\n"+nativeProbe.report();}
 private float handoffAngle=HandoffSettings.DEFAULT;
 String status="Normal display control";
 private void init()throws Exception{
  if(manager!=null)return;
  DeviceCompatibility.requireEligible(Build.MODEL, Build.VERSION.SDK_INT);
  Class<?> type=Class.forName("android.hardware.devicestate.DeviceStateManager");
  Object candidate=type.getConstructor().newInstance();
  for(Object state:(List<?>)type.getMethod("getSupportedDeviceStates").invoke(candidate)){
   if("CONCURRENT_INNER_DEFAULT".equals(state.getClass().getMethod("getName").invoke(state)))innerId=(int)state.getClass().getMethod("getIdentifier").invoke(state);
   if("CONCURRENT_OUTER_DEFAULT".equals(state.getClass().getMethod("getName").invoke(state)))coverId=(int)state.getClass().getMethod("getIdentifier").invoke(state);
  }
  if(coverId<0)throw new IllegalStateException("Cover state missing");
  requestType=Class.forName("android.hardware.devicestate.DeviceStateRequest");
  callbackType=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
  request=type.getMethod("requestState",requestType,Executor.class,callbackType);cancel=type.getMethod("cancelStateRequest");manager=candidate;
 }
 synchronized void update(float angle,boolean fresh,boolean interactive,boolean direct,boolean primaryInner,float openThreshold,long probeRequest,float handoffAngle,boolean livePreview){
  this.handoffAngle=HandoffSettings.angle(handoffAngle);policy.handoff(this.handoffAngle);
  if(livePreview){
   probe.abort();nativeProbe.update(false,angle,interactive);
   updateLive(angle,fresh,interactive,direct,primaryInner,openThreshold);
   liveLastAngle=angle;
   return;
  }
  if(liveSession)finishLive(interactive,false,"Developer live handoff disabled");
  liveLastAngle=angle;
  int test=probe.update(SystemClock.elapsedRealtime(),probeRequest,angle,fresh,interactive&&direct,owned!=null&&!innerHeld);
  nativeProbe.update(test==ContinuityProbePolicy.HOLD,angle,interactive);
  if(test==ContinuityProbePolicy.HOLD)return;
  if(test==ContinuityProbePolicy.FINISH){releaseOwned();return;}
  if(owned!=null){
   int next=DirectHandoffPolicy.next(innerHeld,angle,fresh,interactive,direct,openThreshold,this.handoffAngle);
   if(next==DirectHandoffPolicy.RELEASE){releaseOwned();return;}
   if(next==DirectHandoffPolicy.INNER){changeState(true);policy.reset();return;}
   if(next==DirectHandoffPolicy.COVER){changeState(false);policy.cover=owned!=null;return;}
   if(innerHeld)return;
  }
  if(!fresh||!interactive){releaseOwned();return;}
  int action=policy.update(angle,fresh,interactive);
  if(action<0){releaseOwned();return;}if(action!=1)return;
  changeState(false);
 }
 private void updateLive(float angle,boolean fresh,boolean interactive,boolean direct,boolean primaryInner,float openThreshold){
  long now=SystemClock.elapsedRealtime();
  if(!fresh||!interactive||!direct||!Float.isFinite(angle)){
   if(liveSession)finishLive(interactive,false,"Developer live handoff paused");
   return;
  }
  float open=FoldThreshold.sanitize(openThreshold);
  boolean endpoint=angle<=0f||angle>=open;
  if(endpoint){
   if(liveSession&&!liveCommitted)finishLive(interactive,false,"Developer live handoff returned to endpoint");
   if(liveCommitted){liveSession=false;liveCommitted=false;livePlaced=false;liveRouter=null;liveStatus="Developer live handoff ready";}
   liveBlocked=false;
   return;
  }
  if(liveBlocked)return;
  float delta=Float.isFinite(liveLastAngle)?angle-liveLastAngle:0f;
  if(!liveSession){
   boolean opening=!primaryInner&&delta>.2f;
   boolean closing=primaryInner&&delta<-.2f;
   if(!opening&&!closing)return;
   liveSession=true;liveSourceInner=primaryInner;livePlaced=false;liveCommitted=false;liveRepair=false;liveRouter=null;liveRequestedAt=livePlacedAt=0;
   changeState(liveSourceInner);
   if(owned==null){liveSession=false;liveStatus="Developer live handoff could not hold outgoing panel: "+status;return;}
   liveStatus="Developer live handoff: "+(liveSourceInner?"inner":"cover")+" stays primary; destination panel awake";
   status=liveStatus;
   return;
  }
  if(liveCommitted)return;
  if(owned==null){finishLive(interactive,false,"Developer live handoff concurrent hold was canceled");return;}
  if(primaryInner!=liveSourceInner&&owned!=null){
   finishLive(interactive,false,"Developer live handoff mapping changed before route");
   return;
  }
  boolean crossed=liveSourceInner?angle<=HandoffSettings.closing(handoffAngle):angle>=handoffAngle;
  if(!crossed)return;
  try{
   if(liveRouter==null){
    liveRouter=new TaskDisplayRouter();liveRequestedAt=now;
    liveStatus="Developer live handoff: "+liveRouter.beginProbe();
    status=liveStatus;
   }
   if(!livePlaced&&liveRouter.probePlaced()){
    livePlaced=true;livePlacedAt=now;
    try{liveStatus="Developer live handoff: native task placed on destination; "+liveRouter.repairProbe();}
    catch(Exception e){liveStatus="Developer live handoff: native task placed; focus request not verified: "+rootMessage(e);}
    status=liveStatus;
   }
   if(!livePlaced&&!liveRepair&&now-liveRequestedAt>=300){
    liveRepair=true;
    try{liveStatus="Developer live handoff: "+liveRouter.repairProbe();}
    catch(Exception e){liveStatus="Developer live handoff: placement repair failed: "+rootMessage(e);}
    status=liveStatus;
   }
   if(!livePlaced&&now-liveRequestedAt>=1200){
    finishLive(interactive,false,"Developer live handoff route timed out; normal mapping restored");
    return;
   }
   if(livePlaced&&now-livePlacedAt>=120){
    liveRouter=null;
    releaseOwned();
    liveCommitted=true;
    liveStatus="Developer live handoff committed: destination task live; outgoing concurrent hold released";
    status=liveStatus;
   }
  }catch(Exception e){finishLive(interactive,false,"Developer live handoff failed: "+rootMessage(e));}
 }
 private String rootMessage(Exception e){Throwable root=e;while(root.getCause()!=null)root=root.getCause();return root.getClass().getSimpleName()+": "+root.getMessage();}
 private void finishLive(boolean interactive,boolean keepTask,String reason){
  if(liveRouter!=null&&!keepTask)try{liveRouter.endProbe(interactive);}catch(Exception ignored){}
  liveRouter=null;liveSession=false;livePlaced=false;liveCommitted=false;liveRepair=false;
  liveBlocked=reason.contains("failed")||reason.contains("timed out")||reason.contains("canceled")||reason.contains("could not hold");
  releaseOwned();liveStatus=reason;status=reason;
 }
 private void changeState(boolean toInner){
  long identity=Binder.clearCallingIdentity();Object previous=owned;boolean previousInner=innerHeld;
  try{
   init();
   if(toInner && innerId<0)throw new IllegalStateException("Inner concurrent state missing");
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
   Object service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(service);
   Object current=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
   if(owned==null && !current.getClass().getMethod("getIdentifier").invoke(current).equals(base.getClass().getMethod("getIdentifier").invoke(base)))throw new IllegalStateException("Another display override is active");
   Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,toInner?innerId:coverId);
   Object next=builder.getClass().getMethod("build").invoke(builder);
   Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,m,args)->{
    if(m.getName().equals("hashCode"))return System.identityHashCode(proxy);
    if(m.getName().equals("equals"))return proxy==args[0];
    if(m.getName().equals("toString"))return "DuoCoverHandoff";
    if(m.getName().equals("onRequestCanceled")){synchronized(this){if(owned==next){owned=null;innerHeld=false;status="Concurrent handoff request canceled by system";}}}return null;
   });
   owned=next;innerHeld=toInner;request.invoke(manager,next,(Executor)Runnable::run,callback);
   status=toInner?"Direct concurrent handoff: inner primary; holding until fully open":"Cover held below handoff; switch at "+Math.round(handoffAngle)+"° or closed";
  }catch(Exception e){owned=previous;innerHeld=previousInner;releaseOwned();Throwable root=e;while(root.getCause()!=null)root=root.getCause();status="Handoff unavailable: "+root.getClass().getSimpleName()+": "+root.getMessage();}
  finally{Binder.restoreCallingIdentity(identity);}
 }
 synchronized boolean active(){return owned!=null && !innerHeld;}
 synchronized void release(){probe.abort();nativeProbe.update(false,0,false);if(liveSession)finishLive(false,false,"Developer live handoff released");else releaseOwned();}
 private void releaseOwned(){
  policy.reset();if(owned==null)return;long identity=Binder.clearCallingIdentity();
  try{cancel.invoke(manager);owned=null;innerHeld=false;status="Normal display control restored";}catch(Exception e){status="Display release pending";}finally{Binder.restoreCallingIdentity(identity);}
 }
}
