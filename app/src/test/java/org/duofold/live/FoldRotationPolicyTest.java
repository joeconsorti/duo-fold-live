package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class FoldRotationPolicyTest {
 @Test public void pauseUnwindDissolveAndResumeAtPartialAnglesInBothModes(){
  for(int mode:new int[]{0,2})for(float angle:new float[]{40,120}){
   FoldRotationPolicy rotation=new FoldRotationPolicy();HingePausePolicy pause=new HingePausePolicy();
   for(long t=1000;t<=2250;t+=50){
    pause.update(true,t,t,angle,172,1,mode);
    assertTrue(rotation.update(t,FoldRotationPolicy.animationEnabled(true,true,true,pause.settled(t),mode==2,false),true,angle,172));
   }
   assertTrue(pause.held());assertFalse(pause.settled(2529));assertTrue(pause.settled(2530));
   // Duo's snapshot dissolve can outlive the hinge's 280ms unwind.
   assertEquals(mode==2,rotation.update(2530,FoldRotationPolicy.animationEnabled(true,true,true,true,mode==2,false),true,angle,172));
   assertFalse(rotation.update(2800,FoldRotationPolicy.animationEnabled(true,true,true,true,false,false),true,angle,172));
   pause.update(true,2850,2850,angle+4.99f,172,1,mode);assertTrue(pause.held());
   assertFalse(rotation.update(2850,FoldRotationPolicy.animationEnabled(true,true,true,pause.settled(2850),false,false),true,angle+4.99f,172));
   pause.update(true,2900,2900,angle+5f,172,1,mode);assertFalse(pause.held());
   assertTrue(rotation.update(2900,FoldRotationPolicy.animationEnabled(true,true,true,false,false,false),true,angle+5f,172));
   assertFalse(rotation.update(2950,true,true,172,172));
  }
 }
 @Test public void startingDisabledCannotBlockMidAngleAnimation(){
  FoldRotationPolicy p=new FoldRotationPolicy();assertFalse(p.update(0,false,false,Float.NaN,172));
  assertTrue(p.update(10,true,true,90,172));assertFalse(p.update(20,false,true,90,172));
  assertTrue(p.update(30,true,true,95,172));
 }
 @Test public void offLockedThermalAndProbeNeverAcquireEvenWithRetainedScene(){
  assertFalse(FoldRotationPolicy.animationEnabled(false,true,true,false,true,false));
  assertFalse(FoldRotationPolicy.animationEnabled(true,false,true,false,true,false));
  assertFalse(FoldRotationPolicy.animationEnabled(true,true,false,false,true,false));
  assertFalse(FoldRotationPolicy.animationEnabled(true,true,true,false,true,true));
 }
 @Test public void unfoldsAndReleasesAtEndpoint(){FoldRotationPolicy p=new FoldRotationPolicy();assertFalse(p.update(0,true,true,0,172));assertTrue(p.update(10,true,true,4,172));assertTrue(p.update(200,true,true,100,172));assertFalse(p.update(300,true,true,172,172));assertFalse(p.update(749,true,true,179,172));assertFalse(p.update(750,true,true,179,172));}
 @Test public void foldsAndRestoresAtClosedEndpoint(){FoldRotationPolicy p=new FoldRotationPolicy();assertFalse(p.update(0,true,true,179,172));assertTrue(p.update(10,true,true,170,172));assertFalse(p.update(100,true,true,0,172));assertFalse(p.update(550,true,true,0,172));}
 @Test public void reversalRetainsHold(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,40,172);p.update(10,true,true,179,172);assertTrue(p.update(200,true,true,150,172));assertTrue(p.update(600,true,true,60,172));}
 @Test public void staleAnglesReleaseAndFreshAnimationCanRearm(){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,50,172));assertTrue(p.update(50,true,false,50,172));assertFalse(p.update(1550,true,false,50,172));assertTrue(p.update(1560,true,true,50,172));p.update(1570,true,true,179,172);assertTrue(p.update(1580,true,true,150,172));}
 @Test public void stoppedOrLockedDeviceReleases(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,50,172);assertFalse(p.update(10,false,true,50,172));}
 @Test public void freshAnimationRemainsLockedPastThirtySeconds(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,50,172);assertTrue(p.update(30000,true,true,50,172));assertTrue(p.update(30001,true,true,50,172));p.update(30002,true,true,0,172);assertTrue(p.update(30003,true,true,10,172));}
 @Test public void invalidAnglesRelease(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,50,172);assertTrue(p.update(10,true,true,Float.NaN,172));assertFalse(p.update(1510,true,true,Float.NaN,172));}
 @Test public void handoffGapDoesNotUnlock(){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,80,172));assertTrue(p.update(100,true,false,80,172));assertTrue(p.update(900,true,true,150,172));assertFalse(p.update(1000,true,true,179,172));assertFalse(p.update(1450,true,true,179,172));}
 @Test public void configuredOpenThresholdReleasesImmediately(){for(float open:new float[]{165,172,179}){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,100,open));assertFalse(p.update(1,true,true,open,open));}}
 @Test public void configuredClosedAngleReleasesImmediately(){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,20,172));assertFalse(p.update(1,true,true,FoldThreshold.effectiveAngle(2,2),172));}
 @Test public void endpointJitterDoesNotReacquire(){FoldRotationPolicy p=new FoldRotationPolicy();assertFalse(p.update(0,true,true,0,172));assertFalse(p.update(1,true,true,.4f,172));assertFalse(p.update(2,true,true,179,172));}
}
