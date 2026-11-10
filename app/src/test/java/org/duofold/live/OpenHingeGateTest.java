package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenHingeGateTest {
 @Test public void sustainedFlatReadingsCannotStartEvenOnFirstSample(){
  OpenHingeGate gate=new OpenHingeGate();
  for(int i=0;i<10000;i++){
   float effective=gate.filter(i%3==0?170f:171f,172f,true,true);
   assertEquals(172f,effective,0f);assertFalse(FoldThreshold.canStart(effective,172f));
  }
  assertEquals(1L,gate.suppressedEpisodes());
 }
 @Test public void observedEndpointChatterCannotRearmAnimation(){
  OpenHingeGate gate=new OpenHingeGate();
  for(float raw:new float[]{174,172,171,172,171,170,171,172,170,171})
   assertTrue(FoldThreshold.endpoint(gate.filter(raw,172,true,true),172));
 }
 @Test public void realClosingReleasesImmediatelyAndTracksUntilEndpoint(){
  OpenHingeGate gate=new OpenHingeGate();gate.filter(178,172,true,true);
  assertEquals(172f,gate.filter(169.5f,172,true,true),0f);
  for(float raw:new float[]{169,170,171,168,120,85,20,3,0,4,85,169,170,171,172,178})
   assertEquals(raw,gate.filter(raw,172,true,true),0f);
  assertEquals(172f,gate.filter(171,172,true,true),0f);
 }
 @Test public void openingKeepsExistingEndpoint(){
  OpenHingeGate gate=new OpenHingeGate();
  for(float raw:new float[]{0,4,25,85,150,169,170,171,171.9f,172})
   assertEquals(raw,gate.filter(raw,172,true,true),0f);
 }
 @Test public void disabledIsIdentityAndEnablingRearms(){
  OpenHingeGate gate=new OpenHingeGate();
  assertEquals(171f,gate.filter(171,172,false,true),0f);
  assertEquals(172f,gate.filter(171,172,true,true),0f);
  assertEquals(169f,gate.filter(169,172,true,true),0f);
  assertEquals(171f,gate.filter(171,172,true,true),0f);
  assertEquals(171f,gate.filter(171,172,false,true),0f);
  assertEquals(172f,gate.filter(171,172,true,true),0f);
 }
 @Test public void staleOrInvalidSamplesCannotReleaseOpenLatch(){
  OpenHingeGate gate=new OpenHingeGate();
  assertTrue(Float.isNaN(gate.filter(140,172,true,false)));
  assertEquals(172f,gate.filter(171,172,true,true),0f);
  assertEquals(172f,gate.filter(140,172,true,false),0f);
  assertTrue(Float.isNaN(gate.filter(Float.NaN,172,true,true)));
  assertTrue(Float.isNaN(gate.filter(Float.POSITIVE_INFINITY,172,true,true)));
  assertEquals(172f,gate.filter(171,172,true,true),0f);
 }
 @Test public void staleEndpointCannotRelatchActiveMotion(){
  OpenHingeGate gate=new OpenHingeGate();gate.filter(140,172,true,true);
  assertEquals(140f,gate.filter(178,172,true,false),0f);
  assertEquals(171f,gate.filter(171,172,true,true),0f);
 }
 @Test public void resetAndThresholdChangesRearmConservatively(){
  OpenHingeGate gate=new OpenHingeGate();gate.filter(140,172,true,true);gate.reset();
  assertEquals(172f,gate.filter(171,172,true,true),0f);
  gate.filter(140,172,true,true);
  assertEquals(179f,gate.filter(178,179,true,true),0f);
  assertEquals(176f,gate.filter(176,179,true,true),0f);
 }
 @Test public void configuredThresholdsKeepThreeDegreeSeparation(){
  for(float open=165;open<=179;open++){
   OpenHingeGate gate=new OpenHingeGate();
   assertEquals(open,gate.filter(open-2,open,true,true),0f);
   assertEquals(open-3,gate.filter(open-3,open,true,true),0f);
   assertEquals(open-1,gate.filter(open-1,open,true,true),0f);
   gate.filter(open,open,true,true);
   assertEquals(open,gate.filter(open-1,open,true,true),0f);
  }
  assertEquals(169f,OpenHingeGate.startAngle(Float.NaN),0f);
 }
 @Test public void physicalClosedVetoAndClosedJitterStillWin(){
  PhysicalHingeGate physical=new PhysicalHingeGate();ClosedHingeGate closed=new ClosedHingeGate();OpenHingeGate open=new OpenHingeGate();
  open.filter(178,172,true,true);
  assertEquals(0f,open.filter(closed.filter(physical.filter(171,1000,0,1000,1000),2,true,true),172,true,true),0f);
  for(float raw:new float[]{0,1,3,2,3})assertEquals(0f,open.filter(closed.filter(raw,2,true,true),172,true,true),0f);
  assertEquals(4f,open.filter(closed.filter(4,2,true,true),172,true,true),0f);
 }
}
