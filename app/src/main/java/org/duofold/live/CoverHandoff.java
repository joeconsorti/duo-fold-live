package org.duofold.live;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
/** A process-owned request: Android releases it if this Shizuku process dies. */
final class CoverHandoff {
 private Object manager,owned; private Method cancel,request; private Class<?> requestType,callbackType;
 private int coverId=-1,innerId=-1; private boolean innerHeld=false; private final HandoffPolicy policy=new HandoffPolicy();
 private final ReleaseDeferral deferral=new ReleaseDeferral();
 // Display power only (CloseReleaseGate input); "unlocked" still decides whether to request at all.
 private volatile boolean screenOn=true;
 void screen(boolean on){screenOn=on;}
 private final NativeContinuityProbe nativeProbe=new NativeContinuityProbe();
 synchronized boolean probeNative(){return nativeProbe.nativeVisible();}
 private final ContinuityProbePolicy probe=new ContinuityProbePolicy();
 synchronized boolean probeHolding(){return probe.holding();}
 synchronized String probeStatus(){return probe.status+"\n"+nativeProbe.report();}
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
   // CloseReleaseGate still applies: releasing at base HALF_OPENED goes 5->2->0 (sleep). If it defers, the next
   // poll completes the release once the base state is CLOSED (or reopening, display off, 3 s cap).
   if(gatedRelease(observedAngle,false)){event("CLOSE_DELAY TIMER; release deferred by CloseReleaseGate");return;}
   releaseOwned();
   if(owned==null)timedCoverReleased=true;
  }};
  delayHandler.postDelayed(delayTimer,ClosingReleaseDelay.DURATION_MS);
  return true;
 }
 private float observedAngle=Float.NaN;
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private void event(String message){events.addLast(SystemClock.elapsedRealtime()+": angle="+observedAngle+" "+message);while(events.size()>48)events.removeFirst();}
 synchronized String trace(){return "Cover-close continuity: Android release with 1000 ms fallback; active="+delay.active()+"; deadline="+delay.deadline()+"\nNormal handoff request events (explicit cancel vs Android callback):\n"+String.join("\n",events);}
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
 synchronized void update(float angle,boolean fresh,boolean interactive,boolean direct,float openThreshold,long probeRequest,float handoffAngle){
  observedAngle=angle;this.handoffAngle=HandoffSettings.angle(handoffAngle);policy.handoff(this.handoffAngle);
  if(delay.active() && (!fresh || !interactive || !direct || !Float.isFinite(angle))){
   event("CLOSE_DELAY ABORT: fresh="+fresh+" interactive="+interactive+" direct="+direct);releaseOwned();return;
  }
  if(delay.active() && angle>0)clearDelay("reopening; normal handoff resumed");
  int test=probe.update(SystemClock.elapsedRealtime(),probeRequest,angle,fresh,interactive&&direct,owned!=null&&!innerHeld);
  nativeProbe.update(test==ContinuityProbePolicy.HOLD,angle,interactive);
  if(test==ContinuityProbePolicy.HOLD)return;
  if(test==ContinuityProbePolicy.FINISH){if(gatedRelease(angle,fresh))return;event("RELEASE reason=continuity test finished");releaseOwned();return;}
  if(owned!=null){
   int next=DirectHandoffPolicy.next(innerHeld,angle,fresh,interactive,direct,openThreshold,this.handoffAngle);
   if(next==DirectHandoffPolicy.RELEASE){if(deferClosingRelease(angle,fresh,interactive,direct))return;if(CloseReleaseGate.gates(next,innerHeld)&&gatedRelease(angle,fresh))return;event("RELEASE decision: fresh="+fresh+" interactive="+interactive+" direct="+direct+" open="+openThreshold);releaseOwned();return;}
   if(next==DirectHandoffPolicy.INNER){changeState(true);policy.reset();return;}
   if(next==DirectHandoffPolicy.COVER){changeState(false);policy.cover=owned!=null;return;}
   if(innerHeld)return;
  }
  if(!fresh||!interactive){if(gatedRelease(angle,fresh))return;if(owned!=null)event("RELEASE reason=stale or not interactive");releaseOwned();return;}
  int action=policy.update(angle,fresh,interactive);
  if(action<0){if(gatedRelease(angle,fresh)){policy.cover=true;return;}event("RELEASE reason=cover policy endpoint");releaseOwned();return;}
  deferral.reset();
  if(action!=1)return;
  changeState(false);
 }
 private void changeState(boolean toInner){
  // Every new direct hold starts its own deferral window; a window left over from an earlier close must not let
  // the new cover hold release early.
  deferral.reset();
  long identity=Binder.clearCallingIdentity();Object previous=owned;boolean previousInner=innerHeld;
  try{
   init();
   if(toInner && innerId<0)throw new IllegalStateException("Inner concurrent state missing");
   Object info=BaseDeviceState.info();
   Object current=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
   if(owned==null && !current.getClass().getMethod("getIdentifier").invoke(current).equals(base.getClass().getMethod("getIdentifier").invoke(base)))throw new IllegalStateException("Another display override is active");
   event("REQUEST "+(toInner?"inner":"cover")+" id="+(toInner?innerId:coverId)+" current="+current.getClass().getMethod("getIdentifier").invoke(current)+" base="+base.getClass().getMethod("getIdentifier").invoke(base));
   Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,toInner?innerId:coverId);
   Object next=builder.getClass().getMethod("build").invoke(builder);
   Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,m,args)->{
    if(m.getName().equals("hashCode"))return System.identityHashCode(proxy);
    if(m.getName().equals("equals"))return proxy==args[0];
    if(m.getName().equals("toString"))return "DuoCoverHandoff";
    if(m.getName().equals("onRequestCanceled")){synchronized(this){event("ANDROID CANCEL callback; current owned request="+(owned==next)+" request="+(toInner?"inner":"cover"));if(owned==next){clearDelay("Android canceled owned request");owned=null;innerHeld=false;policy.onCanceled();deferral.reset();status="Concurrent handoff request canceled by system";}}}return null;
   });
   owned=next;innerHeld=toInner;request.invoke(manager,next,(Executor)Runnable::run,callback);
   status=toInner?"Direct concurrent handoff: inner primary; holding until fully open":"Cover held below handoff; switch at "+Math.round(handoffAngle)+"° or closed";
  }catch(Exception e){owned=previous;innerHeld=previousInner;releaseOwned();Throwable root=e;while(root.getCause()!=null)root=root.getCause();status="Handoff unavailable: "+root.getClass().getSimpleName()+": "+root.getMessage();}
  finally{Binder.restoreCallingIdentity(identity);}
 }
 /** True = keep an override for now (CloseReleaseGate). */
 private boolean gatedRelease(float angle,boolean fresh){
  if(owned==null)return false;
  // Reopening = back at or above the configured handoff angle, where the inner panel takes over again.
  boolean reopening=fresh&&Float.isFinite(angle)&&angle>=handoffAngle;
  // Cheap escapes first inside ReleaseDeferral: the base-state query is a system_server call on the poll.
  return deferral.keep(SystemClock.elapsedRealtime(),true,screenOn,reopening,BaseDeviceState::closed);
 }
 /** External release (effect disallowed by the animation mode, dual mode toggled): gated like every other release. */
 synchronized void releaseGated(float angle,boolean fresh){
  if(gatedRelease(angle,fresh))return;
  release();
 }
 /** Reader stop / Shizuku destroy / restart: wait, bounded, for CLOSED before cancelling. */
 synchronized void releaseForTeardown(){
  long start=SystemClock.elapsedRealtime();
  while(CloseReleaseGate.waitBeforeTeardown(owned!=null,BaseDeviceState.closed(),screenOn,SystemClock.elapsedRealtime()-start)){
   try{Thread.sleep(BaseDeviceState.CACHE_MS);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
  }
  release();
 }
 synchronized boolean active(){return owned!=null && !innerHeld;}
 synchronized void release(){probe.abort();nativeProbe.update(false,0,false);releaseOwned();}
 private void releaseOwned(){
  clearDelay("explicit release");policy.reset();if(owned==null)return;long identity=Binder.clearCallingIdentity();
  try{event("EXPLICIT cancelStateRequest; held="+(innerHeld?"inner":"cover"));cancel.invoke(manager);event("EXPLICIT cancelStateRequest returned");owned=null;innerHeld=false;status="Normal display control restored";}catch(Exception e){status="Display release pending";}finally{Binder.restoreCallingIdentity(identity);}
 }
}
