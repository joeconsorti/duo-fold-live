package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ClosingMirrorFadePolicyTest {
 @Test public void lateMirrorGetsFullRevealAfterPanelAndSubmissionSettle(){
  ClosingMirrorFadePolicy p=new ClosingMirrorFadePolicy();
  assertEquals(0,p.opacity(0,true,true,-1,0),0);
  assertEquals(1,p.opacity(10,false,false,-1,0),0);
  assertEquals(1,p.opacity(200,false,true,-1,0),0);
  assertEquals(1,p.opacity(400,false,true,400,0),0);
  assertEquals(1,p.opacity(431,false,true,400,0),0);
  assertEquals(1,p.opacity(432,false,true,400,0),0);
  assertEquals(.5f,p.opacity(522,false,true,400,0),.001);
  assertEquals(0,p.opacity(612,false,true,400,0),0);
 }
 @Test public void coverStartupAndOpeningAreUnaffected(){
  ClosingMirrorFadePolicy p=new ClosingMirrorFadePolicy();
  assertEquals(0,p.opacity(0,false,true,0,1),0);
  assertEquals(0,p.opacity(20,true,true,0,1),0);
  assertEquals(1,p.opacity(30,false,true,30,1),0);
  assertEquals(0,p.opacity(40,true,true,30,1),0);
 }
 @Test public void offInterruptsRevealAndDurationUsesExistingPreference(){
  ClosingMirrorFadePolicy p=new ClosingMirrorFadePolicy();p.opacity(0,true,true,-1,1);
  p.opacity(10,false,true,10,1);p.opacity(130,false,true,10,1);
  assertEquals(.5f,p.opacity(380,false,true,10,1),.001);
  assertEquals(1,p.opacity(390,false,false,10,1),0);
  assertEquals(1,p.opacity(1000,false,true,10,1),0);
  assertEquals(1,p.opacity(1120,false,true,10,1),0);
  assertEquals(.5f,p.opacity(1370,false,true,10,1),.001);
 }
 @Test public void oldMirrorCannotReleaseHoldAndMissingMirrorHasBoundedFallback(){
  ClosingMirrorFadePolicy p=new ClosingMirrorFadePolicy();p.opacity(100,true,true,10,0);
  p.opacity(110,false,true,10,0);
  assertEquals(1,p.opacity(500,false,true,10,0),0);
  assertEquals(1,p.opacity(1010,false,true,10,0),0);
  assertEquals(0,p.opacity(1190,false,true,10,0),0);
  p.reset();assertEquals(0,p.opacity(1200,false,true,10,0),0);
 }
}
