package org.duofold.live.experimental.b8;
import java.io.File;
import java.lang.reflect.Method;
import android.os.SystemClock;
public final class LiveB8ControllerTest {
 static boolean captured=true,nativeReady=true,animated=false,nativeImage=true;
 static boolean pairVerified=true,unwindAck=true;
 static LiveB8Controller directController(){return new LiveB8Controller(root,false,true,(session,a,r0,b,r1)->pairVerified);}
 static float savedAngle=85;
 static boolean ack=true,prepared=true,paused=true,commit=true;static Method tick;static File root;static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static LiveB8Controller fresh(){pairVerified=true;unwindAck=true;savedAngle=85;captured=nativeReady=ack=prepared=paused=commit=true;animated=false;nativeImage=true;LiveB8Power.reset();LiveB8Task.moves=LiveB8Task.retargets=LiveB8Task.focuses=0;LiveB8Task.home=false;LiveB8Task.workspace=false;LiveB8Task.primaryUnavailable=false;LiveB8Task.onRestore=null;LiveB8Task.onVerify=null;LiveB8Task.selected="test.app";LiveB8Brightness.begun=0;LiveB8Brightness.failPrepared=false;LiveB8Brightness.pending=false;LiveB8Brightness.onBegin=null;LiveB8Brightness.onPrepare=null;LiveB8Bridge.arms=0;LiveB8Bridge.allowRemoval=true;LiveB8Bridge.allowCommit=true;LiveB8Bridge.failArm=false;android.view.IWindowManager.locked=false;SystemClock.now+=10000;return new LiveB8Controller(root,false);}
 static void step(LiveB8Controller c,float a)throws Exception{SystemClock.now+=25;c.update(true,a,SystemClock.now,172,savedAngle);if(ack)c.presentation(c.sceneSession(),prepared,LiveB8Power.panel==1?"inner":"cover",commit?SystemClock.now:0,paused,animated,nativeImage,c.sceneRevision(),SystemClock.now);if(captured)c.captured(c.sceneSession(),c.capturePhysical(),c.sceneRevision());if(nativeReady)c.primaryReady(c.sceneSession(),c.primaryCheck(),c.primaryCheckPhysical(),SystemClock.now);if(unwindAck&&c.pauseReturnAt()>0&&SystemClock.now-c.pauseReturnAt()>=280)c.pausePresented(c.sceneSession(),c.pauseReturnAt(),LiveB8Power.panel==1?"inner":"cover",SystemClock.now);tick.invoke(c);}
 static void run(LiveB8Controller c,float a,int ms)throws Exception{for(int n=0;n<ms;n+=25)step(c,a);}
 static void stop(LiveB8Controller c)throws Exception{c.close();tick.invoke(c);check(!c.ownsRoute(),"cleanup releases ownership");}
 // Exercise the unchanged non-brightness recovery independently of preflight.
 static void otherSoftwareFailure(LiveB8Controller c)throws Exception{
  Method fallback=LiveB8Controller.class.getDeclaredMethod("nativeFallback",String.class);
  fallback.setAccessible(true);fallback.invoke(c,"test non-brightness readiness failure");
 }
 static void policyStep(LiveB8Controller c,org.duofold.live.HingePausePolicy p,float a)throws Exception{
  long at=SystemClock.now+25;p.update(true,at,at,a,172,LiveB8Power.panel,2,false,true);c.pause(p.held(),p.sourcePrimary());step(c,a);
 }
 static void policyRun(LiveB8Controller c,org.duofold.live.HingePausePolicy p,float a,int ms)throws Exception{for(int n=0;n<ms;n+=25)policyStep(c,p,a);}
 public static void main(String[] args)throws Exception{
  root=new File(args[0]);tick=LiveB8Controller.class.getDeclaredMethod("tick");tick.setAccessible(true);
  for(boolean opening:new boolean[]{false,true}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   org.duofold.live.HingePausePolicy gesture=new org.duofold.live.HingePausePolicy();
   policyStep(tested,gesture,opening?0:172);
   policyRun(tested,gesture,opening?125:15,3400);
   check(gesture.held()&&tested.paused()&&tested.sceneSession()==0&&LiveB8Power.panel==(opening?1:2),"stationary angle selects its screen despite an earlier opposite endpoint, opening="+opening);
   stop(tested);
  }
  // Replay the shape of alpha100's multi-reversal session, including the long
  // 127/128 hold that was blocked by the stale cover origin.
  for(boolean mirrored:new boolean[]{false,true}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Power.panel=mirrored?1:2;LiveB8Power.base=LiveB8Power.current=mirrored?3:1;
   org.duofold.live.HingePausePolicy gesture=new org.duofold.live.HingePausePolicy();
   policyStep(tested,gesture,mirrored?172:0);
   for(float a:new float[]{7,12,23,41,71,107,127,134,118,111,89,70,48,35,41,58,94,110,125,129,128}){
    policyRun(tested,gesture,mirrored?172-a:a,200);check(!gesture.held(),"moving replay cannot trigger hold");
   }
   for(int n=0;n<40;n++)policyRun(tested,gesture,mirrored?44+n%2:128-n%2,100);
   check(tested.paused()&&tested.sceneSession()==0&&LiveB8Power.panel==(mirrored?2:1),"reversal replay unwinds onto held screen");
   policyRun(tested,gesture,mirrored?120:60,500);
   check(tested.paused()&&tested.sceneSession()==0&&LiveB8Power.panel==(mirrored?1:2),"continuing through midpoint never restarts effect");stop(tested);
  }
  brightnessMotionRegression();
  nativeTaskHandoffRegression();
  // Other software failures still use the alpha103 retained-frame recovery.
  // A late renderer acknowledgement cannot be replaced by elapsed shell time.
  for(int origin:new int[]{1,2}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Power.panel=origin;LiveB8Power.base=LiveB8Power.current=origin==1?3:1;
   LiveB8Brightness.pending=true;float a=origin==1?133:40;run(tested,a,500);
   unwindAck=false;otherSoftwareFailure(tested);run(tested,a,700);
   check(tested.report().contains("SOFTWARE_NATIVE_RETURN")&&tested.pauseFadeAt()==0&&tested.sceneSession()>0,"non-brightness fallback retains coverage until renderer unwind");
   int requests=LiveB8Power.requests,ons=LiveB8Power.powerOns;
   unwindAck=true;run(tested,a,450);
   check(tested.report().contains("PAUSE_DISSOLVE")&&tested.paused(),"non-brightness fallback dissolves only after completed unwind");
   check(requests==LiveB8Power.requests&&LiveB8Brightness.begun==0,"software recovery retains chosen native panel without repeated requests or a brightness writer");stop(tested);
  }
  // Other software recovery while closing at 42 -> 35 degrees with Samsung
  // base still OPENED. It must not release to that base and strand cover OFF.
  for(int origin:new int[]{1,2})for(float held:new float[]{35,125,90}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Power.panel=origin;LiveB8Power.base=LiveB8Power.current=origin==1?3:1;
   LiveB8Brightness.pending=true;run(tested,origin==1?160:8,500);
   run(tested,held,100);otherSoftwareFailure(tested);
   nativeReady=false;run(tested,held,500);
   int selected=held<90?2:held>90?1:origin;
   check(LiveB8Power.panel==selected&&LiveB8Power.releases==0,"software failure selects held angle, not Samsung base/origin");
   check(tested.pauseFadeAt()==0&&tested.sceneSession()>0,"selected panel needs fresh native readiness before removing coverage");
   nativeReady=true;run(tested,held,500);
   check(tested.paused()&&tested.sceneSession()==0&&LiveB8Scene.last.closed,"software recovery settles onto interactive panel and removes image layers");
   int requests=LiveB8Power.requests,starts=LiveB8Scene.starts;
   run(tested,held,35000);
   check(tested.paused()&&LiveB8Power.panel==selected&&LiveB8Power.requests==requests,"native screen survives extended partial hold without animation restart");
   float crossed=selected==1?35:125;run(tested,crossed,600);
   check(tested.paused()&&LiveB8Power.panel==3-selected&&LiveB8Scene.starts==starts,"crossing 90 during software continuation changes only the native panel");
   step(tested,90);check(LiveB8Power.panel==3-selected,"exact midpoint retains active native screen");
   step(tested,0);check(!tested.ownsRoute(),"endpoint releases software continuation");stop(tested);
  }
  for(int stopReason=0;stopReason<5;stopReason++){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Brightness.pending=true;run(tested,155,500);run(tested,35,100);
   otherSoftwareFailure(tested);run(tested,35,700);
   int requests=LiveB8Power.requests;
   if(stopReason==0){java.lang.reflect.Field f=LiveB8Controller.class.getDeclaredField("power");f.setAccessible(true);((LiveB8Power)f.get(tested)).canceled=true;step(tested,35);}
   if(stopReason==1){android.view.IWindowManager.locked=true;step(tested,35);}
   if(stopReason==2){tested.thermal(3);step(tested,35);}
   if(stopReason==3){SystemClock.now+=3000;tick.invoke(tested);}
   if(stopReason==4){tested.update(false,35,SystemClock.now,172,85);tick.invoke(tested);}
   check(!tested.ownsRoute()&&LiveB8Power.requests==requests,"cancellation/lock/thermal/stale/disable cannot reassert recovery panel reason="+stopReason);
   if(stopReason==0){run(tested,35,1500);check(LiveB8Power.requests==requests,"fresh partial-angle replies cannot restart an Android-canceled software return");}stop(tested);
  }
  // Cross the midpoint during the reverse animation: keep the original bound
  // and verify the new physical primary before dissolving.
  {LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Brightness.pending=true;run(tested,160,500);run(tested,42,100);
   unwindAck=false;otherSoftwareFailure(tested);run(tested,35,100);
   long start=tested.pauseReturnAt();run(tested,125,100);
   check(LiveB8Power.panel==1&&tested.pauseReturnAt()==start&&tested.pauseFadeAt()==0,"mid-unwind crossing retargets native panel without restarting timer");
   unwindAck=true;run(tested,125,500);check(tested.paused()&&LiveB8Power.panel==1,"new physical panel finishes unwind and readiness checks");stop(tested);
  }
  // No native-ready evidence: fail-open at the original bound, never retain
  // opaque screenshots indefinitely or spin up another animation immediately.
  {LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Brightness.pending=true;run(tested,160,500);run(tested,35,100);
   nativeReady=false;otherSoftwareFailure(tested);run(tested,35,2000);
   check(!tested.ownsRoute()&&tested.sceneSession()==0,"unverified software return is bounded");
   int requests=LiveB8Power.requests;run(tested,35,2000);check(LiveB8Power.requests==requests,"failed unwind cannot restart in the same partial leg");stop(tested);
  }
  {LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Brightness.pending=true;run(tested,160,500);run(tested,35,100);
   java.lang.reflect.Field field=LiveB8Controller.class.getDeclaredField("power");field.setAccessible(true);
   LiveB8Power owned=(LiveB8Power)field.get(tested);int requests=LiveB8Power.requests;
   LiveB8Task.onRestore=()->owned.canceled=true;tested.pause(true,2);step(tested,35);
   check(!tested.ownsRoute()&&LiveB8Power.requests==requests,"Android cancellation during task restore blocks subsequent software panel request");
   LiveB8Task.onRestore=null;stop(tested);
  }
  LiveB8Controller initial=fresh();stop(initial);initial=directController();LiveB8Power.allowReady=false;
  int readsBefore=LiveB8Brightness.earlyReads,permitsBefore=LiveB8Brightness.refreshPermits;
  step(initial,160);
  check(LiveB8Brightness.earlyReads==readsBefore+1,"brightness read overlaps initial panel wake");
  run(initial,150,150);
  check(LiveB8Brightness.refreshPermits==permitsBefore&&LiveB8Task.moves==0&&LiveB8Power.requests==1,"unready panels cannot authorize refresh or handoff");
  LiveB8Power.allowReady=true;step(initial,140);
  check(LiveB8Brightness.refreshPermits==permitsBefore+1&&LiveB8Brightness.earlyReads==readsBefore+1,"both-ON permits refresh without restarting the read");stop(initial);
  // Renderer delivery can lag the shell by longer than the entire unwind.
  for(boolean recovery:new boolean[]{false,true}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Controller tested=directController();
   LiveB8Brightness.pending=true;run(tested,133,500);unwindAck=false;
   if(recovery)otherSoftwareFailure(tested);else tested.pause(true,1);
   run(tested,133,100);
   org.duofold.live.B8PauseAnimation animation=new org.duofold.live.B8PauseAnimation();
   org.duofold.live.B8PauseAnimation.Frame first=animation.frame(tested.pauseReturnAt(),SystemClock.now,133,true,172);
   run(tested,133,400);check(tested.pauseFadeAt()==0,"no native dissolve while GPU has no completed return frame");
   check(!animation.presented(first,SystemClock.now),"one delayed frame is not a complete unwind");
   int frames=1;boolean done=false;
   while(!done&&frames<40){
    org.duofold.live.B8PauseAnimation.Frame f=animation.frame(tested.pauseReturnAt(),SystemClock.now,133,true,172);
    step(tested,133);done=animation.presented(f,SystemClock.now);frames++;
    if(done)tested.pausePresented(tested.sceneSession(),tested.pauseReturnAt(),"inner",SystemClock.now);
    else check(tested.pauseFadeAt()==0,"intermediate reverse pose retains opaque coverage");
   }
   check(done&&frames>=10,"real animation produces multiple completed reverse poses before final ack");
   run(tested,133,200);check(tested.paused(),"controller finishes only after real frame progression and dissolve");stop(tested);
  }
  {LiveB8Controller tested=fresh();stop(tested);tested=directController();run(tested,140,400);
   tested.pause(true,1);unwindAck=false;run(tested,140,700);
   check(tested.pauseReturnAt()>0&&tested.pauseFadeAt()==0&&tested.sceneSession()>0,"elapsed shell time cannot skip renderer unwind");
   tested.pausePresented(tested.sceneSession()-1,tested.pauseReturnAt(),"inner",SystemClock.now);run(tested,140,50);
   check(tested.pauseFadeAt()==0,"old session cannot authorize dissolve");
   tested.pausePresented(tested.sceneSession(),tested.pauseReturnAt(),"cover",SystemClock.now);run(tested,140,50);
   check(tested.pauseFadeAt()==0,"other panel cannot authorize dissolve");
   unwindAck=true;run(tested,140,400);check(tested.paused(),"own completed unwind plus native readiness allows dissolve");stop(tested);
  }
  // alpha78: feed the real default pause policy into the real controller for Home and apps.
  for(boolean home:new boolean[]{false,true})for(boolean inner:new boolean[]{false,true}){
   LiveB8Controller preparation=fresh();stop(preparation);
   LiveB8Controller tested=directController();LiveB8Task.home=home;LiveB8Power.panel=inner?1:2;LiveB8Power.base=LiveB8Power.current=inner?3:1;
   org.duofold.live.HingePausePolicy policy=new org.duofold.live.HingePausePolicy();float held=inner?120:60;
   policyRun(tested,policy,held,3000);check(tested.paused()&&tested.sceneSession()==0,"default hold releases animation layers for Home/app selected panel");
   int requests=LiveB8Power.requests;policyRun(tested,policy,inner?160:10,500);
   check(tested.paused()&&tested.sceneSession()==0&&requests==LiveB8Power.requests,"continued same-side movement cannot create a new session or primary request");
   policyStep(tested,policy,90);check(tested.paused(),"exact midpoint does not restart held native screen");
   policyRun(tested,policy,inner?89:91,200);check(tested.paused()&&tested.sceneSession()==0&&LiveB8Power.panel==(inner?2:1),"midpoint continuation switches native panel without restarting animation");stop(tested);
   preparation=fresh();stop(preparation);tested=directController();LiveB8Task.home=home;LiveB8Power.panel=inner?1:2;LiveB8Power.base=LiveB8Power.current=inner?3:1;
   policy=new org.duofold.live.HingePausePolicy();policyRun(tested,policy,held,3000);policyRun(tested,policy,inner?172:0,500);
   check(!tested.ownsRoute()&&tested.sceneSession()==0,"endpoint clears held panel lease without restarting animation");
   policyRun(tested,policy,inner?165:6,150);check(tested.ownsRoute()&&tested.sceneSession()>0,"next physical leg can animate after endpoint");stop(tested);
  }
  // alpha76: freshly timestamped wallpaper 142/0 repeats while physical base stays CLOSED.
  LiveB8Controller c=fresh();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=0;
  for(int cycle=0;cycle<4;cycle++){LiveB8Power.current=new int[]{0,5,4,0}[cycle];run(c,142,1350);run(c,0,1100);}
  check(LiveB8Power.requests==0&&LiveB8Power.powerOns==0&&LiveB8Task.moves==0&&c.sceneSession()==0&&!c.ownsRoute(),"closed 142/0 replay creates no session, capture or power request");
  check(c.report().contains("START_BLOCKED base=CLOSED"),"closed contradiction leaves explicit evidence");
  LiveB8Power.base=LiveB8Power.current=1;run(c,6,150);
  check(c.ownsRoute()&&LiveB8Power.requests==1,"physical reopening admits normal early motion without waiting for another endpoint");stop(c);
  c=fresh();run(c,179,200);check(!c.ownsRoute()&&LiveB8Power.requests==0,"idle endpoint never starts timed test");
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
  c=fresh();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=1;LiveB8Brightness.pending=true;run(c,4,800);
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
  c=fresh();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=1;run(c,120,1500);
  check(c.report().contains("HANDOFF_COMPLETE primary=1"),"opening handoff completed before outgoing panel disappears");
  LiveB8Power.base=3;prepared=false;nativeReady=false;before=LiveB8Bridge.arms;requestCount=LiveB8Power.requests;
  step(c,180);check(c.sceneSession()>0&&c.report().contains("NATIVE_FALLBACK presentation stale at endpoint"),"lost secondary commit does not immediately remove primary coverage");
  run(c,180,150);check(c.ownsRoute(),"endpoint retains coverage until new native readiness");
  nativeReady=true;run(c,180,250);check(!c.ownsRoute()&&c.report().contains("CANCEL_NATIVE_READY"),"fresh primary commit and app readiness complete fallback without secondary commit");
  check(LiveB8Bridge.arms==before&&LiveB8Power.requests==requestCount,"endpoint fallback allocates no new shield and does not reassert state");stop(c);
  // Replay alpha39: native policy already OPENED while physical D0 is still cover.
  c=fresh();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=1;LiveB8Brightness.failPrepared=true;
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
   c=fresh();LiveB8Power.panel=source;LiveB8Power.base=LiveB8Power.current=source==1?3:1;
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
   LiveB8Power.base=LiveB8Power.current=source==1?1:3;step(c,departed);
   check(c.ownsRoute()&&c.sceneSession()!=canceledSession&&LiveB8Power.requests==requestCount+1,"observed endpoint rearms next fold even after hinge already reopened");stop(c);
  }
  c=fresh();nativeReady=false;run(c,60,1000);
  ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;
  requestCount=LiveB8Power.requests;run(c,60,200);nativeReady=true;run(c,60,400);
  check(!c.ownsRoute()&&LiveB8Power.requests==requestCount,"verified mid-fold cancellation cannot restart without an observed endpoint");
  step(c,0);LiveB8Power.base=LiveB8Power.current=1;step(c,12);check(c.ownsRoute(),"a later fresh endpoint rearms a blocked route");
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
  step(c,0);LiveB8Power.base=LiveB8Power.current=1;step(c,12);check(c.ownsRoute()&&LiveB8Power.requests==requestCount,"endpoint during cleanup does not authorize an overlapping request");
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
   c=fresh();animated=animate;nativeReady=false;LiveB8Power.panel=primary;LiveB8Power.base=LiveB8Power.current=primary==1?3:1;
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
   c=fresh();animated=animate;LiveB8Power.panel=primary;LiveB8Power.base=LiveB8Power.current=primary==1?3:1;
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
   c=fresh();LiveB8Power.panel=primary;LiveB8Power.base=LiveB8Power.current=primary==1?3:1;
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
  // First X unfold: the last native image stays up across a 758 ms hinge gap.
  // An app heartbeat can retain the same scene but cannot authorize its reveal.
  for(String outcome:new String[]{"resume","expire","wrong-session","lock"}){
   c=fresh();stop(c);c=directController();LiveB8Scene.mapping=LiveB8Scene.removal=true;
   LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=1;nativeReady=false;
   run(c,10,350);run(c,120,500);
   long heldSession=c.sceneSession(),sample=SystemClock.now,until=sample+2000;
   check(heldSession>0&&LiveB8Power.panel==1&&!c.report().contains("HANDOFF_COMPLETE"),"unfold holds until fresh inner readiness");
   SystemClock.now=sample+758;
   c.heldPresentation("wrong-session".equals(outcome)?heldSession-1:heldSession,until);
   c.update(true,120,sample,172,savedAngle);tick.invoke(c);
   if("wrong-session".equals(outcome)){
    check(!c.ownsRoute(),"another session cannot retain this scene");stop(c);continue;
   }
   check(c.sceneSession()==heldSession&&!c.report().contains("HANDOFF_COMPLETE"),"758 ms gap retains only existing coverage");
   int requests=LiveB8Power.requests,moves=LiveB8Task.moves;
   for(int age=800;age<=1950;age+=50){
    SystemClock.now=sample+age;c.heldPresentation(heldSession,until);
    c.update(true,120,sample,172,savedAngle);tick.invoke(c);
   }
   check(c.sceneSession()==heldSession&&LiveB8Power.requests==requests&&LiveB8Task.moves==moves&&!c.report().contains("HANDOFF_COMPLETE"),"held heartbeats never authorize movement or reveal");
   if("expire".equals(outcome)){
    SystemClock.now=until;c.update(true,120,sample,172,savedAngle);c.heldPresentation(heldSession,until);tick.invoke(c);
    check(!c.ownsRoute(),"replaying the same hold cannot extend its absolute deadline");
   }else if("lock".equals(outcome)){
    android.view.IWindowManager.locked=true;tick.invoke(c);check(!c.ownsRoute(),"lock revokes held coverage");
   }else{
    nativeReady=true;run(c,120,450);
    check(c.sceneSession()==heldSession&&c.report().contains("HANDOFF_COMPLETE"),"fresh inner readiness resumes the same unfold");
   }
   stop(c);
  }
  // Android 15+ production route: independent animated roots replace the shield.
  for(boolean opening:new boolean[]{false,true})for(boolean plain:new boolean[]{false,true}){
   LiveB8Controller unused=fresh();stop(unused);
   LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();animated=!plain;
   if(opening){LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=1;}
   float start=opening?10:150,end=opening?120:60;
   LiveB8Brightness.pending=true;prepared=false;run(c,start,150);check(LiveB8Task.moves==0,"direct route waits for both presented images before moving task");
   prepared=true;run(c,start,300);check(LiveB8Task.moves==0,"pending brightness leaves the app on the source");
   LiveB8Scene.mapping=false;LiveB8Brightness.pending=false;run(c,end,150);check(LiveB8Power.requests==1,"incomplete root mapping blocks primary switch");
   LiveB8Scene.mapping=true;nativeReady=false;run(c,end,500);
   check(LiveB8Power.requests==2&&LiveB8Bridge.arms==0,"direct route promotes without allocating or capturing static shield");
   check(c.report().contains("MOVING_COVERAGE_HANDOFF")&&!c.report().contains("HANDOFF_COMPLETE"),"moving route still waits for native readiness");
   nativeReady=true;run(c,end,350);check(c.report().contains("HANDOFF_COMPLETE"),"direct presented/native readiness completes both directions and plain fallback");
   run(c,start,900);check(LiveB8Task.moves==0&&LiveB8Task.retargets==0&&LiveB8Task.focuses==0&&LiveB8Bridge.arms==0,"direct reversal observes native task placement without static shield");
   LiveB8Scene.removal=false;c.close();tick.invoke(c);check(c.ownsRoute()&&c.sceneSession()>0,"direct removal callback retains route ownership");
   LiveB8Scene.removal=true;tick.invoke(c);check(!c.ownsRoute(),"completed root removal releases direct route");stop(c);
  }
  c=fresh();stop(c);c=directController();LiveB8Scene.mapping=true;
  run(c,60,500);nativeReady=false;
  ((LiveB8Power)pf.get(c)).canceled=true;LiveB8Power.base=LiveB8Power.current=0;run(c,0,150);
  check(c.report().contains("CANCEL_COVERAGE_WAIT")&&LiveB8Bridge.arms==0,"direct cancellation retains moving coverage without power reassertion");
  long recovering=c.sceneSession();step(c,8);nativeReady=true;run(c,12,400);check(c.report().contains("CANCEL_NATIVE_READY")&&c.sceneSession()!=recovering,"direct cancellation settles after rapid reopen");
  LiveB8Power.base=LiveB8Power.current=1;step(c,18);check(c.ownsRoute(),"direct immediate reopening starts next eligible episode");stop(c);
  // Home uses a source projection, never a launcher task move. Its first
  // destination image is captured only after native Home geometry is verified.
  for(boolean opening:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=true;captured=false;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   float early=opening?12:160,middle=opening?120:60,endpoint=opening?180:0;
   run(c,early,500);check(c.sceneHome()&&LiveB8Task.moves==0,"Home source is covered without moving root");
   check(c.captureDisplay()==0,"Home requests its first native image on the remapped primary");
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
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   c=directController();captured=false;
   run(c,opening?10:162,400);
   check(c.sceneWorkspace()&&!c.sceneHome()&&c.sceneTasks().length==3,"workspace membership separate from Home");
   check(c.captureDisplay()==0&&LiveB8Task.moves==0,"workspace captures its remapped primary without moving a leaf");
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
  // An early switch must not race rotation journaling or a slow brightness preflight.
  for(boolean opening:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Scene.mapping=LiveB8Scene.removal=true;c=directController();
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   float angle=opening?9:158;
   pairVerified=false;run(c,angle,600);
   check(LiveB8Task.moves==0&&LiveB8Power.requests==1&&c.report().contains("ROTATION_PAIR_WAIT"),"no task move or primary change during journal save");
   pairVerified=true;run(c,angle,700);
   check(LiveB8Power.requests==2&&LiveB8Task.moves==0&&c.report().contains("rotation=verified-pair"),"same leg continues with native task placement when both rotations verify");
   stop(c);
  }
  c=fresh();stop(c);c=directController();pairVerified=false;run(c,158,2500);
  check(LiveB8Power.requests==1&&LiveB8Task.moves==0&&c.report().contains("NATIVE_FALLBACK rotation hold unverified"),"bounded wait falls back without moving selected app");stop(c);
  c=fresh();stop(c);c=directController();LiveB8Brightness.onBegin=()->pairVerified=false;run(c,158,700);
  check(LiveB8Power.requests==1&&c.report().contains("rotation hold changed before primary request"),"loss of hold during brightness writes cannot promote");stop(c);
  c=fresh();stop(c);c=directController();LiveB8Brightness.failPrepared=true;run(c,158,500);
  check(LiveB8Power.requests==1&&LiveB8Task.moves==0,"failed brightness preparation leaves app on source");stop(c);
  c=fresh();stop(c);c=directController();pairVerified=false;run(c,158,500);step(c,0);
  check(LiveB8Power.requests==1&&LiveB8Task.moves==0&&c.report().contains("endpoint reached before primary request"),"endpoint while waiting never starts another switch");stop(c);
  // The outgoing screenshot, positive brightness, and a live leg authorize the
  // primary request. Destination capture and native windows gate completion.
  for(boolean home:new boolean[]{false,true})for(boolean opening:new boolean[]{false,true})for(float saved:new float[]{75,115}){
   c=fresh();stop(c);savedAngle=saved;LiveB8Task.home=home;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();
   int sourcePanel=opening?2:1;
   float early=opening?7:164;
   LiveB8Power.panel=sourcePanel;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   commit=false;captured=false;nativeReady=false;
   run(c,early,250);check(LiveB8Power.requests==1,"queued screenshot cannot authorize promotion");
   commit=true;nativeImage=false;
   run(c,early,250);check(LiveB8Power.requests==1,"projected content cannot stand in for native outgoing screenshot");
   nativeImage=true;LiveB8Brightness.pending=true;
   run(c,early,550);check(LiveB8Power.requests==1,"committed screenshot alone cannot promote");
   LiveB8Brightness.pending=false;
   boolean beforePrimary=c.directStage().equals("BEFORE_PRIMARY"),secondaryCapture=c.captureDisplay()==1;
   for(int i=0;i<14;i++){
    step(c,early);
    if(c.directStage().equals("BEFORE_PRIMARY"))beforePrimary=true;
    if(c.captureDisplay()==1)secondaryCapture=true;
   }
   check(beforePrimary&&!secondaryCapture,"direct app and Home retain the projection through promotion without speculative D1 capture (home="+home+", opening="+opening+", sawPre="+beforePrimary+", D1="+secondaryCapture+")");
   check(LiveB8Power.panel==3-sourcePanel&&LiveB8Power.requests==2&&LiveB8Brightness.begun==1,"readiness switches early with either saved angle");
   check(c.primaryCheck()>0&&c.captureDisplay()==0&&!c.report().contains("HANDOFF_COMPLETE"),"native destination capture can follow switch");
   nativeReady=true;run(c,early,250);check(!c.report().contains("HANDOFF_COMPLETE"),"native window alone cannot reveal an uncaptured destination");
   captured=true;run(c,early,300);check(c.report().contains("HANDOFF_COMPLETE"),"exact destination image and native readiness complete the switch");
   int requests=LiveB8Power.requests;float peak=opening?40:130,sign=opening?1:-1;
   run(c,peak,150);
   for(int n=0;n<6;n++)run(c,n%2==0?peak-sign*4:peak,100);
   check(LiveB8Power.requests==requests,"jitter near the local turning point does not reswitch");
   LiveB8Brightness.pending=true;run(c,peak-sign*5,200);
   check(LiveB8Power.requests==requests,"deliberate reversal needs its own brightness preparation");
   LiveB8Brightness.pending=false;run(c,peak-sign*5,650);
   check(LiveB8Power.panel==sourcePanel&&LiveB8Power.requests==requests+1,"reverse leg can switch without crossing a preset hinge angle");
   stop(c);
  }
  for(boolean home:new boolean[]{true,false}){
   c=fresh();stop(c);LiveB8Task.home=home;c=directController();
   LiveB8Brightness.pending=true;
   run(c,155,200);run(c,130,200);run(c,138,200);
   LiveB8Brightness.pending=false;run(c,138,350);
   check(LiveB8Power.requests==1,"reversal before readiness cancels the obsolete target");
   run(c,130,700);check(LiveB8Power.requests==2&&LiveB8Power.panel==2,"next closing leg can proceed after canceled preparation");stop(c);
  }
  c=fresh();stop(c);LiveB8Task.home=true;c=directController();
  LiveB8Brightness.pending=true;nativeReady=false;captured=false;run(c,158,2100);
  LiveB8Brightness.pending=false;run(c,158,4500);
  check(c.ownsRoute()&&c.primaryCheck()>0&&!c.report().contains("ABORT"),"slow preparation does not consume the destination verification budget");
  captured=nativeReady=true;run(c,158,300);
  check(c.report().contains("HANDOFF_COMPLETE"),"late native destination can finish the early handoff");stop(c);
  c=fresh();stop(c);LiveB8Task.home=true;c=directController();
  LiveB8Brightness.failPrepared=true;run(c,164,450);
  check(c.report().contains("BRIGHTNESS_BLOCKED brightness unavailable")&&LiveB8Power.requests==1&&LiveB8Brightness.begun==0&&c.rotationSession()>0&&c.pauseReturnAt()==0,"missing samples block handoff without releasing animation or rotation");stop(c);
  for(int failure=0;failure<7;failure++){
   c=fresh();stop(c);LiveB8Task.home=true;c=directController();
   final LiveB8Controller atRequest=c;final int condition=failure;
   LiveB8Brightness.onBegin=()->{
    if(condition==0)throw new IllegalStateException("second panel rejected brightness");
    if(condition==1)atRequest.presentation(atRequest.sceneSession()-1,true,"inner",SystemClock.now,true,true,true,atRequest.sceneRevision(),SystemClock.now);
    if(condition==2)atRequest.presentation(atRequest.sceneSession(),true,"cover",SystemClock.now,true,true,true,atRequest.sceneRevision(),SystemClock.now);
    if(condition==3)atRequest.presentation(atRequest.sceneSession(),true,"inner",atRequest.sceneSession()-1,true,true,true,atRequest.sceneRevision(),SystemClock.now);
    if(condition==4)atRequest.update(true,170,SystemClock.now,172,85);
    if(condition==5)atRequest.presentation(atRequest.sceneSession(),true,"inner",SystemClock.now,true,true,true,atRequest.sceneRevision()-1,SystemClock.now);
    if(condition==6){long previousObservation=SystemClock.now;SystemClock.now+=300;atRequest.presentation(atRequest.sceneSession(),true,"inner",previousObservation,true,true,true,atRequest.sceneRevision(),previousObservation);}
   };
   run(c,150,400);check(LiveB8Power.requests==1&&!c.report().contains("READINESS_HANDOFF"),"failed brightness, stale source, wrong screen, old commit or reversed motion blocks final request");stop(c);
  }
  for(boolean opening:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();
   int sourcePanel=opening?2:1;LiveB8Power.panel=sourcePanel;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   float handoffAngle=opening?105:65;
   LiveB8Brightness.pending=true;captured=false;nativeReady=false;run(c,handoffAngle,500);
   check(LiveB8Power.panel==sourcePanel&&c.report().contains("BRIGHTNESS_GATE")&&LiveB8Brightness.begun==0,"Home gives pending preparation a bounded opportunity before remap, with no unprepared writer");
   LiveB8Brightness.pending=false;captured=false;nativeReady=false;run(c,handoffAngle,500);
   check(LiveB8Power.panel==3-sourcePanel&&!c.report().contains("HANDOFF_COMPLETE"),"Home remap cannot complete without captured native content");
   captured=true;run(c,handoffAngle,300);check(!c.report().contains("HANDOFF_COMPLETE"),"Home remap also waits for native window readiness");
   nativeReady=true;run(c,handoffAngle,400);check(c.report().contains("HANDOFF_COMPLETE"),"Home completes only after all original guards pass");stop(c);
  }
  for(boolean endpoint:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();LiveB8Brightness.pending=true;
   run(c,60,500);int canceled=LiveB8Brightness.closed,requests=LiveB8Power.requests;
   if(endpoint){step(c,0);check(c.sceneSession()==0&&LiveB8Brightness.closed>canceled&&LiveB8Power.requests==requests,"endpoint cancels pending Home refresh immediately without a primary request or image wait");}
   else{run(c,60,2300);check(LiveB8Power.requests==requests&&LiveB8Brightness.closed>canceled&&LiveB8Brightness.begun==0&&c.report().contains("BRIGHTNESS_BLOCKED brightness preparation timed out")&&c.rotationSession()>0&&c.pauseReturnAt()==0,"Home timeout stops only brightness preparation, preserving the ongoing fold");}
   stop(c);
  }
  for(boolean opening:new boolean[]{false,true})for(boolean prepared:new boolean[]{false,true}){
   c=fresh();stop(c);LiveB8Task.home=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   captured=false;nativeReady=false;
   run(c,opening?12:160,prepared?500:50);
   if(prepared)run(c,opening?105:65,400);
   LiveB8Power.cleanupFailures=2;LiveB8Power.base=opening?3:0;
   step(c,opening?180:0);
   check(c.sceneSession()==0,"endpoint revokes Home session with missing native capture");
   check(c.report().contains("HOME_ENDPOINT_BYPASS"),"endpoint bypass runs during preparation too");
   check(LiveB8Scene.last==null||LiveB8Scene.last.closed,"Home coverage detached before deferred power cleanup");
   check(c.ownsRoute(),"Home keeps ownership while cleanup is pending");
   LiveB8Power.cleanupFailures=0;run(c,opening?180:0,500);
   check(!c.ownsRoute()&&LiveB8Task.moves==0,"Home native fallback completes without capture");stop(c);
  }
  // Early reveal must not release route ownership while owned resources linger.
  c=fresh();stop(c);c=directController();LiveB8Scene.mapping=LiveB8Scene.removal=true;
  run(c,60,800);LiveB8Power.base=0;LiveB8Power.cleanupFailures=100;step(c,0);run(c,0,500);
  check(c.ownsRoute()&&LiveB8Scene.last.closed,"native visible while pending power cleanup still owns route");
  LiveB8Power.cleanupFailures=0;step(c,0);check(!c.ownsRoute(),"route released only after resource cleanup");stop(c);
  // Stationary gesture: both angle-selected panels, origins, Home/apps and handoff stages.
  for(boolean home:new boolean[]{false,true})for(int origin:new int[]{1,2})for(boolean afterHandoff:new boolean[]{false,true})for(int selected:new int[]{1,2}){
   c=fresh();stop(c);c=directController();LiveB8Scene.mapping=LiveB8Scene.removal=true;
   LiveB8Task.home=home;LiveB8Power.panel=origin;LiveB8Power.base=LiveB8Power.current=origin==1?3:1;
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
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);step(c,140);
  nativeReady=false;run(c,140,2000);check(!c.ownsRoute()&&c.sceneSession()==0,"unverified stationary return has bounded native fallback");
  int pauseRequests=LiveB8Power.requests;run(c,140,2000);check(LiveB8Power.requests==pauseRequests,"failed pause cannot restart while still");
  c.pause(false,1);nativeReady=true;step(c,135);check(c.ownsRoute(),"motion rearms after bounded pause fallback");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);run(c,140,200);c.pause(false,1);step(c,180);
  check(!c.ownsRoute()&&c.sceneSession()==0,"endpoint interrupts pause unwind immediately");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);run(c,140,800);
  check(c.paused(),"pause ready before lock test");android.view.IWindowManager.locked=true;step(c,140);
  check(!c.ownsRoute(),"lock releases stationary power choice");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);run(c,140,800);
  c.update(false,140,SystemClock.now,172,85);tick.invoke(c);check(!c.ownsRoute(),"disable releases stationary power choice");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);run(c,140,800);
  SystemClock.now+=2100;tick.invoke(c);check(!c.ownsRoute(),"lost reader lease releases stationary power choice");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);run(c,140,800);
  c.pause(false,1);step(c,0);check(!c.ownsRoute(),"full close clears a settled hold before any new session");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);step(c,140);
  c.pause(false,1);run(c,125,800);check(c.sceneSession()>0&&!c.paused()&&c.pauseReturnAt()==0,"movement during unwind finishes cleanup then starts a fresh current-pose session");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);run(c,140,800);
  pauseRequests=LiveB8Power.requests;((LiveB8Power)pf.get(c)).canceled=true;run(c,140,2000);
  check(!c.ownsRoute()&&LiveB8Power.requests==pauseRequests,"Android cancellation never reasserts the settled panel request");
  c.pause(false,1);step(c,135);check(c.ownsRoute(),"only new movement rearms a canceled stationary episode");stop(c);
  c=fresh();run(c,140,400);c.pause(true,1);run(c,140,800);
  check(c.paused()&&c.sceneSession()==0,"logical-window fallback follows the same readiness and timed dissolve");stop(c);
  c=fresh();stop(c);c=directController();run(c,140,400);c.pause(true,1);run(c,140,300);
  // A remap invalidates the ongoing dissolve, including a successful prior window token.
  run(c,140,50);check(c.pauseFadeAt()>0,"fade underway before mapping-loss test");
  LiveB8Task.primaryUnavailable=true;step(c,140);check(c.pauseFadeAt()==0&&LiveB8Scene.last.alpha==1,"mapping loss cancels dissolve and restores coverage");
  LiveB8Task.primaryUnavailable=false;run(c,140,600);check(c.paused(),"fresh repeated window evidence completes replacement dissolve");stop(c);
  // Home close removes images immediately, but lets the OS finish its own
  // cancellation before releasing the existing bounded power ownership.
  for(int ending=0;ending<5;ending++){
   c=fresh();stop(c);LiveB8Task.home=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();
   run(c,60,1000);
   check(LiveB8Power.panel==2&&c.report().contains("HANDOFF_COMPLETE"),"Home completes with validated brightness");
   java.lang.reflect.Field brightnessField=LiveB8Controller.class.getDeclaredField("brightness");
   brightnessField.setAccessible(true);((LiveB8Brightness)brightnessField.get(c)).running=false; // Expired writer.
   int releases=LiveB8Power.releases,requests=LiveB8Power.requests;
   step(c,0);
   check(c.sceneSession()==0&&LiveB8Scene.last.closed&&c.ownsRoute(),"closed Home is uncovered before power tail, independent of image readiness");
   check(LiveB8Power.releases==releases,"endpoint does not explicitly cancel ahead of native fold policy");
   if(ending==0){
    LiveB8Power p=(LiveB8Power)pf.get(c);p.canceled=true;LiveB8Power.base=LiveB8Power.current=0;
    run(c,0,75);check(c.ownsRoute(),"native cover needs distinct stable observations before lease release");run(c,0,75);
    check(!c.ownsRoute()&&c.report().contains("native cover stable 100ms"),"OS cancellation and cover mapping complete bounded power cleanup");
   }else if(ending==1){run(c,0,450);check(!c.ownsRoute()&&c.report().contains("400ms bound"),"missing OS cancellation cannot retain power ownership indefinitely");}
   else if(ending==2){step(c,4);check(!c.ownsRoute(),"new motion releases old tail without waiting for deadline");}
   else if(ending==3){android.view.IWindowManager.locked=true;step(c,0);check(!c.ownsRoute(),"lock releases tail immediately");}
   else {c.update(false,0,SystemClock.now,172,85);tick.invoke(c);check(!c.ownsRoute(),"disable releases tail immediately");}
   check(LiveB8Power.requests==requests,"power tail never reasserts a canceled state request");stop(c);
  }
  for(int ending=0;ending<5;ending++){
   c=fresh();stop(c);LiveB8Task.home=true;LiveB8Scene.mapping=LiveB8Scene.removal=true;
   c=directController();int preflights=LiveB8Brightness.homePrepared;
   run(c,60,1000);check(LiveB8Brightness.homePrepared>preflights&&LiveB8Brightness.begun==1,"Home restores B8 preparation and starts one validated writer");
   int writers=LiveB8Brightness.begun,closed=LiveB8Brightness.closed,tails=LiveB8Brightness.tails,ons=LiveB8Power.powerOns,requests=LiveB8Power.requests;
   step(c,0);check(c.sceneSession()==0&&LiveB8Scene.last.closed&&LiveB8Brightness.tails==tails+1&&LiveB8Brightness.closed==closed,"Home pixels released immediately while existing writer narrows to cover");
   if(ending==0){LiveB8Power p=(LiveB8Power)pf.get(c);p.canceled=true;LiveB8Power.base=LiveB8Power.current=0;run(c,0,150);}
   else if(ending==1)run(c,0,450);
   else if(ending==2)step(c,4);
   else if(ending==3){android.view.IWindowManager.locked=true;step(c,0);}
   else {c.update(false,0,SystemClock.now,172,85);tick.invoke(c);}
   check(!c.ownsRoute()&&LiveB8Brightness.closed==closed+1,"native settle, bound, reversal, lock and disable all terminate cover brightness tail");
   check(LiveB8Brightness.begun==writers&&LiveB8Power.powerOns==ons&&LiveB8Power.requests==requests,"cover tail creates no writer, ON renewal or state request");stop(c);
  }
  // Post-completion checks run on released layers, do nothing for a healthy
  // endpoint, and cannot hold up a new fold or replay a late correction.
  fresh();LiveB8Power.base=LiveB8Power.current=3;
  LiveB8Power ep=new LiveB8Power();LiveB8Task et=new LiveB8Task(root,"test.app");
  LiveB8EndpointCheck guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);
  check(guard.tick(()->true).startsWith("OK")&&guard.finished,"healthy endpoint completes in one read");
  check(LiveB8Task.endpointRepairs==0&&LiveB8Power.endpointReleases==0,"healthy endpoint does not write state or layout");
  LiveB8Task.endpointIssue="LAYOUT root=42 old bounds";
  guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);
  check(guard.tick(()->true).isEmpty(),"first mismatch is observation only");
  SystemClock.now+=25;guard.tick(()->true);check(LiveB8Task.endpointRepairs==0,"remap receives a short confirmation interval");
  SystemClock.now+=25;check(guard.tick(()->true).startsWith("REPAIR_SUBMITTED"),"persistent mismatch requests one repair");
  for(int n=0;n<6;n++){SystemClock.now+=50;guard.tick(()->true);}
  check(LiveB8Task.endpointRepairs==1,"unresolved mismatch never loops correction writes");
  LiveB8Task.endpointIssue="";SystemClock.now+=50;
  check(guard.tick(()->true).startsWith("REPAIR_VERIFIED")&&guard.finished,"repair requires readback");
  LiveB8Task.endpointIssue="LAYOUT root=42 old bounds";
  guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);guard.tick(()->true);
  SystemClock.now+=50;check(guard.tick(()->false).startsWith("SKIPPED")&&LiveB8Task.endpointRepairs==1,"new fold or stale input revokes pending repair");
  guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);guard.tick(()->true);
  LiveB8Task.endpointRecheck=()->LiveB8Power.base=0;SystemClock.now+=50;
  check(guard.tick(()->true).startsWith("SKIPPED")&&LiveB8Task.endpointRepairs==1,"base change during layout recheck prevents mutation");
  LiveB8Task.endpointRecheck=null;LiveB8Power.base=3;LiveB8Power.current=4;
  guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);guard.tick(()->true);SystemClock.now+=50;
  check(guard.tick(()->true).startsWith("REPAIR_SUBMITTED")&&LiveB8Power.endpointReleases==1,"confirmed leftover state releases own client");
  LiveB8Power.current=99;guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);guard.tick(()->true);SystemClock.now+=50;
  check(guard.tick(()->true).startsWith("UNRESOLVED")&&LiveB8Power.endpointReleases==1,"unknown state is never overridden");
  LiveB8Power.current=3;LiveB8Power.panel=2;
  guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);guard.tick(()->true);SystemClock.now+=50;
  check(guard.tick(()->true).startsWith("UNRESOLVED")&&LiveB8Power.requests==0,"unresolved physical mapping creates no new state request");
  LiveB8Power.panel=1;guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);guard.tick(()->true);SystemClock.now+=500;
  check(guard.tick(()->true).startsWith("UNRESOLVED")&&guard.finished,"postflight expires after 500 ms");
  et.selectNativePrimary();int repairs=LiveB8Task.endpointRepairs;
  guard=new LiveB8EndpointCheck(ep,et,3,"inner",SystemClock.now);guard.tick(()->true);
  SystemClock.now+=100;check(guard.tick(()->true).isEmpty()&&!guard.finished,"native app endpoint observes pending layout without a repair");
  SystemClock.now+=400;check(guard.tick(()->true).startsWith("UNRESOLVED")&&LiveB8Task.endpointRepairs==repairs,"native layout timeout is explicit and makes no task/layout writes");
  c=fresh();stop(c);c=directController();run(c,60,1000);LiveB8Power.base=0;run(c,0,1000);
  check(!c.ownsRoute()&&LiveB8Task.endpointReads>0&&c.report().contains("ENDPOINT_CHECK OK"),"real controller checks completed closing after releasing ownership");
  int endpointReads=LiveB8Task.endpointReads;run(c,0,2000);
  check(LiveB8Task.endpointReads==endpointReads,"healthy endpoint check never repeats at rest");stop(c);
  c=fresh();stop(c);c=directController();LiveB8Power.panel=2;LiveB8Power.base=LiveB8Power.current=1;run(c,60,1000);LiveB8Power.base=3;run(c,172,1000);
  check(!c.ownsRoute()&&c.report().contains("ENDPOINT_CHECK OK"),"real controller checks completed opening");stop(c);
  c=fresh();stop(c);c=directController();run(c,60,1000);LiveB8Power.base=0;
  LiveB8Scene.removal=false;run(c,0,1000);
  check(c.ownsRoute()&&LiveB8Task.endpointReads==0,"pending layer removal cannot start endpoint check");
  LiveB8Scene.removal=true;run(c,0,100);
  check(!c.ownsRoute()&&LiveB8Task.endpointReads>0,"confirmed layer removal admits endpoint check");stop(c);
  // alpha95: native app is ready but the effect still has its old rotation,
  // no matching texture, no new commit and no capture. Removing it cannot wait
  // for the broken effect to redraw. Exercise open, close and Android cancel.
  for(boolean opening:new boolean[]{false,true})for(boolean cancel:new boolean[]{false,true}){
   c=fresh();stop(c);c=directController();
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   nativeReady=false;run(c,opening?60:120,500);
   check(c.directStage().equals("AFTER_PRIMARY"),"stalled test reached real primary handoff");
   commit=false;nativeImage=false;captured=false;prepared=false;
   LiveB8Power.base=opening?3:0;
   if(cancel){java.lang.reflect.Field powerField=LiveB8Controller.class.getDeclaredField("power");powerField.setAccessible(true);LiveB8Power power=(LiveB8Power)powerField.get(c);power.nativeRelease();power.canceled=true;}
   float end=opening?180:0;run(c,end,200);
   check(c.nativeReadinessOnly()&&c.captureDisplay()==-1,"native return stops captures, renderer is not a prerequisite");
   int writes=LiveB8Power.powerOns;
   check(c.ownsRoute(),"missing app readiness still keeps bounded coverage");
   long checkId=c.primaryCheck();String expectedPhysical=c.primaryCheckPhysical();
   c.primaryReady(c.sceneSession(),checkId,"wrong",SystemClock.now);run(c,end,150);
   check(c.ownsRoute(),"wrong-panel readiness cannot release despite native return");
   c.primaryReady(c.sceneSession(),checkId,expectedPhysical,SystemClock.now);step(c,end);
   check(c.ownsRoute(),"one native observation cannot release coverage");
   nativeReady=true;run(c,end,200);
   check(!c.ownsRoute()&&LiveB8Scene.last.closed&&!c.report().contains("ABORT"),"fresh native evidence releases stuck effect without any renderer commit");
   check(LiveB8Power.powerOns==writes,"native return never renews concurrent panel power");stop(c);
  }
  c=fresh();stop(c);c=directController();nativeReady=false;run(c,120,500);
  commit=false;nativeImage=false;captured=false;prepared=false;LiveB8Power.base=0;
  run(c,0,1600);check(c.ownsRoute(),"unready native app is not reported ready on a timer");
  run(c,0,400);check(!c.ownsRoute()&&c.report().contains("timeout"),"failed native recovery removes coverage within 1800ms budget");stop(c);
  c=fresh();stop(c);c=directController();nativeReady=false;run(c,120,4200);
  check(c.directStage().equals("AFTER_PRIMARY"),"slow app still in moving handoff after three seconds");
  commit=false;nativeImage=false;captured=false;prepared=false;LiveB8Power.base=0;
  run(c,0,200);check(c.nativeReadinessOnly(),"old effect age cannot prevent return to native policy at endpoint");
  nativeReady=true;run(c,0,250);check(!c.ownsRoute()&&!c.report().contains("ABORT"),"late endpoint releases on fresh native readiness");stop(c);
  System.out.println("LiveB8ControllerTest: "+checks+" lifecycle/readiness checks passed (fake platform, not handset proof)");
 }
 static void nativeTaskHandoffRegression()throws Exception{
  // Both alpha106 failures: Instagram closing at ~162 degrees and Plex
  // opening at ~96 degrees. Exercise the real controller, including all
  // preflight/readiness gates; no guessed delay stands in for task ownership.
  for(boolean opening:new boolean[]{false,true}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Task.sourceChanged=false;
   LiveB8Controller c=directController();
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   float angle=opening?96:162;
   LiveB8Brightness.pending=true;run(c,angle,300);
   check(c.report().contains("NATIVE_APP_PREPARED")&&LiveB8Task.moves==0&&LiveB8Power.requests==1,"native app remains on D0 during brightness preparation");
   LiveB8Brightness.pending=false;paused=false;run(c,angle,100);
   check(LiveB8Power.requests==1,"producer must drain before native handoff");
   paused=true;pairVerified=false;run(c,angle,100);
   check(LiveB8Power.requests==1,"native route still requires rotation pair proof");
   pairVerified=true;nativeReady=false;captured=false;LiveB8Task.primaryUnavailable=true;
   run(c,angle,400);
   check(LiveB8Power.requests==2&&LiveB8Brightness.begun==1,"single primary switch follows accepted brightness and coverage");
   check(c.primaryCheck()==0&&c.captureDisplay()==-1,"remap with stale geometry cannot capture or complete");
   LiveB8Task.primaryUnavailable=false;run(c,angle,200);
   check(c.captureDisplay()==0&&!c.report().contains("HANDOFF_COMPLETE"),"settled native D0 requests destination capture, still waits for readiness");
   nativeReady=true;run(c,angle,250);
   check(!c.report().contains("HANDOFF_COMPLETE"),"native readiness cannot replace the destination screenshot");
   captured=true;run(c,angle,300);
   check(c.report().contains("HANDOFF_COMPLETE primary="+(opening?1:2)),"native app handoff completes in either direction");
   run(c,opening?30:170,900);
   check(LiveB8Power.requests==3&&LiveB8Task.moves==0&&LiveB8Task.retargets==0&&LiveB8Task.focuses==0,"reversal changes primary with zero task or focus writes");
   c.pause(true,opening?2:1);run(c,opening?30:170,900);stop(c);
   check(LiveB8Task.moves==0&&LiveB8Task.retargets==0&&LiveB8Task.focuses==0,"pause and teardown preserve native ownership");
  }
  LiveB8Controller previous=fresh();stop(previous);LiveB8Controller c=directController();
  LiveB8Brightness.onBegin=()->LiveB8Task.sourceChanged=true;
  run(c,162,800);
  check(LiveB8Power.requests==1&&!c.ownsRoute(),"foreground change during brightness priming prevents primary switch");
  check(LiveB8Task.moves==0&&LiveB8Task.retargets==0&&LiveB8Task.focuses==0,"aborted native route never restores or focuses a foreign task");
  LiveB8Task.sourceChanged=false;stop(c);
 }
 static void brightnessMotionRegression()throws Exception{
  // All three direct routes, both origins, and all pre-request brightness failures.
  // Travel lasts beyond the six-second transition timeout and never becomes still.
  for(int route=0;route<3;route++)for(boolean opening:new boolean[]{false,true})for(int failure=0;failure<3;failure++){
   LiveB8Controller previous=fresh();stop(previous);
   LiveB8Task.home=route==1;LiveB8Task.workspace=route==2;
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   LiveB8Controller c=directController();org.duofold.live.HingePausePolicy gesture=new org.duofold.live.HingePausePolicy();
   LiveB8Brightness.pending=true;float first=opening?6:166;
   policyRun(c,gesture,first,300);long session=c.sceneSession();int releases=LiveB8Power.releases;
   if(failure!=1)LiveB8Brightness.pending=false;
   if(failure==0)LiveB8Brightness.failPrepared=true;
   if(failure==2)LiveB8Brightness.onBegin=()->{throw new IllegalStateException("second physical panel rejected");};
   float last=first;
   for(int n=1;n<=140;n++){
    last=first+(opening?n:-n);policyRun(c,gesture,last,50);
    check(!gesture.held()&&c.pauseReturnAt()==0&&c.sceneSession()==session&&c.rotationSession()==session,
      "moving brightness failure retains session and rotation: route="+route+" opening="+opening+" failure="+failure+" angle="+last);
    check(LiveB8Power.releases==releases&&LiveB8Power.requests==1&&!LiveB8Scene.last.closed,
      "brightness failure does not release power, switch primary, or remove coverage");
   }
   check(c.report().contains("BRIGHTNESS_BLOCKED")&&!c.report().contains("NATIVE_FALLBACK")&&!c.report().contains("RECOVERY_UNWIND"),"failed preflight stays in existing handoff phase");
   int writes=LiveB8Brightness.begun,reads=LiveB8Brightness.earlyReads+LiveB8Brightness.homePrepared;
   LiveB8Brightness.pending=false;LiveB8Brightness.failPrepared=false;LiveB8Brightness.onBegin=null;
   // A late success or reversal must not silently restart the failed writer.
   policyRun(c,gesture,last+(opening?-8:8),200);
   policyRun(c,gesture,last,200);
   check(LiveB8Power.requests==1&&LiveB8Brightness.begun==writes&&LiveB8Brightness.earlyReads+LiveB8Brightness.homePrepared==reads,"failed session cannot retry brightness or promote after reversal");
   // Only a real 1.5-second stillness decision starts the ordinary return.
   policyRun(c,gesture,last,1000);check(c.pauseReturnAt()==0,"brightness failure cannot shorten the stillness timer");
   unwindAck=false;policyRun(c,gesture,last,700);
   check(gesture.held()&&c.pauseReturnAt()>0&&c.pauseFadeAt()==0&&!LiveB8Scene.last.closed,"true pause waits for the completed renderer unwind");
   unwindAck=true;policyRun(c,gesture,last,500);
   check(c.paused()&&c.sceneSession()==0&&LiveB8Power.panel==(last<90?2:1),"true pause returns onto angle-selected panel after failed brightness");
   stop(c);
  }
  // Reproduce the final Home closing trace: preparation fails around 45 degrees,
  // then 41 -> 32 -> 24 -> 18 -> 15 -> 11 -> 5. No recovery before the endpoint.
  for(int route=0;route<3;route++)for(boolean opening:new boolean[]{false,true}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Task.home=route==1;LiveB8Task.workspace=route==2;
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   LiveB8Controller c=directController();org.duofold.live.HingePausePolicy gesture=new org.duofold.live.HingePausePolicy();
   LiveB8Brightness.pending=true;policyRun(c,gesture,opening?6:166,300);
   for(float closingAngle:new float[]{145,130,116,102,84,76,65,56,45})policyRun(c,gesture,opening?172-closingAngle:closingAngle,200);
   LiveB8Brightness.pending=false;LiveB8Brightness.failPrepared=true;long session=c.sceneSession();int releases=LiveB8Power.releases;
   for(float closingAngle:new float[]{41,32,24,18,15,11,5,4,3,2,1}){
    policyRun(c,gesture,opening?172-closingAngle:closingAngle,100);
    check(c.sceneSession()==session&&c.rotationSession()==session&&c.pauseReturnAt()==0&&LiveB8Power.releases==releases,"reported motion and final few degrees retain animation and ownership");
   }
   LiveB8Power.base=opening?3:0;policyRun(c,gesture,opening?172:0,600);
   check(!c.ownsRoute()&&c.sceneSession()==0&&LiveB8Power.requests==1,"actual endpoint releases normally without failed-brightness primary request");stop(c);
  }
  // Reversal back to the source endpoint can put the controller in MOVING,
  // rather than BEFORE_PRIMARY. It must not try to arm a stopped brightness object.
  for(int route=0;route<3;route++)for(boolean opening:new boolean[]{false,true}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Task.home=route==1;LiveB8Task.workspace=route==2;
   int source=opening?2:1;LiveB8Power.panel=source;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   LiveB8Controller c=directController();LiveB8Brightness.failPrepared=true;
   run(c,opening?40:130,500);run(c,opening?20:150,100);
   check(c.livePresentation()&&c.pauseReturnAt()==0,"reversal preserves normal moving phase after failed brightness");
   LiveB8Power.base=opening?0:3;run(c,opening?0:172,600);
   check(!c.ownsRoute()&&!c.report().contains("ABORT")&&LiveB8Brightness.begun==0,"source endpoint finishes without rearming failed brightness");
   LiveB8Brightness.failPrepared=false;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   run(c,opening?8:164,900);
   check(c.report().contains("HANDOFF_COMPLETE")&&LiveB8Power.panel==3-source,"next fold gets fresh brightness preparation after endpoint cleanup");stop(c);
  }
  // Failure on a later leg must not get a new preflight on every reversal.
  for(int route=0;route<3;route++)for(boolean opening:new boolean[]{false,true}){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Task.home=route==1;LiveB8Task.workspace=route==2;
   LiveB8Power.panel=opening?2:1;LiveB8Power.base=LiveB8Power.current=opening?1:3;
   LiveB8Controller c=directController();run(c,opening?40:130,900);
   check(c.report().contains("HANDOFF_COMPLETE"),"first leg completes before later failure");
   int requests=LiveB8Power.requests,releases=LiveB8Power.releases;long session=c.sceneSession();
   LiveB8Brightness.failPrepared=true;run(c,opening?30:140,500);
   check(c.report().contains("BRIGHTNESS_BLOCKED"),"new leg validates brightness separately");
   LiveB8Brightness.failPrepared=false;
   run(c,opening?45:125,200);run(c,opening?25:145,7000);
   check(c.sceneSession()==session&&c.rotationSession()==session&&c.pauseReturnAt()==0&&LiveB8Power.requests==requests&&LiveB8Power.releases==releases,"later-leg failure retains session without retries, early rotation release or transition timeout");stop(c);
  }
  // An unresolved preflight is not permission to retain ownership indefinitely.
  for(int reason=0;reason<7;reason++){
   LiveB8Controller previous=fresh();stop(previous);LiveB8Task.home=reason%2==0;
   LiveB8Controller c=directController();LiveB8Brightness.failPrepared=true;run(c,150,500);
   int requests=LiveB8Power.requests;
   if(reason==0){java.lang.reflect.Field f=LiveB8Controller.class.getDeclaredField("power");f.setAccessible(true);((LiveB8Power)f.get(c)).canceled=true;run(c,140,1900);}
   if(reason==1){android.view.IWindowManager.locked=true;step(c,140);}
   if(reason==2){c.thermal(3);step(c,140);}
   if(reason==3){SystemClock.now+=3000;tick.invoke(c);}
   if(reason==4){c.update(false,140,SystemClock.now,172,85);tick.invoke(c);}
   if(reason==5){ack=false;run(c,140,900);}
   if(reason==6){run(c,140,30000);check(c.report().contains("30 second bound"),"brightness gate retains global session bound");}
   check(!c.ownsRoute()&&LiveB8Power.requests==requests,"cancellation, lock, thermal, stale input, disable, lost coverage or session bound terminates blocked fold: "+reason);stop(c);
  }
 }

}
