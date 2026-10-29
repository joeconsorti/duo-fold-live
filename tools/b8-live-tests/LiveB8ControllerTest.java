package org.duofold.live.experimental.b8;
import java.io.File;
import java.lang.reflect.Method;
import android.os.SystemClock;
public final class LiveB8ControllerTest {
 static boolean captured=true,nativeReady=true,animated=false,nativeImage=true;
 static boolean ack=true,prepared=true,paused=true,commit=true;static Method tick;static File root;static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static LiveB8Controller fresh(){captured=nativeReady=ack=prepared=paused=commit=true;animated=false;nativeImage=true;LiveB8Power.reset();LiveB8Task.moves=LiveB8Task.retargets=LiveB8Task.focuses=0;LiveB8Task.home=false;LiveB8Task.workspace=false;LiveB8Task.primaryUnavailable=false;LiveB8Task.onRestore=null;LiveB8Task.onVerify=null;LiveB8Task.selected="test.app";LiveB8Brightness.begun=0;LiveB8Brightness.failPrepared=false;LiveB8Brightness.pending=false;LiveB8Brightness.onBegin=null;LiveB8Brightness.onPrepare=null;LiveB8Bridge.arms=0;LiveB8Bridge.allowRemoval=true;LiveB8Bridge.allowCommit=true;LiveB8Bridge.failArm=false;android.view.IWindowManager.locked=false;SystemClock.now+=10000;return new LiveB8Controller(root,false);}
 static void step(LiveB8Controller c,float a)throws Exception{SystemClock.now+=25;c.update(true,a,SystemClock.now,172,85);if(ack)c.presentation(c.sceneSession(),prepared,LiveB8Power.panel==1?"inner":"cover",commit?SystemClock.now:0,paused,animated,nativeImage);if(captured)c.captured(c.sceneSession(),c.capturePhysical(),c.sceneRevision());if(nativeReady)c.primaryReady(c.sceneSession(),c.primaryCheck(),c.primaryCheckPhysical(),SystemClock.now);tick.invoke(c);}
 static void run(LiveB8Controller c,float a,int ms)throws Exception{for(int n=0;n<ms;n+=25)step(c,a);}
 static void stop(LiveB8Controller c)throws Exception{c.close();tick.invoke(c);check(!c.ownsRoute(),"cleanup releases ownership");}
 public static void main(String[] args)throws Exception{
  root=new File(args[0]);tick=LiveB8Controller.class.getDeclaredMethod("tick");tick.setAccessible(true);
  LiveB8Controller c=fresh();run(c,179,200);check(!c.ownsRoute()&&LiveB8Power.requests==0,"idle endpoint never starts timed test");
  run(c,140,150);check(c.ownsRoute()&&LiveB8Task.moves==1&&LiveB8Power.panel==1,"early closing prepares destination without promoting primary");
  run(c,60,1700);check(LiveB8Task.moves==1&&LiveB8Power.panel==2&&c.report().contains("HANDOFF_COMPLETE primary=2"),"hinge triggers cover move and verified primary");
  run(c,83,700);check(LiveB8Task.moves==1,"hold band retains cover");
  run(c,120,3000);check(LiveB8Task.moves==2&&LiveB8Power.panel==1&&c.report().contains("HANDOFF_COMPLETE primary=1"),"reverse hinge moves back without manual countdown");
  run(c,180,2500);check(!c.ownsRoute()&&c.report().contains("NATIVE_COMPLETE"),"endpoint protected native release completes");stop(c);
  c=fresh();LiveB8Bridge.allowCommit=false;run(c,140,100);run(c,60,1200);check(LiveB8Power.requests==1&&LiveB8Brightness.begun==0,"never promote before shield commit");run(c,60,6000);check(!c.ownsRoute()&&LiveB8Power.requests==1,"deadline abort does not retry mid-fold");stop(c);
  c=fresh();LiveB8Bridge.failArm=true;run(c,140,100);run(c,60,1500);check(LiveB8Bridge.arms==1&&!c.ownsRoute(),"arm rejection aborts once rather than repeatedly creating shields");stop(c);
  c=fresh();run(c,140,100);SystemClock.now+=2100;tick.invoke(c);check(!c.ownsRoute(),"stale angle releases session without polling renewal");stop(c);
  c=fresh();run(c,140,100);LiveB8Power.cleanupFailures=1;c.update(false,140,SystemClock.now,172,85);tick.invoke(c);check(c.ownsRoute(),"failed cleanup retains ownership against legacy controller");tick.invoke(c);check(!c.ownsRoute(),"cleanup retried and ownership released");stop(c);
  c=fresh();run(c,140,100);android.view.IWindowManager.locked=true;step(c,120);check(!c.ownsRoute(),"lock aborts owned session");stop(c);
  c=fresh();LiveB8Task.selected=null;run(c,140,100);run(c,60,1800);check(LiveB8Task.moves==0&&LiveB8Bridge.arms==0&&!c.ownsRoute(),"unselected app never freezes wallpaper or promotes");stop(c);
  c=fresh();run(c,140,100);LiveB8Bridge.allowCommit=false;run(c,60,500);run(c,120,100);LiveB8Bridge.allowCommit=true;
  run(c,120,5000);check(LiveB8Task.moves==2&&LiveB8Power.panel==1,"rapid reversal serializes pending handoff then follows latest hinge");stop(c);
  c=fresh();run(c,140,100);final LiveB8Controller disabling=c;LiveB8Brightness.onBegin=()->disabling.update(false,60,SystemClock.now,172,85);
  run(c,60,800);check(LiveB8Power.requests==1&&!c.ownsRoute(),"disable during brightness sampling prevents primary request");stop(c);
  c=fresh();LiveB8Brightness.failPrepared=true;run(c,60,1000);check(LiveB8Bridge.arms==0&&!c.ownsRoute(),"brightness failure falls back before shield allocation");stop(c);
  c=fresh();run(c,140,100);run(c,60,1000);int before=LiveB8Bridge.arms;nativeReady=false;nativeImage=false;run(c,120,500);step(c,180);nativeReady=true;nativeImage=true;run(c,180,1500);check(LiveB8Bridge.arms==before+1&&c.report().contains("ENDPOINT_REUSE")&&!c.ownsRoute(),"endpoint reuses in-flight shield without a second capture");stop(c);
  c=fresh();run(c,140,100);LiveB8Bridge.allowRemoval=false;run(c,60,1000);before=LiveB8Bridge.arms;run(c,120,1000);check(LiveB8Bridge.arms==before&&c.ownsRoute(),"no second shield before removal commit");LiveB8Bridge.last.closed=true;LiveB8Bridge.allowRemoval=true;step(c,120);run(c,120,1200);check(LiveB8Power.panel==1,"reversal resumes after confirmed removal");stop(c);
  c=fresh();run(c,140,100);LiveB8Bridge.allowRemoval=false;run(c,60,600);c.update(false,60,SystemClock.now,172,85);
  for(int n=0;n<20;n++){SystemClock.now+=25;tick.invoke(c);}
  check(c.report().split("\\n[0-9]+ STOP enabled=",-1).length-1==1,"pending cleanup preserves initiating evidence without STOP flooding");
  LiveB8Bridge.allowRemoval=true;tick.invoke(c);stop(c);
  c=fresh();run(c,140,100);c.thermal(3);step(c,120);check(!c.ownsRoute()&&c.report().contains("Thermal pause"),"severe thermal state cleans up");stop(c);
  c=fresh();ack=false;run(c,60,1000);check(LiveB8Task.moves==0&&LiveB8Bridge.arms==0,"no app move or shield without scene acknowledgement");stop(c);
  c=fresh();prepared=false;run(c,60,1000);check(LiveB8Task.moves==0,"both panel commits required before task move");stop(c);
  c=fresh();paused=false;run(c,60,1000);check(LiveB8Bridge.arms==0,"shield waits for capture producer to drain");stop(c);
  c=fresh();LiveB8Bridge.allowCommit=false;run(c,60,500);
  check(LiveB8Bridge.arms==1&&LiveB8Power.requests==1,"armed shield still waits for its commit");
  LiveB8Bridge.allowCommit=true;prepared=false;step(c,60);
  check(LiveB8Power.requests==1,"arm commit alone cannot bypass both-panel coverage");
  prepared=true;step(c,60);
  check(LiveB8Power.requests==2,"arm commit and prepared scene promote in one tick without a duplicate hold transaction");stop(c);
  c=fresh();paused=false;run(c,140,150);long old=c.sceneSession();c.presentation(old-1,true,"inner",SystemClock.now,true);ack=false;run(c,60,1000);check(LiveB8Task.moves==0,"prior session acknowledgement cannot authorize move");stop(c);
  c=fresh();commit=false;run(c,60,1800);check(!c.report().contains("HANDOFF_COMPLETE"),"no reveal without a fresh matching primary scene commit");commit=true;run(c,60,300);check(c.report().contains("HANDOFF_COMPLETE"),"matching primary scene commit releases short shield");check(c.sceneSession()>0,"outgoing scene remains after short shield removal");stop(c);
  c=fresh();run(c,60,1500);ack=false;run(c,60,1000);check(!c.ownsRoute()&&c.sceneSession()==0,"lost app scene lease cleans owned route");stop(c);
  c=fresh();run(c,140,31000);check(!c.ownsRoute()&&c.sceneSession()==0,"outgoing hold has independent 30 second lifetime bound");stop(c);
  c=fresh();prepared=false;run(c,60,500);run(c,172,100);check(!c.ownsRoute()&&c.sceneSession()==0&&LiveB8Task.moves==0,"unready opening releases native policy at endpoint without moving app");check(c.report().contains("PREPARATION_INCOMPLETE_AT_ENDPOINT"),"endpoint failure remains in report");stop(c);
  c=fresh();prepared=false;run(c,140,500);run(c,0,100);check(!c.ownsRoute()&&LiveB8Bridge.arms==0,"unready closing releases at endpoint without allocating shield");stop(c);
  c=fresh();run(c,140,150);prepared=false;run(c,140,1000);check(!c.ownsRoute()&&c.report().contains("Presentation commits stalled")&&LiveB8Task.moves==1,"stalled commits release despite fresh acknowledgements");stop(c);
  c=fresh();run(c,140,150);prepared=false;step(c,0);check(c.ownsRoute()&&c.report().contains("NATIVE_FALLBACK presentation stale at endpoint"),"stale endpoint retains coverage while native policy settles");run(c,0,1900);check(!c.ownsRoute(),"stale endpoint fallback is bounded");stop(c);
  c=fresh();run(c,140,150);prepared=false;step(c,172);check(c.ownsRoute()&&c.report().contains("PREPARED_RETURN_TO_SOURCE"),"source return retains coverage until task restoration settles");run(c,172,400);check(!c.ownsRoute(),"verified source return releases coverage");stop(c);
  c=fresh();run(c,140,150);prepared=false;run(c,140,300);prepared=true;run(c,140,100);check(c.ownsRoute(),"brief commit interruption can recover");stop(c);
  c=fresh();LiveB8Brightness.pending=true;run(c,160,800);
  check(LiveB8Task.moves==1&&c.report().contains("DESTINATION_PREPARED")&&LiveB8Power.requests==1,"closing content prepares at 160 degrees while brightness pending");
  run(c,60,500);check(LiveB8Bridge.arms==0&&LiveB8Power.requests==1,"pending brightness still blocks primary switch");
  LiveB8Brightness.pending=false;run(c,60,800);check(c.report().contains("HANDOFF_COMPLETE"),"brightness completion permits prepared handoff");stop(c);
  c=fresh();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=0;LiveB8Brightness.pending=true;run(c,4,800);
  check(LiveB8Task.moves==1&&c.report().contains("DESTINATION_PREPARED")&&LiveB8Power.panel==2,"opening content prepares at 4 degrees independently of brightness");stop(c);
  c=fresh();LiveB8Brightness.pending=true;run(c,60,4000);check(!c.ownsRoute()&&c.report().contains("NATIVE_FALLBACK brightness preparation timed out")&&LiveB8Bridge.arms==0,"pending brightness has bounded native fallback without shield allocation");stop(c);
  c=fresh();LiveB8Brightness.failPrepared=true;nativeReady=false;run(c,160,800);step(c,60);
  check(c.sceneSession()>0&&c.report().contains("NATIVE_FALLBACK")&&LiveB8Power.requests==1,"brightness failure retains presentation and restores native policy without promotion");
  run(c,60,1900);check(!c.ownsRoute(),"brightness fallback bounded when app never becomes ready");stop(c);
  c=fresh();captured=false;run(c,60,1800);check(LiveB8Task.moves==1&&LiveB8Bridge.arms==0,"destination native screenshot required before shield");captured=true;run(c,60,800);check(c.report().contains("HANDOFF_COMPLETE"),"captured destination permits shield");stop(c);
  c=fresh();run(c,119,800);check(LiveB8Task.moves==1&&LiveB8Bridge.arms==0&&LiveB8Power.panel==1,"early close preparation does not promote before hinge handoff");run(c,172,100);check(c.ownsRoute(),"return to source waits under coverage for native app");run(c,172,400);check(!c.ownsRoute(),"verified return to source cleans prepared task");stop(c);
  c=fresh();nativeReady=false;run(c,60,1800);check(c.primaryCheck()>0&&!c.report().contains("HANDOFF_COMPLETE"),"HAS_DRAWN gate required despite presentation commit");
  long token=c.primaryCheck();c.primaryReady(c.sceneSession()-1,token,c.primaryCheckPhysical(),SystemClock.now);step(c,60);check(!c.report().contains("HANDOFF_COMPLETE"),"wrong session rejected");
  c.primaryReady(c.sceneSession(),token-1,c.primaryCheckPhysical(),SystemClock.now);step(c,60);check(!c.report().contains("HANDOFF_COMPLETE"),"wrong check rejected");
  c.primaryReady(c.sceneSession(),token,"wrong",SystemClock.now);step(c,60);check(!c.report().contains("HANDOFF_COMPLETE"),"wrong physical panel rejected");
  c.primaryReady(c.sceneSession(),token,c.primaryCheckPhysical(),SystemClock.now-300);step(c,60);check(!c.report().contains("HANDOFF_COMPLETE"),"stale readiness rejected");
  nativeReady=true;run(c,60,250);check(c.report().contains("HANDOFF_COMPLETE"),"fresh exact readiness permits reveal");stop(c);
  c=fresh();nativeReady=false;run(c,60,1800);long firstCheck=c.primaryCheck();
  check(firstCheck>0&&LiveB8Task.focuses==1,"first verification focuses once");
  LiveB8Task.primaryUnavailable=true;step(c,60);check(c.primaryCheck()==0,"mapping loss invalidates readiness");
  LiveB8Task.primaryUnavailable=false;run(c,60,150);
  check(c.primaryCheck()>firstCheck&&LiveB8Task.focuses==1,"reverification renews check without repeated focus mutation");
  c.primaryReady(c.sceneSession(),firstCheck,c.primaryCheckPhysical(),SystemClock.now);step(c,60);
  check(!c.report().contains("HANDOFF_COMPLETE"),"pre-reset acknowledgement cannot reveal");
  nativeReady=true;run(c,60,250);check(c.report().contains("HANDOFF_COMPLETE"),"new mapping requires fresh readiness");stop(c);
  c=fresh();nativeReady=false;run(c,60,1200);long canceledCheck=c.primaryCheck();
  java.lang.reflect.Field pf=LiveB8Controller.class.getDeclaredField("power");pf.setAccessible(true);
  ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;int requestCount=LiveB8Power.requests;
  step(c,0);check(c.sceneSession()>0&&LiveB8Bridge.last.closed,"cancel retains plain app presentation after early shield removal");
  run(c,0,150);check(c.primaryCheck()>canceledCheck,"cancel requires a new readiness check");
  c.primaryReady(c.sceneSession(),canceledCheck,c.primaryCheckPhysical(),SystemClock.now);step(c,0);
  check(c.ownsRoute(),"pre-cancel readiness cannot release shield");
  nativeReady=true;run(c,0,250);check(!c.ownsRoute()&&c.report().contains("CANCEL_NATIVE_READY"),"native readiness releases cancellation coverage");
  check(LiveB8Power.requests==requestCount,"cancellation never reasserts concurrent power");stop(c);
  c=fresh();nativeReady=false;run(c,60,1200);((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;
  run(c,0,600);check(c.ownsRoute(),"cancel coverage survives the old 500ms bound while refresh settles");run(c,0,1300);check(!c.ownsRoute()&&c.report().contains("CANCEL_COVERAGE_TIMEOUT"),"cancel coverage has bounded fallback without native readiness");stop(c);
  c=fresh();nativeReady=false;run(c,60,1200);((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;
  step(c,0);android.view.IWindowManager.locked=true;step(c,0);check(!c.ownsRoute(),"lock immediately terminates cancellation coverage");stop(c);
  c=fresh();run(c,60,1500);check(LiveB8Bridge.last.closed,"normal handoff removed shield before cancellation");
  nativeReady=false;((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;requestCount=LiveB8Power.requests;
  step(c,0);check(c.sceneSession()>0,"phase 2 cancellation retains presentation even without shield");
  run(c,0,150);nativeReady=true;run(c,0,250);
  check(!c.ownsRoute()&&c.report().contains("CANCEL_NATIVE_READY")&&LiveB8Power.requests==requestCount,"presentation-only cancellation releases on fresh readiness without power reassertion");stop(c);
  c=fresh();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=0;run(c,120,1500);
  check(c.report().contains("HANDOFF_COMPLETE primary=1"),"opening handoff completed before outgoing panel disappears");
  LiveB8Power.base=3;prepared=false;nativeReady=false;before=LiveB8Bridge.arms;requestCount=LiveB8Power.requests;
  step(c,180);check(c.sceneSession()>0&&c.report().contains("NATIVE_FALLBACK presentation stale at endpoint"),"lost secondary commit does not immediately remove primary coverage");
  run(c,180,150);check(c.ownsRoute(),"endpoint retains coverage until new native readiness");
  nativeReady=true;run(c,180,250);check(!c.ownsRoute()&&c.report().contains("CANCEL_NATIVE_READY"),"fresh primary commit and app readiness complete fallback without secondary commit");
  check(LiveB8Bridge.arms==before&&LiveB8Power.requests==requestCount,"endpoint fallback allocates no new shield and does not reassert state");stop(c);
  // Replay alpha39: native policy already OPENED while physical D0 is still cover.
  c=fresh();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=0;LiveB8Brightness.failPrepared=true;
  run(c,30,300);LiveB8Power.base=3;LiveB8Power.deferRemap=true;
  run(c,179,450);check(c.ownsRoute()&&!c.report().contains("CANCEL_NATIVE_READY"),"old cover cannot release opening endpoint coverage after brightness failure");
  requestCount=LiveB8Power.requests;LiveB8Power.panel=1;
  run(c,179,450);check(!c.ownsRoute()&&c.report().contains("CANCEL_NATIVE_READY"),"fallback waits for physical inner remap and repeated readiness");
  check(LiveB8Power.requests==requestCount,"fallback never reasserts concurrent request");stop(c);
  c=fresh();nativeReady=false;run(c,60,1000);
  c.primaryReady(c.sceneSession(),c.primaryCheck(),c.primaryCheckPhysical(),SystemClock.now);run(c,60,125);
  check(!c.report().contains("HANDOFF_COMPLETE"),"one HAS_DRAWN acknowledgement cannot authorize reveal by aging");
  c.primaryReady(c.sceneSession(),c.primaryCheck(),c.primaryCheckPhysical(),0);step(c,60);
  nativeReady=true;run(c,60,75);check(!c.report().contains("HANDOFF_COMPLETE"),"readiness interruption resets successful observation span");
  run(c,60,200);check(c.report().contains("HANDOFF_COMPLETE"),"stable repeated observations permit reveal");stop(c);
  // alpha49 replay: full close was observed during cancellation recovery, then
  // reopening began before native readiness/cleanup. Do not lose that endpoint.
  for(int source:new int[]{1,2})for(boolean pendingCleanup:new boolean[]{false,true}){
   c=fresh();LiveB8Power.panel=source;LiveB8Power.base=LiveB8Power.current=source==1?3:0;
   float middle=source==1?60:120,end=source==1?0:180,departed=source==1?12:160;
   nativeReady=false;run(c,middle,1000);long canceledSession=c.sceneSession();
   ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=source==1?0:3;
   requestCount=LiveB8Power.requests;step(c,end);run(c,departed,200);
   check(c.sceneSession()==canceledSession&&LiveB8Power.requests==requestCount,"reopening cannot reassert power before recovery readiness");
   if(pendingCleanup)LiveB8Power.cleanupFailures=2;
   nativeReady=true;
   for(int n=0;n<20&&!c.report().contains("CANCEL_NATIVE_READY");n++)step(c,departed);
   check(c.report().contains("CANCEL_NATIVE_READY"),"native recovery reaches verified completion after departure from endpoint");
   if(pendingCleanup){
    check(c.ownsRoute()&&c.sceneSession()==canceledSession,"pending power cleanup keeps old scene and ownership");
    step(c,departed);check(c.sceneSession()==canceledSession&&LiveB8Power.requests==requestCount,"new fold waits for the remaining owned power cleanup");
    step(c,departed);
   }
   check(!c.ownsRoute()&&LiveB8Power.requests==requestCount,"successful recovery releases all ownership before next request");
   step(c,departed);
   check(c.ownsRoute()&&c.sceneSession()!=canceledSession&&LiveB8Power.requests==requestCount+1,"observed endpoint rearms next fold even after hinge already reopened");stop(c);
  }
  c=fresh();nativeReady=false;run(c,60,1000);
  ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;
  requestCount=LiveB8Power.requests;run(c,60,200);nativeReady=true;run(c,60,400);
  check(!c.ownsRoute()&&LiveB8Power.requests==requestCount,"verified mid-fold cancellation cannot restart without an observed endpoint");
  step(c,0);step(c,12);check(c.ownsRoute(),"a later fresh endpoint rearms a blocked route");
  // That endpoint belongs to the previous recovery, not this new session.
  run(c,120,1200);nativeReady=false;((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=3;
  requestCount=LiveB8Power.requests;run(c,120,200);nativeReady=true;run(c,120,400);
  check(!c.ownsRoute()&&LiveB8Power.requests==requestCount,"endpoint evidence cannot leak into a later session's cancellation");stop(c);
  c=fresh();nativeReady=false;run(c,60,1000);
  ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;
  requestCount=LiveB8Power.requests;step(c,0);run(c,12,1900);nativeReady=true;run(c,24,400);
  check(!c.ownsRoute()&&LiveB8Power.requests==requestCount&&c.report().contains("CANCEL_COVERAGE_TIMEOUT"),"timed-out recovery cannot reuse an earlier endpoint to reassert power");stop(c);
  c=fresh();nativeReady=false;run(c,60,1000);
  ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;
  requestCount=LiveB8Power.requests;run(c,60,200);nativeReady=true;LiveB8Power.cleanupFailures=3;
  for(int n=0;n<20&&!c.report().contains("CANCEL_NATIVE_READY");n++)step(c,60);
  check(c.ownsRoute(),"verified mid-fold recovery can still own pending cleanup");
  step(c,0);step(c,12);check(c.ownsRoute()&&LiveB8Power.requests==requestCount,"endpoint during cleanup does not authorize an overlapping request");
  step(c,16);check(!c.ownsRoute(),"cleanup releases before using its observed endpoint");
  step(c,20);check(c.ownsRoute()&&LiveB8Power.requests==requestCount+1,"endpoint observed only during cleanup is preserved for next motion");stop(c);
  c=fresh();run(c,140,800);nativeReady=false;requestCount=LiveB8Power.requests;
  final LiveB8Controller restoringAtEndpoint=c;
  LiveB8Task.onRestore=()->restoringAtEndpoint.update(true,160,SystemClock.now,172,85);
  step(c,180);LiveB8Task.onRestore=null;run(c,160,200);nativeReady=true;
  for(int n=0;n<20&&!c.report().contains("CANCEL_NATIVE_READY");n++)step(c,160);
  check(!c.ownsRoute()&&LiveB8Power.requests==requestCount,"source-return fallback releases before rapid departure can start a new leg");
  step(c,140);check(c.ownsRoute()&&LiveB8Power.requests==requestCount+1,"endpoint at fallback entry is remembered if departure precedes the next tick");stop(c);
  // alpha49: plain native-panel frames are opaque coverage too; animation
  // skipping must not retain an unnecessary shield until native readiness.
  c=fresh();nativeReady=false;run(c,60,1200);
  check(c.report().contains("PRESENTATION_UNMASK_SUBMITTED")&&c.report().contains("primaryFrame=plain"),"plain native coverage explicitly reports early shield removal");
  check(LiveB8Bridge.last.closed&&c.ownsRoute()&&!c.report().contains("HANDOFF_COMPLETE"),"plain presentation remains owned until native readiness");
  nativeReady=true;run(c,60,400);check(c.report().contains("HANDOFF_COMPLETE"),"plain coverage still completes on native readiness");stop(c);
  // Removing the animation-only condition must preserve every coverage guard.
  c=fresh();nativeReady=false;nativeImage=false;commit=false;run(c,60,1000);
  check(!LiveB8Bridge.last.closed&&c.report().contains("commitAgeMs=-1"),"plain placeholder retains shield and missing commit age stays unmeasured");
  commit=true;run(c,60,100);check(!LiveB8Bridge.last.closed,"fresh plain placeholder still cannot uncover destination");
  ack=false;
  c.presentation(c.sceneSession(),true,"inner",SystemClock.now,true,false,true);step(c,60);check(!LiveB8Bridge.last.closed,"wrong physical plain frame cannot unmask");
  c.presentation(c.sceneSession(),true,"cover",SystemClock.now-300,true,false,true);step(c,60);check(!LiveB8Bridge.last.closed,"stale plain frame cannot unmask");
  c.presentation(c.sceneSession(),true,"cover",SystemClock.now+200,true,false,true);step(c,60);check(!LiveB8Bridge.last.closed,"future plain frame cannot unmask");
  c.presentation(c.sceneSession(),false,"cover",SystemClock.now,true,false,true);step(c,60);check(!LiveB8Bridge.last.closed,"mid-fold plain coverage still needs both panels");
  ack=true;nativeImage=true;LiveB8Power.reportedState=1;step(c,60);check(!LiveB8Bridge.last.closed,"plain coverage cannot unmask an OFF primary");
  LiveB8Power.reportedState=2;LiveB8Power.reportedCommittedState=1;step(c,60);check(!LiveB8Bridge.last.closed,"plain coverage requires committed ON state");
  LiveB8Power.reportedCommittedState=2;step(c,180);check(!LiveB8Bridge.last.closed,"plain opening endpoint rejects a cover primary");
  step(c,60);check(LiveB8Bridge.last.closed&&c.ownsRoute()&&!c.report().contains("HANDOFF_COMPLETE"),"valid plain coverage removes only the shield after rejected evidence");stop(c);
  c=fresh();nativeReady=false;nativeImage=false;run(c,60,1000);ack=false;
  c.presentation(c.sceneSession()-1,true,"cover",SystemClock.now,true,false,true);step(c,60);
  check(!c.report().contains("PRESENTATION_UNMASK_SUBMITTED")&&!c.ownsRoute(),"prior-session plain frame cannot authorize early unmask");stop(c);
  // alpha44: display queries can finish after a newer scene commit arrives.
  c=fresh();animated=true;nativeReady=false;nativeImage=false;run(c,60,1000);
  long beforeDisplayQuery=SystemClock.now;SystemClock.now+=80;
  c.presentation(c.sceneSession(),true,"cover",SystemClock.now,true,true,true);
  java.lang.reflect.Field inputField=LiveB8Controller.class.getDeclaredField("input");inputField.setAccessible(true);
  Object latestInput=inputField.get(c);
  Method unmask=LiveB8Controller.class.getDeclaredMethod("unmaskPresentation",long.class,latestInput.getClass());unmask.setAccessible(true);
  unmask.invoke(c,beforeDisplayQuery,latestInput);
  check(LiveB8Bridge.last.closed,"commit arriving during display query is not rejected as future-dated");
  check(!c.report().contains("HANDOFF_COMPLETE"),"fresh clock does not bypass native readiness");stop(c);
  c=fresh();animated=true;nativeReady=false;nativeImage=false;run(c,60,1000);
  c.presentation(c.sceneSession(),true,"cover",SystemClock.now+100,true,true,true);
  unmask.invoke(c,SystemClock.now,inputField.get(c));
  check(!LiveB8Bridge.last.closed,"actually future-dated commits remain rejected");stop(c);
  // alpha41 replay: shaders commit while a native window/layout is still settling.
  for(boolean animate:new boolean[]{false,true})for(int primary:new int[]{1,2}){
   c=fresh();animated=animate;nativeReady=false;LiveB8Power.panel=primary;LiveB8Power.base=LiveB8Power.current=primary==1?3:0;
   float angle=primary==1?60:120;run(c,angle,1200);
   check(LiveB8Bridge.last.closed&&c.report().contains("PRESENTATION_UNMASK_SUBMITTED"),"both directions remove frozen shield before native window readiness");
   check(c.sceneSession()>0&&c.ownsRoute()&&!c.report().contains("HANDOFF_COMPLETE"),"app coverage stays owned while native app is unready");
   int arms=LiveB8Bridge.arms,moves=LiveB8Task.moves;run(c,primary==1?120:60,600);
   check(LiveB8Bridge.arms==arms&&LiveB8Task.moves==moves,"reversal cannot start another transfer during native settlement");
   nativeReady=true;run(c,angle,400);check(c.report().contains("HANDOFF_COMPLETE"),"native readiness completes after shield already removed");stop(c);
  }
  c=fresh();animated=true;nativeReady=false;LiveB8Task.primaryUnavailable=true;run(c,60,1200);
  check(LiveB8Bridge.last.closed&&c.sceneSession()>0&&c.primaryCheck()==0,"native task configuration lag cannot freeze coherent animated app coverage");
  LiveB8Task.primaryUnavailable=false;nativeReady=true;run(c,60,500);check(c.report().contains("HANDOFF_COMPLETE"),"layout guards still required to complete handoff");stop(c);
  c=fresh();animated=true;nativeReady=false;nativeImage=false;run(c,60,1200);
  check(!LiveB8Bridge.last.closed&&!c.report().contains("PRESENTATION_UNMASK_SUBMITTED"),"source placeholder cannot uncover destination shield");
  nativeImage=true;commit=false;run(c,60,300);check(!LiveB8Bridge.last.closed,"missing post-remap commit cannot uncover shield");
  commit=true;step(c,60);check(LiveB8Bridge.last.closed&&c.sceneSession()>0,"matching native image and fresh commit resume presentation");stop(c);
  c=fresh();animated=true;nativeReady=false;run(c,60,1200);((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;requestCount=LiveB8Power.requests;
  run(c,0,300);check(c.sceneSession()>0&&LiveB8Bridge.last.closed,"endpoint cancellation retains animated coverage after early unmask");
  nativeReady=true;run(c,0,300);check(!c.ownsRoute()&&LiveB8Power.requests==requestCount,"fresh endpoint readiness releases coverage without reasserting power");stop(c);
  c=fresh();animated=true;nativeReady=false;run(c,60,1200);nativeImage=false;ack=false;
  c.presentation(c.sceneSession(),true,"inner",SystemClock.now,true,true,true);step(c,60);
  check(!c.report().contains("HANDOFF_COMPLETE"),"wrong panel acknowledgement cannot complete early-unmasked handoff");
  run(c,60,800);check(!c.ownsRoute(),"loss of presentation lease after unmask terminates owned route");stop(c);
  c=fresh();animated=true;nativeReady=false;LiveB8Bridge.allowRemoval=false;run(c,60,1200);
  check(c.report().contains("PRESENTATION_UNMASK_SUBMITTED")&&!LiveB8Bridge.last.closed,"unmask submission is not removal proof");
  nativeReady=true;run(c,60,300);check(!c.report().contains("HANDOFF_COMPLETE"),"handoff still waits for shield removal acknowledgement");
  LiveB8Bridge.last.closed=true;LiveB8Bridge.allowRemoval=true;step(c,60);check(c.report().contains("HANDOFF_COMPLETE"),"confirmed removal completes early-unmasked handoff");stop(c);
  c=fresh();animated=true;nativeReady=false;nativeImage=false;run(c,60,1000);ack=false;
  c.presentation(c.sceneSession(),true,"inner",SystemClock.now,true,true,true);step(c,60);check(!LiveB8Bridge.last.closed,"wrong physical scene cannot unmask incoming shield");
  c.presentation(c.sceneSession(),true,"cover",SystemClock.now-300,true,true,true);step(c,60);check(!LiveB8Bridge.last.closed,"stale scene cannot unmask incoming shield");
  c.presentation(c.sceneSession(),false,"cover",SystemClock.now,true,true,true);step(c,60);check(!LiveB8Bridge.last.closed,"middle handoff requires both panel composites before unmask");
  ack=true;nativeImage=true;step(c,60);check(LiveB8Bridge.last.closed,"current coherent coverage permits unmask after rejected acknowledgements");stop(c);
  c=fresh();animated=true;nativeReady=false;nativeImage=false;run(c,60,1000);ack=false;
  c.presentation(c.sceneSession()-1,true,"cover",SystemClock.now,true,true,true);step(c,60);
  check(!c.report().contains("PRESENTATION_UNMASK_SUBMITTED")&&!c.ownsRoute(),"old session aborts lost lease without authorizing animated coverage");stop(c);
  c=fresh();animated=true;nativeReady=false;run(c,60,1200);LiveB8Power.base=0;step(c,0);
  check(c.sceneSession()>0&&c.report().contains("ENDPOINT_RELEASE retained app coverage"),"endpoint after early unmask continues animated coverage without recapture");
  nativeReady=true;run(c,0,400);check(!c.ownsRoute()&&c.report().contains("NATIVE_COMPLETE"),"endpoint release after early unmask finishes only on native readiness");stop(c);
  // alpha42 replay: after HANDOFF_COMPLETE, endpoint policy release must not
  // create a second frozen shield over the already committed native animation.
  for(boolean animate:new boolean[]{false,true})for(int primary:new int[]{1,2}){
   c=fresh();animated=animate;LiveB8Power.panel=primary;LiveB8Power.base=LiveB8Power.current=primary==1?3:0;
   float middle=primary==1?60:120,end=primary==1?0:180;
   run(c,middle,1500);check(c.report().contains("HANDOFF_COMPLETE primary="+(3-primary)),"animated destination handoff completes before endpoint");
   int arms=LiveB8Bridge.arms,moves=LiveB8Task.moves,writers=LiveB8Brightness.begun,requests=LiveB8Power.requests;
   java.lang.reflect.Field checkField=LiveB8Controller.class.getDeclaredField("primaryCheck");checkField.setAccessible(true);
   long priorCheck=checkField.getLong(c);LiveB8Power.base=primary==1?0:3;nativeReady=false;
   LiveB8Task.primaryUnavailable=true;step(c,end);run(c,end,200);
   check(c.report().contains("ENDPOINT_RELEASE_PRESENTATION")&&c.ownsRoute()&&!c.report().contains("ABORT"),"endpoint retains animation while native task configuration lags");
   check(LiveB8Bridge.arms==arms&&LiveB8Task.moves==moves&&LiveB8Brightness.begun==writers&&LiveB8Power.requests==requests,"endpoint release adds no shield, task transfer, brightness writer or concurrent request");
   LiveB8Task.primaryUnavailable=false;run(c,end,150);
   check(c.primaryCheck()>priorCheck&&c.ownsRoute(),"endpoint requires a fresh native readiness check under retained animation");
   c.primaryReady(c.sceneSession(),priorCheck,c.primaryCheckPhysical(),SystemClock.now);step(c,end);
   check(!c.report().contains("NATIVE_COMPLETE"),"old handoff readiness cannot complete endpoint release");
   nativeReady=true;run(c,end,350);
   check(!c.ownsRoute()&&c.report().contains("NATIVE_COMPLETE")&&LiveB8Bridge.arms==arms,"fresh endpoint readiness cleans up without recapture in both directions");stop(c);
  }
  c=fresh();animated=true;run(c,60,1500);LiveB8Power.base=0;before=LiveB8Bridge.arms;nativeReady=false;step(c,0);
  run(c,120,150);check(LiveB8Bridge.arms==before&&c.ownsRoute(),"reversal waits for pending animated endpoint release without another shield");
  nativeReady=true;run(c,0,350);check(!c.ownsRoute(),"animated endpoint release still completes after reversal");stop(c);
  for(boolean placeholder:new boolean[]{true,false}){
   c=fresh();animated=true;run(c,60,1500);LiveB8Power.base=0;before=LiveB8Bridge.arms;
   if(placeholder)nativeImage=false;else commit=false;
   step(c,0);check(!c.report().contains("ENDPOINT_RELEASE_PRESENTATION"),"placeholder or missing primary commit cannot authorize shield-free endpoint");
   run(c,0,150);check(LiveB8Bridge.arms==before+1,"unverified animated coverage retains existing protected endpoint path");stop(c);
  }
  c=fresh();run(c,60,1500);LiveB8Power.base=0;before=LiveB8Bridge.arms;run(c,0,150);
  check(LiveB8Bridge.arms==before&&c.report().contains("ENDPOINT_RELEASE_PRESENTATION target=2 primaryFrame=plain"),"verified plain presentation avoids a duplicate endpoint shield");stop(c);
  c=fresh();animated=true;run(c,60,1500);LiveB8Power.base=0;nativeReady=false;step(c,0);run(c,0,6500);
  check(!c.ownsRoute()&&c.report().contains("Transition readiness timeout"),"shield-free endpoint retains a bounded native-readiness deadline");stop(c);
  // Successful window checks may be 400ms apart; a stale sample cannot release,
  // but a pending result must not erase the previous bounded successful sample.
  c=fresh();nativeReady=false;run(c,60,1000);token=c.primaryCheck();String physical=c.primaryCheckPhysical();
  c.primaryReady(c.sceneSession(),token,physical,SystemClock.now);step(c,60);
  run(c,60,375);check(!c.report().contains("HANDOFF_COMPLETE"),"expired first observation never releases by itself");
  c.primaryReady(c.sceneSession(),token,physical,SystemClock.now);step(c,60);
  check(c.report().contains("HANDOFF_COMPLETE"),"fresh second success after a normal query gap completes the streak");stop(c);
  c=fresh();nativeReady=false;run(c,60,1000);token=c.primaryCheck();physical=c.primaryCheckPhysical();
  long began=SystemClock.now;run(c,60,400);c.primaryReady(c.sceneSession(),token,physical,SystemClock.now,began);step(c,60);
  began=SystemClock.now;run(c,60,400);c.primaryReady(c.sceneSession(),token,physical,SystemClock.now,began);step(c,60);
  check(c.report().contains("HANDOFF_COMPLETE"),"bounded slow queries are aged from worker completion while start remains post-verification");stop(c);
  c=fresh();nativeReady=false;run(c,60,1000);token=c.primaryCheck();physical=c.primaryCheckPhysical();
  c.primaryReady(c.sceneSession(),token,physical,SystemClock.now);step(c,60);run(c,60,800);
  c.primaryReady(c.sceneSession(),token,physical,SystemClock.now);step(c,60);check(!c.report().contains("HANDOFF_COMPLETE"),"long observation gap starts a new streak");
  c.primaryReady(c.sceneSession(),token,physical,SystemClock.now,SystemClock.now-800);step(c,60);check(!c.report().contains("HANDOFF_COMPLETE"),"overlong query cannot report a fresh success");
  nativeReady=true;run(c,60,75);check(!c.report().contains("HANDOFF_COMPLETE"),"overlong query reset prevents reuse of prior streak");
  run(c,60,200);check(c.report().contains("HANDOFF_COMPLETE"),"fresh repeated observations recover after overlong query");stop(c);
  check(!LiveB8Policy.nativePanelMatches(0,172,"inner","inner","cover"),"closing endpoint rejects old inner");
  check(LiveB8Policy.nativePanelMatches(90,172,"inner","inner","cover"),"middle posture leaves native policy in charge");
  check(LiveB8Policy.target(65,1,85,172)==2&&LiveB8Policy.target(66,1,85,172)==1,"closing threshold");
  check(LiveB8Policy.target(105,2,85,172)==1&&LiveB8Policy.target(104,2,85,172)==2,"opening threshold");
  for(int primary:new int[]{1,2}){
   c=fresh();LiveB8Power.panel=primary;LiveB8Power.base=LiveB8Power.current=primary==1?3:0;
   run(c,90,800);int moves=LiveB8Task.moves,arms=LiveB8Bridge.arms,requests=LiveB8Power.requests;
   for(int n=0;n<30;n++){run(c,80,50);run(c,100,50);}
   check(LiveB8Power.panel==primary&&LiveB8Task.moves==moves&&LiveB8Bridge.arms==arms&&LiveB8Power.requests==requests,"80-100 demo creates no additional task moves, shields or primary requests from either panel");
   run(c,primary==1?65:105,1500);check(LiveB8Power.panel!=primary,"leaving hold band completes handoff");stop(c);
  }
  check(LiveB8Policy.target(0,1,85,172)==2&&LiveB8Policy.target(172,2,85,172)==1,"endpoints always route to native destination");
  check(!LiveB8Policy.usable(true,10000,7000,9999,90),"missing app heartbeat expires even with a fresh sample");
  check(!LiveB8Policy.usable(true,10000,9999,9999,Float.NaN),"NaN never routes");
  // Scene acknowledgements use a local callback clock; one may arrive during
  // a slower controller query, after that tick sampled its clock.
  c=fresh();run(c,140,150);long earlierTick=SystemClock.now;SystemClock.now+=80;
  c.presentation(c.sceneSession(),true,"inner",SystemClock.now,true);
  Method alive=LiveB8Controller.class.getDeclaredMethod("presentationAlive",long.class);alive.setAccessible(true);
  Method ready=LiveB8Controller.class.getDeclaredMethod("presentationReady",long.class);ready.setAccessible(true);
  check((Boolean)alive.invoke(c,earlierTick)&&(Boolean)ready.invoke(c,earlierTick),"callback arriving during controller query cannot look like a lost scene lease");stop(c);
  // alpha45: stale coverage after task preparation restores native policy
  // under the existing scene, then releases on new evidence or a fixed deadline.
  c=fresh();animated=true;run(c,140,800);prepared=false;nativeReady=false;
  before=LiveB8Bridge.arms;requestCount=LiveB8Power.requests;int moved=LiveB8Task.moves;
  run(c,140,850);
  check(c.ownsRoute()&&c.report().contains("NATIVE_FALLBACK Prepared destination commits stalled")&&!c.report().contains("ABORT"),"persistent stale destination uses native recovery instead of abrupt abort");
  check(LiveB8Bridge.arms==before&&LiveB8Power.requests==requestCount&&LiveB8Task.moves==moved,"stall recovery allocates no shield and performs no additional handoff");
  run(c,120,500);run(c,160,500);
  check(c.ownsRoute(),"quick reversals cannot restart a handoff while native recovery is pending");
  run(c,140,1000);check(!c.ownsRoute()&&c.report().contains("CANCEL_COVERAGE_TIMEOUT"),"persistent stall recovery cannot extend its deadline on reversal");stop(c);
  c=fresh();animated=true;captured=false;run(c,140,200);prepared=false;nativeReady=false;
  run(c,140,800);check(c.report().contains("NATIVE_FALLBACK Presentation commits stalled"),"pre-capture commit stall also enters native recovery");
  nativeReady=true;run(c,140,400);
  check(!c.ownsRoute()&&c.report().contains("CANCEL_NATIVE_READY"),"fresh native checks and primary commit can end recovery before deadline");stop(c);
  // alpha46: asynchronous cleanup must not withdraw the app scene before the
  // last task/display mutation or while owned layers are still being removed.
  c=fresh();run(c,140,150);final LiveB8Controller cleanupScene=c;final long retainedSession=c.sceneSession();
  LiveB8Task.onRestore=()->check(cleanupScene.sceneSession()==retainedSession&&cleanupScene.sceneTask()==42,"task restoration runs under retained app coverage");
  LiveB8Power.onClose=()->check(cleanupScene.sceneSession()==retainedSession,"display release runs under retained app coverage");
  LiveB8Power.cleanupFailures=2;c.update(false,140,SystemClock.now,172,85);tick.invoke(c);
  check(c.sceneSession()==retainedSession&&c.ownsRoute(),"pending power release keeps scene identity and route ownership");
  tick.invoke(c);check(c.sceneSession()==retainedSession,"retry does not clear coverage early");
  tick.invoke(c);check(c.sceneSession()==0&&!c.ownsRoute(),"successful cleanup finally withdraws the scene");
  LiveB8Task.onRestore=null;LiveB8Power.onClose=null;stop(c);
  c=fresh();nativeReady=false;nativeImage=false;run(c,60,800);LiveB8Bridge.allowRemoval=false;long shieldSession=c.sceneSession();
  c.update(false,60,SystemClock.now,172,85);tick.invoke(c);
  check(c.sceneSession()==shieldSession&&c.ownsRoute(),"pending shield removal cannot withdraw presentation");
  LiveB8Bridge.allowRemoval=true;tick.invoke(c);check(c.sceneSession()==0,"confirmed shield and power cleanup withdraws presentation");stop(c);
  // A reversal before promotion can expose Home while the app moves back to D0.
  // Keep coverage, require a new native check, and retain the fixed timeout.
  for(boolean destinationCaptured:new boolean[]{false,true}){
   c=fresh();animated=true;captured=destinationCaptured;run(c,140,800);nativeReady=false;
   long scene=c.sceneSession();int shields=LiveB8Bridge.arms,requests=LiveB8Power.requests;
   step(c,180);run(c,180,500);
   check(c.sceneSession()==scene&&c.report().contains("PREPARED_RETURN_TO_SOURCE"),"pre-promotion reversal retains coverage with or without destination capture");
   check(LiveB8Bridge.arms==shields&&LiveB8Power.requests==requests,"source return adds no shield or primary request");
   nativeReady=true;run(c,180,400);check(!c.ownsRoute()&&c.report().contains("CANCEL_NATIVE_READY"),"source return completes after repeated native readiness");stop(c);
  }
  c=fresh();run(c,140,800);nativeReady=false;step(c,180);run(c,180,1900);
  check(!c.ownsRoute()&&c.report().contains("CANCEL_COVERAGE_TIMEOUT"),"source return cannot retain coverage indefinitely");stop(c);
  // A readiness callback can arrive during a slow layout query. Age it against
  // query completion, never the clock sampled before the Binder call.
  for(boolean cancellation:new boolean[]{false,true}){
   c=fresh();nativeReady=false;run(c,60,1000);
   if(cancellation){((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;run(c,0,150);}
   final LiveB8Controller querying=c;
   LiveB8Task.onVerify=()->{SystemClock.now+=80;querying.presentation(querying.sceneSession(),true,"cover",SystemClock.now,true,true,true);querying.primaryReady(querying.sceneSession(),querying.primaryCheck(),querying.primaryCheckPhysical(),SystemClock.now);};
   step(c,cancellation?0:60);step(c,cancellation?0:60);
   check(c.report().contains(cancellation?"CANCEL_NATIVE_READY":"HANDOFF_COMPLETE"),"in-query callback progresses readiness in both normal and canceled routes");
   LiveB8Task.onVerify=null;stop(c);
  }
  // Android 15+ production route: independent animated roots replace the shield.
  for(boolean opening:new boolean[]{false,true})for(boolean plain:new boolean[]{false,true}){
   LiveB8Controller unused=fresh();stop(unused);
   LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=new LiveB8Controller(root,false,true);animated=!plain;
   if(opening){LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=0;}
   float start=opening?10:150,end=opening?120:60;
   prepared=false;run(c,start,150);check(LiveB8Task.moves==0,"direct route waits for both presented images before moving task");
   prepared=true;run(c,start,300);check(LiveB8Task.moves==1,"direct coverage permits task preparation");
   LiveB8Scene.mapping=false;run(c,end,150);check(LiveB8Power.requests==1,"incomplete root mapping blocks primary switch");
   LiveB8Scene.mapping=true;nativeReady=false;run(c,end,500);
   check(LiveB8Power.requests==2&&LiveB8Bridge.arms==0,"direct route promotes without allocating or capturing static shield");
   check(c.report().contains("MOVING_COVERAGE_HANDOFF")&&!c.report().contains("HANDOFF_COMPLETE"),"moving route still waits for native readiness");
   nativeReady=true;run(c,end,350);check(c.report().contains("HANDOFF_COMPLETE"),"direct presented/native readiness completes both directions and plain fallback");
   run(c,start,900);check(LiveB8Task.moves==2&&LiveB8Bridge.arms==0,"direct reversal keeps same scene without static shield");
   LiveB8Scene.removal=false;c.close();tick.invoke(c);check(c.ownsRoute()&&c.sceneSession()>0,"direct removal callback retains route ownership");
   LiveB8Scene.removal=true;tick.invoke(c);check(!c.ownsRoute(),"completed root removal releases direct route");stop(c);
  }
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);LiveB8Scene.mapping=true;
  run(c,60,500);nativeReady=false;
  ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;run(c,0,150);
  check(c.report().contains("CANCEL_COVERAGE_WAIT")&&LiveB8Bridge.arms==0,"direct cancellation retains moving coverage without power reassertion");
  long recovering=c.sceneSession();step(c,8);nativeReady=true;run(c,12,400);check(c.report().contains("CANCEL_NATIVE_READY")&&c.sceneSession()!=recovering,"direct cancellation settles after rapid reopen");
  step(c,18);check(c.ownsRoute(),"direct immediate reopening starts next eligible episode");stop(c);
  // Home uses a source projection, never a launcher task move. Its first
  // destination image is captured only after native Home geometry is verified.
  for(boolean opening:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=true;captured=false;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=new LiveB8Controller(root,false,true);
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?0:3;
   float early=opening?12:160,middle=opening?120:60,endpoint=opening?180:0;
   run(c,early,500);check(c.sceneHome()&&LiveB8Task.moves==0,"Home source is covered without moving root");
   check(c.captureDisplay()==-1,"Home never requests a secondary task capture");
   captured=false;run(c,middle,600);
   check(c.captureDisplay()==0&&c.primaryCheck()>0,"Home captures native primary after verified remap");
   check(!c.report().contains("HANDOFF_COMPLETE"),"Home needs matching native capture as well as HAS_DRAWN");
   captured=true;run(c,middle,300);check(c.report().contains("HANDOFF_COMPLETE"),"Home handoff completes after native capture commit");
   int rev=c.sceneRevision();LiveB8Power.base=opening?3:0;nativeReady=false;step(c,endpoint);
   check(c.sceneSession()==0,"Home endpoint revokes session before late capture delivery");
   check(LiveB8Scene.last.closed,"Home endpoint uncovers native without capture/readiness wait");
   final LiveB8Scene homeScene=LiveB8Scene.last;
   LiveB8Power.onClose=()->check(homeScene.closed,"verified native screen uncovered before power cleanup");
   nativeReady=true;run(c,endpoint,400);
   check(!c.ownsRoute()&&LiveB8Task.moves==0&&LiveB8Bridge.arms==0,"Home completes both directions with no root move or frozen shield");stop(c);
  }
  // Split groups use native layout on D0 in both directions, including pause/resume.
  for(boolean opening:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.workspace=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?0:3;
   c=new LiveB8Controller(root,false,true);captured=false;
   run(c,opening?10:162,400);
   check(c.sceneWorkspace()&&!c.sceneHome()&&c.sceneTasks().length==3,"workspace membership separate from Home");
   check(c.captureDisplay()==-1&&LiveB8Task.moves==0,"workspace never moves a leaf or captures D1");
   run(c,85,700);check(c.captureDisplay()==0&&c.primaryCheck()>0,"workspace captures verified native destination");
   check(!c.report().contains("HANDOFF_COMPLETE"),"all panes still need committed native snapshot");
   captured=true;run(c,85,400);check(c.report().contains("HANDOFF_COMPLETE"),"workspace handoff completes after capture");
   c.pause(true,opening?1:2);run(c,85,1000);
   check(c.paused()&&c.sceneSession()==0,"workspace pause removes overlays for interaction");
   c.pause(false,opening?1:2);run(c,opening?91:79,400);
   check(c.sceneSession()>0&&c.sceneWorkspace()&&LiveB8Task.moves==0,"workspace resume retains group");
   captured=nativeReady=false;LiveB8Power.base=opening?3:0;step(c,opening?180:0);
   check(c.sceneSession()==0&&!c.ownsRoute(),"workspace endpoint releases without waiting for capture");
   check(LiveB8Task.moves==0&&LiveB8Bridge.arms==0,"workspace lifecycle never extracts a task or adds frozen shield");stop(c);
  }
  // alpha56: only Home starts destination layout after 20 degrees of travel.
  // Ordinary apps still need the full hold band, and Home reversals keep it.
  for(boolean home:new boolean[]{false,true})for(boolean opening:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=home;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=new LiveB8Controller(root,false,true);
   int sourcePanel=opening?2:1;LiveB8Power.panel=sourcePanel;LiveB8Power.base=LiveB8Power.current=opening?0:3;
   run(c,opening?12:160,500);
   run(c,opening?19:153,300);check(LiveB8Power.panel==sourcePanel,"first remap waits for its angle in both directions");
   run(c,opening?20:152,600);check(LiveB8Power.panel==(home?3-sourcePanel:sourcePanel),"only Home starts native layout after 20 degrees of travel");
   if(home){int requests=LiveB8Power.requests;run(c,opening?30:140,300);check(LiveB8Power.requests==requests,"early Home preparation stays latched before handoff band");}
   run(c,85,600);
   check(LiveB8Power.panel==(home?3-sourcePanel:sourcePanel),"only Home prepares at saved angle instead of 20 degrees beyond");
   if(home){
    check(c.report().contains("HOME_PREP_EARLY")&&c.report().contains("HANDOFF_COMPLETE"),"Home early prep passes full native readiness and capture gates");
    int requests=LiveB8Power.requests;
    for(int n=0;n<8;n++)run(c,n%2==0?84:86,100);
    check(LiveB8Power.requests==requests,"Home jitter around saved angle never repeats the primary remap");
    run(c,opening?66:104,300);check(LiveB8Power.requests==requests,"Home reversal retains the original 20 degree hold boundary");
    run(c,opening?65:105,700);check(LiveB8Power.panel==sourcePanel,"deliberate Home reversal still returns to source");
    check(LiveB8Task.moves==0&&LiveB8Bridge.arms==0,"earlier Home prep never moves launcher or allocates a frozen shield");
   }else{
    run(c,opening?104:66,300);check(LiveB8Power.panel==sourcePanel,"ordinary app remap timing stays unchanged");
    run(c,opening?105:65,700);check(LiveB8Power.panel==3-sourcePanel,"ordinary app still promotes at original threshold");
   }
   stop(c);
  }
  for(boolean opening:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=new LiveB8Controller(root,false,true);
   int sourcePanel=opening?2:1;LiveB8Power.panel=sourcePanel;LiveB8Power.base=LiveB8Power.current=opening?0:3;
   LiveB8Brightness.pending=true;run(c,85,500);
   check(LiveB8Power.panel==sourcePanel&&!c.report().contains("HOME_PREP_EARLY"),"early Home prep still waits for brightness preparation");
   LiveB8Brightness.pending=false;captured=false;nativeReady=false;run(c,85,500);
   check(LiveB8Power.panel==3-sourcePanel&&!c.report().contains("HANDOFF_COMPLETE"),"early Home remap cannot complete without captured native content");
   captured=true;run(c,85,300);check(!c.report().contains("HANDOFF_COMPLETE"),"early Home remap also waits for native window readiness");
   nativeReady=true;run(c,85,400);check(c.report().contains("HANDOFF_COMPLETE"),"Home completes only after all original guards pass");stop(c);
  }
  for(boolean opening:new boolean[]{false,true})for(boolean prepared:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=new LiveB8Controller(root,false,true);LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?0:3;
   captured=false;nativeReady=false;
   run(c,opening?12:160,prepared?500:50);
   if(prepared)run(c,85,400);
   LiveB8Power.cleanupFailures=2;LiveB8Power.base=opening?3:0;
   step(c,opening?180:0);
   check(c.sceneSession()==0,"endpoint revokes Home session with missing native capture");
   check(c.report().contains("HOME_ENDPOINT_BYPASS"),"endpoint bypass runs during preparation too");
   check(LiveB8Scene.last==null||LiveB8Scene.last.closed,"Home coverage detached before deferred power cleanup");
   check(c.ownsRoute(),"Home keeps ownership while cleanup is pending");
   LiveB8Power.cleanupFailures=0;run(c,opening?180:0,200);
   check(!c.ownsRoute()&&LiveB8Task.moves==0,"Home native fallback completes without capture");stop(c);
  }
  // Early reveal must not release route ownership while owned resources linger.
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);LiveB8Scene.mapping=LiveB8Scene.removal=true;
  run(c,60,800);LiveB8Power.base=0;LiveB8Power.cleanupFailures=100;step(c,0);run(c,0,500);
  check(c.ownsRoute()&&LiveB8Scene.last.closed,"native visible while pending power cleanup still owns route");
  LiveB8Power.cleanupFailures=0;step(c,0);check(!c.ownsRoute(),"route released only after resource cleanup");stop(c);
  // Stationary gesture: both angle-selected panels, origins, Home/apps and handoff stages.
  for(boolean home:new boolean[]{false,true})for(int origin:new int[]{1,2})for(boolean afterHandoff:new boolean[]{false,true})for(int selected:new int[]{1,2}){
   c=fresh();stop(c);c=new LiveB8Controller(root,false,true);LiveB8Scene.mapping=LiveB8Scene.removal=true;
   LiveB8Task.home=home;LiveB8Power.panel=origin;LiveB8Power.base=LiveB8Power.current=origin==1?3:0;
   float pose=afterHandoff?(origin==1?40:130):(origin==1?155:10);
   run(c,pose,afterHandoff?1200:300);long oldSession=c.sceneSession();
   c.pause(true,selected);step(c,pose);
   check(c.pauseReturnAt()>0&&LiveB8Power.panel==selected,"pause honors the angle-selected panel even when it differs from the origin");
   nativeReady=false;run(c,pose,350);
   check(c.sceneSession()==oldSession&&!c.paused()&&c.pauseFadeAt()==0,"pause cannot dissolve before native window readiness");
   nativeReady=true;run(c,pose,200);
   check(c.pauseFadeAt()>0&&c.sceneSession()==oldSession,"verified native app starts a timed dissolve");
   step(c,pose);check(LiveB8Scene.last.alpha>0&&LiveB8Scene.last.alpha<1,"scene roots and their guards fade gradually");
   run(c,pose,200);
   check(c.paused()&&c.sceneSession()==0&&LiveB8Scene.last.closed,"settled screen has no snapshot roots");
   int requests=LiveB8Power.requests;run(c,pose,31000);
   check(c.paused()&&LiveB8Power.requests==requests,"stationary use neither restarts animation nor expires after 30 seconds");
   c.pause(false,selected);step(c,pose+(origin==1?-5:5));
   check(c.sceneSession()>oldSession&&!c.paused()&&LiveB8Power.panel==selected,"movement resumes with a fresh capture without native-policy panel bounce");
   stop(c);
  }
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);step(c,140);
  nativeReady=false;run(c,140,2000);check(!c.ownsRoute()&&c.sceneSession()==0,"unverified stationary return has bounded native fallback");
  int pauseRequests=LiveB8Power.requests;run(c,140,2000);check(LiveB8Power.requests==pauseRequests,"failed pause cannot restart while still");
  c.pause(false,1);nativeReady=true;step(c,135);check(c.ownsRoute(),"motion rearms after bounded pause fallback");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);run(c,140,200);c.pause(false,1);step(c,180);
  check(!c.ownsRoute()&&c.sceneSession()==0,"endpoint interrupts pause unwind immediately");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);run(c,140,800);
  check(c.paused(),"pause ready before lock test");android.view.IWindowManager.locked=true;step(c,140);
  check(!c.ownsRoute(),"lock releases stationary power choice");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);run(c,140,800);
  c.update(false,140,SystemClock.now,172,85);tick.invoke(c);check(!c.ownsRoute(),"disable releases stationary power choice");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);run(c,140,800);
  SystemClock.now+=2100;tick.invoke(c);check(!c.ownsRoute(),"lost reader lease releases stationary power choice");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);run(c,140,800);
  c.pause(false,1);step(c,0);check(!c.ownsRoute(),"full close clears a settled hold before any new session");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);step(c,140);
  c.pause(false,1);run(c,125,800);check(c.sceneSession()>0&&!c.paused()&&c.pauseReturnAt()==0,"movement during unwind finishes cleanup then starts a fresh current-pose session");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);run(c,140,800);
  pauseRequests=LiveB8Power.requests;((LiveB8Power)pf.get(c)).canceled=true;run(c,140,2000);
  check(!c.ownsRoute()&&LiveB8Power.requests==pauseRequests,"Android cancellation never reasserts the settled panel request");
  c.pause(false,1);step(c,135);check(c.ownsRoute(),"only new movement rearms a canceled stationary episode");stop(c);
  c=fresh();run(c,140,400);c.pause(true,1);run(c,140,800);
  check(c.paused()&&c.sceneSession()==0,"logical-window fallback follows the same readiness and timed dissolve");stop(c);
  c=fresh();stop(c);c=new LiveB8Controller(root,false,true);run(c,140,400);c.pause(true,1);run(c,140,300);
  // A remap invalidates the ongoing dissolve, including a successful prior window token.
  run(c,140,50);check(c.pauseFadeAt()>0,"fade underway before mapping-loss test");
  LiveB8Task.primaryUnavailable=true;step(c,140);check(c.pauseFadeAt()==0&&LiveB8Scene.last.alpha==1,"mapping loss cancels dissolve and restores coverage");
  LiveB8Task.primaryUnavailable=false;run(c,140,600);check(c.paused(),"fresh repeated window evidence completes replacement dissolve");stop(c);
  System.out.println("LiveB8ControllerTest: "+checks+" lifecycle/readiness checks passed (fake platform, not handset proof)");
 }
}
