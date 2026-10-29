package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeCapturePacingTest {
 @Test public void failedPreflightDoesNotConsumeAScreenshotSlot(){
  HomeCapturePacing p=new HomeCapturePacing();
  assertEquals(0,p.delayMs(1,1000));
  // A rejected guard does not call submitted. Retry promptly when it clears.
  assertEquals(0,p.delayMs(1,1050));
  p.submitted(1,1050);assertEquals(350,p.delayMs(1,1050));
 }
 @Test public void intervalIsMeasuredFromTheRealRequest(){
  HomeCapturePacing p=new HomeCapturePacing();
  assertEquals(0,p.delayMs(1,1000));
  p.submitted(1,1180); // time spent validating Home is not screenshot spacing
  assertEquals(180,p.delayMs(1,1350));
  assertEquals(17,p.delayMs(1,1513));
  assertEquals(0,p.delayMs(1,1530));
 }
 @Test public void newWindowCanPrepareWhileTheSameWindowStillWaits(){
  HomeCapturePacing p=new HomeCapturePacing();p.submitted(1,1000);
  assertEquals(0,p.delayMs(2,1100));p.submitted(2,1100);
  assertEquals(250,p.delayMs(1,1100));
  assertEquals(350,p.delayMs(2,1100));
  assertEquals(0,p.delayMs(1,1350));
  assertEquals(100,p.delayMs(2,1350));
 }
 @Test public void physicalRemapWithSameWindowStillObservesTheInterval(){
  HomeCapturePacing p=new HomeCapturePacing();p.submitted(7,1000);
  // Physical identity is deliberately not the pacing key.
  assertEquals(300,p.delayMs(7,1050));
 }
 @Test public void firmwareRateLimitBacksOffAgainFromItsResponse(){
  HomeCapturePacing p=new HomeCapturePacing();p.submitted(7,1000);
  p.rateLimited(7,1200);assertEquals(350,p.delayMs(7,1200));
  assertEquals(0,p.delayMs(7,1550));
 }
 @Test public void longCaptureAlreadyConsumedTheCooldown(){
  HomeCapturePacing p=new HomeCapturePacing();p.submitted(7,1000);
  assertEquals(0,p.delayMs(7,1800));p.submitted(8,1800);
  assertEquals(0,p.delayMs(7,1800));
 }
}
