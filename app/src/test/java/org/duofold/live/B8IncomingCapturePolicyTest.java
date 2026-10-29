package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8IncomingCapturePolicyTest {
 @Test public void emptyRetryDoesNotConsumeDarkCandidateBudget(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(50,p.emptyRetryDelayMs());assertEquals(150,p.emptyRetryDelayMs());
  assertEquals(0,p.attempts());assertEquals(p.RETRY,p.evaluate(1024,200));
  assertEquals(50,p.retryDelayMs());p.reset(300);assertEquals(50,p.emptyRetryDelayMs());
 }
 @Test public void blackThenContentDoesNotFreezeFirstCapture(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(p.RETRY,p.evaluate(1024,200));
  assertEquals(p.RETRY,p.evaluate(1024,400));
  assertEquals(p.ACCEPT,p.evaluate(900,600));
 }
 @Test public void legitimateBlackHasFiniteAttemptBudget(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  for(int i=0;i<4;i++)assertEquals(p.RETRY,p.evaluate(1024,101+i));
  assertEquals(p.FALLBACK,p.evaluate(1024,700));
 }
 @Test public void fastRetriesCannotExhaustDarkProtectionEarly(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  for(int i=0;i<8;i++)assertEquals(p.RETRY,p.evaluate(1024,100+i*50));
  assertEquals(p.FALLBACK,p.evaluate(1024,700));
 }
 @Test public void onlyFirstDarkRetryIsAccelerated(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  p.evaluate(1024,200);assertEquals(50,p.retryDelayMs());
  p.evaluate(1024,300);assertEquals(150,p.retryDelayMs());
  p.reset(400);p.evaluate(1024,500);assertEquals(50,p.retryDelayMs());
 }
 @Test public void slowCaptureHasFiniteTimeBudget(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(p.FALLBACK,p.evaluate(1024,1300));
 }
 @Test public void missingMeasurementNeverCountsAsContentPass(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(p.RETRY,p.evaluate(-1,200));
  assertEquals(p.FALLBACK,p.evaluate(-1,1300));
 }
 @Test public void newRevisionGetsNewBudget(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(p.FALLBACK,p.evaluate(1024,1300));p.reset(1400);
  assertEquals(p.RETRY,p.evaluate(1024,1500));assertEquals(1,p.attempts());
 }
 @Test public void reportedNearBlackCandidateGetsSettlingBudget(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(p.RETRY,p.evaluate(1023,384));
  assertEquals(p.RETRY,p.evaluate(1000,500));
  assertEquals(p.ACCEPT,p.evaluate(999,650));
 }
 @Test public void legitimateNearBlackStillHasBoundedFallback(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(p.FALLBACK,p.evaluate(1023,1300));
 }
}
