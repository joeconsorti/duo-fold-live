package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class FoldWakePolicyTest {
 @Test public void slowClosingRenewsButCannotHoldBeyondThirtySeconds(){assertEquals(6000,FoldWakePolicy.holdUntil(1000,1000));assertEquals(12000,FoldWakePolicy.holdUntil(1000,7000));assertEquals(31000,FoldWakePolicy.holdUntil(1000,29000));assertEquals(31000,FoldWakePolicy.holdUntil(1000,32000));}
 @Test public void onlyRecentFoldSleepCanWake(){assertTrue(FoldWakePolicy.recover(1000,1500,13));for(int reason:new int[]{0,1,2,3,4,6,7,8,99})assertFalse(FoldWakePolicy.recover(1000,1500,reason));}
 @Test public void expiredMissingAndFutureLeasesCannotWake(){assertFalse(FoldWakePolicy.recover(0,500,13));assertFalse(FoldWakePolicy.recover(1000,999,13));assertFalse(FoldWakePolicy.recover(1000,6001,13));assertTrue(FoldWakePolicy.recover(1000,6000,13));}
 @Test public void closingDeadlineCanShortenButNeverExtendWakeLease(){
  assertTrue(FoldWakePolicy.validLease(1000,1200,1750));
  assertFalse(FoldWakePolicy.validLease(1000,1750,1750));
  assertFalse(FoldWakePolicy.validLease(1000,1200,1000));
  assertFalse(FoldWakePolicy.validLease(1000,1200,6001));
  assertFalse(FoldWakePolicy.validLease(1000,999,1750));
  assertTrue(FoldWakePolicy.validLease(1000,5999,6000));
 }
 @Test public void openEndpointNoiseCannotArmButDeliberateClosingCan(){
  for(float angle:new float[]{180,179,178,177})assertFalse(FoldWakePolicy.closingMotion(180,angle));
  assertTrue(FoldWakePolicy.closingMotion(180,176));
  assertTrue(FoldWakePolicy.closingMotion(100,96));
  assertFalse(FoldWakePolicy.closingMotion(20,10));
  assertFalse(FoldWakePolicy.closingMotion(Float.NaN,170));
 }
}
