package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ClosingPowerPolicyTest {
 @Test public void onlyNativeClosedOffCanReceivePulse(){
  assertTrue(ClosingPowerPolicy.mayPulse(100,1200,250,false,true,true,0,0,1));
  assertFalse(ClosingPowerPolicy.mayPulse(100,1200,250,false,true,true,5,0,1));
  assertFalse(ClosingPowerPolicy.mayPulse(100,1200,250,false,true,true,0,1,1));
  assertFalse(ClosingPowerPolicy.mayPulse(100,1200,250,false,true,true,0,0,2));
 }
 @Test public void neverRepeatsOrTargetsAnotherPanel(){
  assertFalse(ClosingPowerPolicy.mayPulse(100,1200,250,true,true,true,0,0,1));
  assertFalse(ClosingPowerPolicy.mayPulse(100,1200,250,false,true,false,0,0,1));
 }
 @Test public void staleInputSleepAndDeadlineStopPulse(){
  assertFalse(ClosingPowerPolicy.mayPulse(250,1200,250,false,true,true,0,0,1));
  assertFalse(ClosingPowerPolicy.mayPulse(100,1200,0,false,true,true,0,0,1));
  assertFalse(ClosingPowerPolicy.mayPulse(100,1200,250,false,false,true,0,0,1));
  assertFalse(ClosingPowerPolicy.mayPulse(1200,1200,1300,false,true,true,0,0,1));
 }
}
