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
 synchronized boolean probeNative(){return nativeProbe.nativeVisible();}
 private final ContinuityProbePolicy probe=new ContinuityProbePolicy();
 synchronized boolean probeHolding(){return probe.holding();}
 synchronized String probeStatus(){return probe.status+"\n"+nativeProbe.report();}
 // Restore prewarm after alpha.5 showed no benefit from bypassing it.
 private static final boolean CLOSING_PREWARM_ENABLED=true;
 private final ClosingPrewarmPolicy prewarm=new ClosingPrewarmPolicy();
 private Runnable prewarmTimer;
 private void clearPrewarm(){if(delayHandler!=null && prewarmTimer!=null)delayHandler.removeCallbacks(prewarmTimer);prewarmTimer=null;}
 private void prepareCover(){
  changeState(true);
  if(owned==null || !innerHeld)return;
  final Object expected=owned;
  event("PREWARM cover: inner remains primary; bounded "+ClosingPrewarmPolicy.HOLD_MS+" ms; not panel ON proof");
  if(delayHandler==null){HandlerThread thread=new HandlerThread("Duo-closing-release");thread.start();delayHandler=new Handler(thread.getLooper());}
  prewarmTimer=()->{synchronized(CoverHandoff.this){
   if(owned!=expected || !innerHeld)return;
   event("PREWARM expired without handoff; releasing preparation");releaseOwned();
  }};
  delayHandler.postDelayed(prewarmTimer,ClosingPrewarmPolicy.HOLD_MS);
 }
 private float handoffAngle=HandoffSettings.DEFAULT;
 // Let Android complete closure; bound the cover request with the tested one-second fallback.
 private final ClosingReleaseDelay delay=new ClosingReleaseDelay();
 private Handler delayHandler;
 private Runnable delayTimer;
 private boolean timedCoverReleased;
 synchronized boolean takeTimedCoverRelease(){boolean value=timedCoverReleased;timedCoverReleased=false;return value;}
 private void clearDelay(String reason){
  if(delay.active())event("CLOSE_DELAY END reason="+reason+"; age="+(SystemClock.elapsedRealtime()-delay.started())+" ms");
  delay.clear();
  if(delayHandler!=null && delayTimer!=null)delayHandler.removeCallbacks(delayTimer);
  delayTimer=null;
 }
 private boolean deferClosingRelease(float angle,boolean fresh,boolean interactive,boolean direct){
  if(innerHeld || angle!=0 || !fresh || !interactive || !direct)return false;
  long now=SystemClock.elapsedRealtime();
  if(delay.active()){
   if(delay.hold(owned,now))return true;
   clearDelay("deadline reached during angle update");return false;
  }
  if(!delay.arm(owned,now))return false;
  if(delayHandler==null){HandlerThread thread=new HandlerThread("Duo-closing-release");thread.start();delayHandler=new Handler(thread.getLooper());}
  final Object expected=owned;
  event("CLOSE_DELAY ARM; duration="+ClosingReleaseDelay.DURATION_MS+" ms; deadline="+delay.deadline()+"; awaiting Android closure");
  status="Closing hold: awaiting Android release (1000 ms fallback)";
  delayTimer=()->{synchronized(CoverHandoff.this){
   if(!delay.active() || !delay.matches(expected) || owned!=expected)return;
   event("CLOSE_DELAY TIMER; elapsed="+(SystemClock.elapsedRealtime()-delay.started())+" ms; owned=true");
   releaseOwned();
   if(owned==null)timedCoverReleased=true;
  }};
  delayHandler.postDelayed(delayTimer,ClosingReleaseDelay.DURATION_MS);
  return true;
 }
 private float observedAngle=Float.NaN;
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private void event(String message){events.addLast(SystemClock.elapsedRealtime()+": angle="+observedAngle+" "+message);while(events.size()>48)events.removeFirst();}
 synchronized String trace(){return HandoffBaseline.REPORT+"\nClosing prewarm state request: "+(CLOSING_PREWARM_ENABLED?"enabled":"bypassed (alpha experiment)")+"\nCover-close continuity: Android release with 1000 ms fallback; active="+delay.active()+"; deadline="+delay.deadline()+"\nNormal handoff request events (explicit cancel vs Android callback):\n"+String.join("\n",events);}
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
 synchronized void update(float angle,long sampleTime,boolean fresh,boolean interactive,boolean direct,float openThreshold,long probeRequest,float handoffAngle){
  observedAngle=angle;this.handoffAngle=HandoffSettings.angle(handoffAngle);policy.handoff(this.handoffAngle);
  long now=SystemClock.elapsedRealtime();
  if(StaleHandoffGrace.hold(owned!=null,fresh,interactive,direct,angle,openThreshold,sampleTime,now) && !delay.active() && probeRequest==0){
   if(staleTimer==null){
    final Object expected=owned;final int epoch=++staleGeneration;
    event("STALE_HOLD START sampleAge="+(now-sampleTime)+" ms; deadline="+(sampleTime+StaleHandoffGrace.MAX_AGE_MS));
    if(delayHandler==null){HandlerThread thread=new HandlerThread("Duo-closing-release");thread.start();delayHandler=new Handler(thread.getLooper());}
    staleTimer=()->{synchronized(CoverHandoff.this){if(owned==expected && staleTimer!=null && epoch==staleGeneration){event("STALE_HOLD EXPIRED; releasing owned request");releaseOwned();}}};
    delayHandler.postDelayed(staleTimer,Math.max(1,sampleTime+StaleHandoffGrace.MAX_AGE_MS-now));
   }
   return; // Retain ownership only: no stale-angle transitions or new requests.
  }
  clearStale(fresh?"fresh sample":"hold no longer eligible");
  if(delay.active() && (!fresh || !interactive || !direct || !Float.isFinite(angle))){
   event("CLOSE_DELAY ABORT: fresh="+fresh+" interactive="+interactive+" direct="+direct);releaseOwned();return;
  }
  if(delay.active() && angle>0)clearDelay("reopening; normal handoff resumed");
  boolean prepare=prewarm.update(angle,fresh,interactive,CLOSING_PREWARM_ENABLED&&direct&&probeRequest==0,openThreshold,this.handoffAngle);
  int test=probe.update(SystemClock.elapsedRealtime(),probeRequest,angle,fresh,interactive&&direct,owned!=null&&!innerHeld);
  nativeProbe.update(test==ContinuityProbePolicy.HOLD,angle,interactive);
  if(test==ContinuityProbePolicy.HOLD)return;
  if(test==ContinuityProbePolicy.FINISH){event("RELEASE reason=continuity test finished");releaseOwned();return;}
  if(prepare && owned==null){prepareCover();return;}
  if(owned!=null){
   int next=DirectHandoffPolicy.next(innerHeld,angle,fresh,interactive,direct,openThreshold,this.handoffAngle);
   if(next==DirectHandoffPolicy.RELEASE){if(deferClosingRelease(angle,fresh,interactive,direct))return;event("RELEASE decision: fresh="+fresh+" interactive="+interactive+" direct="+direct+" open="+openThreshold);releaseOwned();return;}
   if(next==DirectHandoffPolicy.INNER){changeState(true);policy.reset();return;}
   if(next==DirectHandoffPolicy.COVER){changeState(false);policy.cover=owned!=null;return;}
   if(innerHeld)return;
  }
  if(!fresh||!interactive){if(owned!=null)event("RELEASE reason=stale or not interactive");releaseOwned();return;}
  int action=policy.update(angle,fresh,interactive);
  if(action<0){event("RELEASE reason=cover policy endpoint");releaseOwned();return;}if(action!=1)return;
  changeState(false);
 }
 private Runnable staleTimer;private int staleGeneration;
 private void clearStale(String reason){
  staleGeneration++;
  if(staleTimer!=null){event("STALE_HOLD END "+reason);delayHandler.removeCallbacks(staleTimer);staleTimer=null;}
 }
 private void changeState(boolean toInner){
  clearPrewarm();
  long identity=Binder.clearCallingIdentity();Object previous=owned;boolean previousInner=innerHeld;long decisionAt=SystemClock.elapsedRealtime();
  try{
   init();
   if(toInner && innerId<0)throw new IllegalStateException("Inner concurrent state missing");
   // Query ownership on acquisition only. Replacing our existing request needs no
   // extra device-state round trip; Android still arbitrates the replacement.
   if(owned==null){
    IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
    Object service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
    Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(service);
    Object current=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
    if(!current.getClass().getMethod("getIdentifier").invoke(current).equals(base.getClass().getMethod("getIdentifier").invoke(base)))throw new IllegalStateException("Another display override is active");
   }
   event("REQUEST "+(toInner?"inner":"cover")+" id="+(toInner?innerId:coverId)+" replacingOwned="+(owned!=null));
   Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,toInner?innerId:coverId);
   Object next=builder.getClass().getMethod("build").invoke(builder);
   Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,m,args)->{
    if(m.getName().equals("hashCode"))return System.identityHashCode(proxy);
    if(m.getName().equals("equals"))return proxy==args[0];
    if(m.getName().equals("toString"))return "DuoCoverHandoff";
    if(m.getName().equals("onRequestActivated")){synchronized(this){event("REQUEST ACTIVE callback; request="+(toInner?"inner":"cover")+"; since decision="+(SystemClock.elapsedRealtime()-decisionAt)+" ms; not panel ON proof");}}
    if(m.getName().equals("onRequestCanceled")){synchronized(this){event("ANDROID CANCEL callback; current owned request="+(owned==next)+" request="+(toInner?"inner":"cover"));if(owned==next){clearStale("Android canceled owned request");clearPrewarm();clearDelay("Android canceled owned request");owned=null;innerHeld=false;status="Concurrent handoff request canceled by system";}}}return null;
   });
   owned=next;innerHeld=toInner;long submittedAt=SystemClock.elapsedRealtime();request.invoke(manager,next,(Executor)Runnable::run,callback);
   event("REQUEST returned; preparation="+(submittedAt-decisionAt)+" ms; binder="+(SystemClock.elapsedRealtime()-submittedAt)+" ms; physical remap measured separately");
   status=toInner?"Direct concurrent handoff: inner primary; holding until fully open":"Cover held below handoff; switch at "+Math.round(handoffAngle)+"° or closed";
  }catch(Exception e){owned=previous;innerHeld=previousInner;releaseOwned();Throwable root=e;while(root.getCause()!=null)root=root.getCause();status="Handoff unavailable: "+root.getClass().getSimpleName()+": "+root.getMessage();}
  finally{Binder.restoreCallingIdentity(identity);}
 }
 synchronized boolean innerActive(){return owned!=null && innerHeld;}
 private long pauseToken;
 synchronized void holdPanel(int source,long token){
  clearPrewarm();clearDelay("hinge stationary");clearStale("fresh stationary hinge");
  boolean first=token!=pauseToken;pauseToken=token;
  if(first&&(owned==null||innerHeld!=(source==1)))changeState(source==1);
  if(owned!=null)status="Hinge pause: usable "+(innerHeld?"inner":"cover")+" screen; resume on movement";
 }
 synchronized boolean active(){return owned!=null && !innerHeld;}
 synchronized void release(){prewarm.reset();probe.abort();nativeProbe.update(false,0,false);releaseOwned();}
 private void releaseOwned(){
  clearStale("explicit release");
  clearPrewarm();
  clearDelay("explicit release");policy.reset();if(owned==null)return;long identity=Binder.clearCallingIdentity();
  try{event("EXPLICIT cancelStateRequest; held="+(innerHeld?"inner":"cover"));cancel.invoke(manager);event("EXPLICIT cancelStateRequest returned");owned=null;innerHeld=false;status="Normal display control restored";}catch(Exception e){status="Display release pending";}finally{Binder.restoreCallingIdentity(identity);}
 }
}
