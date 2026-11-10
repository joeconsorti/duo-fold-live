package org.duofold.live.experimental.b8;
import java.io.File;
import android.os.SystemClock;
import org.duofold.live.ClosedHingeGate;
import org.duofold.live.OpenHingeGate;
import org.duofold.live.PhysicalHingeGate;
import org.duofold.live.HingePausePolicy;
import static org.duofold.live.experimental.b8.LiveB8ControllerTest.*;

/** Replay raw near-flat reports through the real filters, pause policy and controller. */
public final class EndpointJitterReplayTest {
 static final class Pipeline {
  final ClosedHingeGate closed=new ClosedHingeGate();
  final OpenHingeGate open=new OpenHingeGate();
  final PhysicalHingeGate physical=new PhysicalHingeGate();
  final HingePausePolicy pause=new HingePausePolicy();
  final LiveB8Controller controller;
  Pipeline()throws Exception{LiveB8Controller reset=fresh();stop(reset);controller=directController();}
  void feed(float raw,boolean enabled)throws Exception{
   long at=SystemClock.now+25;
   float a=physical.filter(raw,at,LiveB8Power.base,at,at);
   a=open.filter(closed.filter(a,2,enabled,true),172,enabled,true);
   policyStep(controller,pause,a);
  }
 }
 public static void main(String[] args)throws Exception{
  root=new File(args[0]);tick=LiveB8Controller.class.getDeclaredMethod("tick");tick.setAccessible(true);
  Pipeline off=new Pipeline();off.feed(172,false);off.feed(171,false);
  check(off.controller.sceneSession()>0&&LiveB8Power.requests>0,"control: disabling protection reproduces the reported false start");stop(off.controller);
  for(boolean startsAtEndpoint:new boolean[]{false,true}){
   Pipeline on=new Pipeline();
   if(startsAtEndpoint)on.feed(172,true);
   int requests=LiveB8Power.requests,powerOns=LiveB8Power.powerOns,scenes=LiveB8Scene.starts;
   for(int i=0;i<2400;i++)on.feed(new float[]{171,171,172,171,170,171}[i%6],true);
   check(on.controller.sceneSession()==0&&LiveB8Power.requests==requests&&LiveB8Power.powerOns==powerOns&&LiveB8Scene.starts==scenes,
    "60s of flat jitter creates no session, capture scene or power request; endpoint first="+startsAtEndpoint);
   on.feed(169,true);
   check(on.controller.sceneSession()>0&&LiveB8Power.requests>requests,"real close starts on the first 169-degree reply");
   for(int i=0;i<40;i++)on.feed(168-i,true);
   check(LiveB8Power.panel==2,"genuine closing still prepares the cover");
   for(int i=0;i<80;i++)on.feed(172,true);
   check(on.controller.sceneSession()==0,"open endpoint still completes the session");
   requests=LiveB8Power.requests;powerOns=LiveB8Power.powerOns;
   for(int i=0;i<160;i++)on.feed(i%2==0?171:172,true);
   check(on.controller.sceneSession()==0&&LiveB8Power.requests==requests&&LiveB8Power.powerOns==powerOns,
    "171-172 cannot restart immediately after cleanup");stop(on.controller);
  }
  System.out.println("Endpoint jitter replay passed: disabled reproduces; enabled blocks flat start/restart; real closing remains immediate.");
 }
}
