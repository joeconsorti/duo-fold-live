package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8AngleContinuityTest {
 @Test public void supplied768msGapRetainsExistingFrameWithoutExtendingShellLease(){
  assertTrue(B8AngleContinuity.retain(true,true,768));
  assertTrue(B8AngleContinuity.retain(true,true,1999));
  assertFalse(B8AngleContinuity.retain(true,true,2000));
 }
 @Test public void cannotStartNewSceneOrUseMissingFutureOrFreshSamplesAsHeldCoverage(){
  assertFalse(B8AngleContinuity.retain(false,true,768));assertFalse(B8AngleContinuity.retain(true,false,768));
  assertFalse(B8AngleContinuity.retain(true,true,-1));assertFalse(B8AngleContinuity.retain(true,true,749));
 }
 @Test public void onlyExistingDirectCoverageGetsLongerHeartbeatLease(){
  assertEquals(2000,B8AngleContinuity.leaseMs(true));assertEquals(750,B8AngleContinuity.leaseMs(false));
 }
}
