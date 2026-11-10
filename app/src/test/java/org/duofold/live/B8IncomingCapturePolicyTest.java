package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8IncomingCapturePolicyTest {
 @Test public void competingTaskRetryIsBoundedByCountAndRouteAge(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();
  String rejection="IllegalStateException: CAPTURE_GUARD_BEFORE COMPETING_TASK selected=21717 other=21715 display=0";
  p.reset(100);
  for(long now:new long[]{135,225,315})assertEquals(50,p.emptyRetryDelayMs(false,rejection,now));
  assertEquals(150,p.emptyRetryDelayMs(false,rejection,405));
  assertEquals(0,p.attempts());
  p.reset(2000);
  assertEquals(50,p.emptyRetryDelayMs(false,rejection,2100));
  assertEquals(150,p.emptyRetryDelayMs(false,rejection,2600));
 }
 @Test public void fasterCompetitorRetryIsSpecificToPreCaptureRejection(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();
  p.reset(100);p.emptyRetryDelayMs();
  assertEquals(150,p.emptyRetryDelayMs(false,"CAPTURE_GUARD_AFTER COMPETING_TASK selected=21717",200));
  assertEquals(150,p.emptyRetryDelayMs(false,"CAPTURE_GUARD_BEFORE TASK_MISSING",250));
 }
 @Test public void fadedAppRetriesNeverConsumeDarkContentFallback(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  for(int i=0;i<8;i++)assertEquals(50,p.emptyRetryDelayMs(false,"APP_CAPTURE_FADED",200+i*100));
  assertEquals(150,p.emptyRetryDelayMs(false,"APP_CAPTURE_FADED",1300));
  assertEquals(0,p.attempts());
  assertEquals(p.ACCEPT,p.evaluate(123,1400));
 }
 @Test public void homeFadeRetriesStayShortOnlyDuringInitialSettlingBudget(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);
  assertEquals(50,p.emptyRetryDelayMs(true,"CAPTURE_GUARD_BEFORE READY; draw-state unreported",200));
  assertEquals(50,p.emptyRetryDelayMs(true,"HOME_CONTENT_FADED; solid=0",500));
  assertEquals(50,p.emptyRetryDelayMs(true,"CAPTURE_GUARD_AFTER pending",1299));
  assertEquals(150,p.emptyRetryDelayMs(true,"HOME_CONTENT_FADED",1300));
  assertEquals(0,p.attempts());
 }
 @Test public void homeRetryDoesNotAcceleratePersistentFailuresOrOtherApps(){
  B8IncomingCapturePolicy p=new B8IncomingCapturePolicy();p.reset(100);p.emptyRetryDelayMs();
  assertEquals(150,p.emptyRetryDelayMs(false,"CAPTURE_GUARD_BEFORE pending",200));
  assertEquals(150,p.emptyRetryDelayMs(true,"Protected Home content omitted",200));
  assertEquals(150,p.emptyRetryDelayMs(true,"HOME_CONTENT_MISSING",200));
  assertEquals(150,p.emptyRetryDelayMs(true,null,200));
 }
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
