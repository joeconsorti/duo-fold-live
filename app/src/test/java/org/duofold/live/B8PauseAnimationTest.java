package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8PauseAnimationTest {
 @Test public void delayedDeliveryAndGpuStallsCannotSkipReverseAnimation(){
  for(boolean inner:new boolean[]{false,true}){B8PauseAnimation p=new B8PauseAnimation();float from=inner?120:40;
   B8PauseAnimation.Frame frame=p.frame(1000,1900,from,inner,172);
   assertEquals(from,frame.pose,0);
   assertEquals(from,p.frame(1000,3000,99,inner,172).pose,0);
   assertFalse(p.presented(frame,3000));
   frame=p.frame(1000,3001,99,inner,172);
   assertTrue(frame.elapsedMs<=32);assertTrue(inner?frame.pose<146:frame.pose>20);
   int count=1;long at=3001;boolean done=false;float last=from;
   while(!done&&count<100){frame=p.frame(1000,at,99,inner,172);assertTrue(inner?frame.pose>=last:frame.pose<=last);last=frame.pose;done=p.presented(frame,at+=16);count++;}
   assertTrue(done);assertTrue(count>=10);assertEquals(inner?172:0,last,0);
  }
 }
 @Test public void obsoleteAndDuplicateCompletionsCannotAdvanceNewerFrames(){
  B8PauseAnimation p=new B8PauseAnimation();B8PauseAnimation.Frame old=p.frame(1000,1000,60,false,172);
  p.presented(old,1016);long step=p.frame(1000,1017,99,false,172).elapsedMs;
  assertFalse(p.presented(old,2000));assertEquals(step,p.frame(1000,2001,99,false,172).elapsedMs);
  assertEquals(80,p.frame(1400,2100,80,false,172).pose,0);
  assertFalse(p.presented(old,2200));assertEquals(80,p.frame(1400,2300,20,false,172).pose,0);
 }
 @Test public void ordinaryFramesFollowTheirVisualAngle(){B8PauseAnimation p=new B8PauseAnimation();assertEquals(50,p.frame(0,1000,50,false,172).pose,0);assertEquals(60,p.frame(0,1100,60,false,172).pose,0);}
}
