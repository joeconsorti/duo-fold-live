package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ClosedHingeGateTest {
 @Test public void closedNoiseCannotStartEvenWhenItPersists(){
  ClosedHingeGate gate=new ClosedHingeGate();
  for(int i=0;i<3000;i++)assertEquals(0f,gate.filter(3f,2f,true,true),0f);
  assertFalse(gate.open());assertEquals(1L,gate.suppressedEpisodes());
  for(float raw:new float[]{1,3,2,3,2,3})assertEquals(0f,gate.filter(raw,2f,true,true),0f);
 }
 @Test public void genuineOpeningIsImmediateAndClosingUsesLowerBoundary(){
  ClosedHingeGate gate=new ClosedHingeGate();
  assertEquals(4f,gate.filter(4f,2f,true,true),0f);
  for(float raw:new float[]{20,85,178,170,85,4,3})assertEquals(raw,gate.filter(raw,2f,true,true),0f);
  assertEquals(0f,gate.filter(2f,2f,true,true),0f);
  assertEquals(0f,gate.filter(3f,2f,true,true),0f);
 }
 @Test public void offRestoresOriginalBehaviorAndOnRearmsConservatively(){
  ClosedHingeGate gate=new ClosedHingeGate();
  assertEquals(3f,gate.filter(3f,2f,false,true),0f);
  assertEquals(0f,gate.filter(3f,2f,true,true),0f);
  assertEquals(4f,gate.filter(4f,2f,true,true),0f);
  assertEquals(3f,gate.filter(3f,2f,false,true),0f);
 }
 @Test public void invalidOrStaleReadingsCannotChangeLatch(){
  ClosedHingeGate gate=new ClosedHingeGate();
  assertTrue(Float.isNaN(gate.filter(8f,2f,true,false)));assertFalse(gate.open());
  assertEquals(0f,gate.filter(3f,2f,true,true),0f);
  gate.filter(4f,2f,true,true);
  assertEquals(4f,gate.filter(0f,2f,true,false),0f);assertTrue(gate.open());
  assertTrue(Float.isNaN(gate.filter(Float.NaN,2f,true,true)));assertTrue(gate.open());
  assertEquals(3f,gate.filter(3f,2f,true,true),0f);
 }
 @Test public void restartAndThresholdChangesCannotRetainOldOpenLatch(){
  ClosedHingeGate gate=new ClosedHingeGate();gate.filter(178f,2f,true,true);
  gate.reset();assertEquals(0f,gate.filter(3f,2f,true,true),0f);
  assertEquals(178f,gate.filter(178f,2f,true,true),0f);
  assertEquals(0f,gate.filter(6f,5f,true,true),0f);
  assertEquals(7f,gate.filter(7f,5f,true,true),0f);
  assertEquals(6f,gate.filter(6f,5f,true,true),0f);
  assertEquals(0f,gate.filter(5f,5f,true,true),0f);
 }
}
