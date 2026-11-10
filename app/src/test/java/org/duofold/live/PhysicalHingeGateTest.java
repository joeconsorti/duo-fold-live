package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class PhysicalHingeGateTest {
 @Test public void replayClosedPhone142DegreeBurstsCannotRearmWakeOrOpenJitterLatch(){
  PhysicalHingeGate g=new PhysicalHingeGate();ClosedHingeGate jitter=new ClosedHingeGate();FoldWakeEpisode wake=new FoldWakeEpisode();
  wake.observe(3);assertTrue(wake.take()); // genuine close already owns its single recovery
  long now=1000;
  for(int cycle=0;cycle<4;cycle++)for(float raw:new float[]{0,142,142,142,0}){
   now+=100;wake.observe(0);float accepted=g.filter(raw,now,0,now,now);
   assertEquals(0f,accepted,0f);assertEquals(0f,jitter.filter(accepted,2,true,true),0f);
   assertFalse(wake.take());
  }
  assertTrue(g.report().contains("rejected episodes=4"));
  now+=100;wake.observe(1);assertEquals(6f,g.filter(6,now,1,now,now),0f);assertTrue(wake.take());assertFalse(wake.take());
 }
 @Test public void closedVetoDoesNotDependOnJitterSetting(){
  PhysicalHingeGate g=new PhysicalHingeGate();ClosedHingeGate jitter=new ClosedHingeGate();
  assertEquals(142f,jitter.filter(g.filter(142,1000,3,1000,1000),2,false,true),0f);
  assertEquals(0f,jitter.filter(g.filter(142,1100,0,1100,1100),2,false,true),0f);
 }
 @Test public void readFailureCannotReleaseClosedLatch(){
  PhysicalHingeGate g=new PhysicalHingeGate();assertTrue(Float.isNaN(g.filter(142,1000,-1,0,1000)));
  assertEquals(0f,g.filter(0,1100,0,1100,1100),0f);
  assertEquals(0f,g.filter(142,2000,-1,0,2000),0f);
  assertEquals(0f,g.filter(142,2000,1,1100,2000),0f);
  assertEquals(6f,g.filter(6,2100,1,2100,2100),0f);
 }
 @Test public void reopeningRequiresReplyNewerThanLastClosedObservation(){
  PhysicalHingeGate g=new PhysicalHingeGate();g.filter(142,1000,0,1010,1010);
  assertTrue(Float.isNaN(g.filter(142,1000,1,1110,1110)));
  assertEquals(7f,g.filter(7,1120,1,1110,1120),0f);
 }
 @Test public void normalOpenAndClosingMotionPreserved(){
  PhysicalHingeGate g=new PhysicalHingeGate();long t=1000;
  for(float angle:new float[]{4,6,20,90,140,177,168,120,60,5}){t+=100;assertEquals(angle,g.filter(angle,t,angle<90?1:3,t,t),0f);}
  assertEquals(0f,g.filter(2,t+100,0,t+100,t+100),0f);
 }
 @Test public void unknownBaseCannotMintAnotherWakeLease(){
  FoldWakeEpisode w=new FoldWakeEpisode();w.observe(-1);assertFalse(w.take());w.observe(3);assertTrue(w.take());
  for(int base:new int[]{-1,3,1,3,0,-1,0}){w.observe(base);assertFalse(w.take());}
  w.observe(1);assertTrue(w.take());w.observe(3);assertFalse(w.take());
 }
 @Test public void physicalCycleCanRearmEvenWithoutAnAngleReplyDuringSleep(){
  FoldWakeEpisode w=new FoldWakeEpisode();w.observe(3);assertTrue(w.take());
  // Base observations are delivered separately from fresh-angle listeners.
  w.observe(0);w.observe(-1);assertFalse(w.take());w.observe(1);assertTrue(w.take());
 }

}
