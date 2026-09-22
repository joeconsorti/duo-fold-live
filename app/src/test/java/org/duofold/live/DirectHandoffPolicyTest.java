package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class DirectHandoffPolicyTest {
 @Test public void switchesAtExistingAngles(){
  assertEquals(0,DirectHandoffPolicy.next(false,97,true,true,true,172));
  assertEquals(1,DirectHandoffPolicy.next(false,98,true,true,true,172));
  assertEquals(0,DirectHandoffPolicy.next(true,95,true,true,true,172));
  assertEquals(2,DirectHandoffPolicy.next(true,94,true,true,true,172));
 }
 @Test public void retainsInnerUntilOpen(){
  assertEquals(0,DirectHandoffPolicy.next(true,171,true,true,true,172));
  assertEquals(3,DirectHandoffPolicy.next(true,172,true,true,true,172));
 }
 @Test public void releasesOnLockStaleOrDisabled(){
  assertEquals(3,DirectHandoffPolicy.next(true,120,true,false,true,172));
  assertEquals(3,DirectHandoffPolicy.next(true,120,false,true,true,172));
  assertEquals(3,DirectHandoffPolicy.next(true,120,true,true,false,172));
 }
 @Test public void defaultModeDoesNotUseDirectSwitch(){assertEquals(0,DirectHandoffPolicy.next(false,98,true,true,false,172));}
 @Test public void closedThresholdIsInclusiveAndDoesNotRescaleOtherAngles(){
  for(float closed:new float[]{1,3,10}){
   assertEquals(0,FoldThreshold.effectiveAngle(closed,closed),0);
   assertEquals(closed+1,FoldThreshold.effectiveAngle(closed+1,closed),0);
   assertEquals(98,FoldThreshold.effectiveAngle(98,closed),0);
   assertEquals(3,DirectHandoffPolicy.next(true,FoldThreshold.effectiveAngle(closed,closed),true,true,true,172));
  }
 }
 @Test public void invalidClosedSettingsAreBounded(){assertEquals(2,FoldThreshold.sanitizeClosed(Float.NaN),0);assertEquals(1,FoldThreshold.sanitizeClosed(-1),0);assertEquals(10,FoldThreshold.sanitizeClosed(180),0);}
}
