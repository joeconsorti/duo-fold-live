package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class InnerRevealReadinessTest {
 private HandoffFadePolicy start(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.renderer(true,172);
  p.opacity(0,false,98,true,true,-1,false);
  p.opacity(10,true,98,true,false,-1,false);
  p.opacity(20,true,98,true,true,-1,false);
  return p;
 }
 private float glass(HandoffFadePolicy p,long now,long commit,long capture){
  return p.opacity(now,true,100,true,true,commit,true,HandoffFadePolicy.GLASS_COMMITTED,capture);
 }
 @Test public void parentDrawNeverProvesGlassReady(){
  HandoffFadePolicy p=start();
  assertEquals(1,p.opacity(100,true,100,true,true,99,true),0);
  assertEquals(1,p.opacity(800,true,100,true,true,799,true),0);
  assertTrue(p.readiness.startsWith("waiting"));
 }
 @Test public void freshCaptureAndCommitAllowTwoMsSettle(){
  HandoffFadePolicy p=start();
  assertEquals(1,glass(p,25,25,22),0);
  assertEquals(1,glass(p,26,25,22),0);
  assertEquals(1,glass(p,27,25,22),0); // fade starts at zero progress
  assertEquals(.5f,glass(p,117,25,22),.001);
  assertEquals(0,glass(p,207,25,22),0);
  assertEquals("fresh content capture + glass frame committed",p.readiness);
 }
 @Test public void preOnCaptureCannotPassWithNewCommit(){
  HandoffFadePolicy p=start();
  assertEquals(1,glass(p,100,99,19),0);
  assertEquals(1,glass(p,150,149,-1),0);
  assertTrue(p.readiness.startsWith("waiting"));
 }
 @Test public void futureAndStaleEvidenceCannotPass(){
  HandoffFadePolicy p=start();
  assertEquals(1,glass(p,100,101,22),0);
  assertEquals(1,glass(p,100,99,100),0);
  assertEquals(1,glass(p,500,499,22),0);
 }
 @Test public void fullyOpenRequiresItsOwnCommittedClear(){
  HandoffFadePolicy p=start();
  assertEquals(1,p.opacity(40,true,178,true,true,39,true),0);
  assertEquals(1,p.opacity(45,true,171,true,true,44,true,HandoffFadePolicy.ENDPOINT_COMMITTED,-1),0);
  assertEquals(1,p.opacity(50,true,178,true,true,50,true,HandoffFadePolicy.ENDPOINT_COMMITTED,-1),0);
  assertEquals(1,p.opacity(52,true,178,true,true,50,true,HandoffFadePolicy.ENDPOINT_COMMITTED,-1),0);
  assertEquals(.5f,p.opacity(142,true,178,true,true,50,true,HandoffFadePolicy.ENDPOINT_COMMITTED,-1),.001);
 }
 @Test public void anotherOffIntervalInvalidatesEvidence(){
  HandoffFadePolicy p=start();glass(p,25,25,22);glass(p,27,25,22);
  assertEquals(1,p.opacity(30,true,100,true,false,25,true,HandoffFadePolicy.GLASS_COMMITTED,22),0);
  assertEquals(1,glass(p,40,25,22),0);
  assertEquals(1,glass(p,50,49,22),0);
  assertEquals(1,glass(p,60,59,45),0);
  assertEquals(1,glass(p,61,59,45),0);
  assertEquals(.5f,glass(p,151,59,45),.001);
 }
 @Test public void emergencyTimeoutIsExplicitlyUnconfirmed(){
  HandoffFadePolicy p=start();
  assertEquals(1,p.opacity(919,true,100,true,true,-1,false),0);
  assertEquals(1,p.opacity(920,true,100,true,true,-1,false),0);
  assertEquals("TIMEOUT recovery; readiness NOT confirmed",p.readiness);
  assertEquals(0,p.opacity(1100,true,100,true,true,-1,false),0);
 }
 @Test public void coverPathStillUsesExistingDrawSettle(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.renderer(true,172);
  p.opacity(0,true,94,true,true,-1,true);
  p.opacity(10,false,94,true,true,-1,true);
  assertEquals(1,p.opacity(30,false,94,true,true,30,false),0);
  assertEquals(1,p.opacity(42,false,94,true,true,42,false),0);
  assertEquals(.5f,p.opacity(132,false,94,true,true,132,false),.001);
 }
}
