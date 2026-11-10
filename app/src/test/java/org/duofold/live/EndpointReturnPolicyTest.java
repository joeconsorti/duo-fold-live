package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndpointReturnPolicyTest {
 @Test public void innerPanelOnWithCoverDimensionsStillWaits(){
  assertFalse(EndpointReturnPolicy.ready("inner","inner",2,2448,1848,1248,1972));
  assertTrue(EndpointReturnPolicy.ready("inner","inner",2,2448,1848,2448,1848));
 }
 @Test public void rotationDoesNotInvalidateDestinationSize(){assertTrue(EndpointReturnPolicy.ready("inner","inner",2,2448,1848,1848,2448));}
 @Test public void wrongPanelOffAndUnknownSizeAreNotReady(){
  assertFalse(EndpointReturnPolicy.ready("inner","cover",2,2448,1848,2448,1848));
  assertFalse(EndpointReturnPolicy.ready("inner","inner",1,2448,1848,2448,1848));
  assertFalse(EndpointReturnPolicy.ready("inner","inner",2,0,0,2448,1848));
 }
 @Test public void waitsWhileOldPrimaryIsStillMapped(){assertTrue(EndpointReturnPolicy.shouldWait(1200,2800,true,false));}
 @Test public void incomingPanelReadyEndsWaitImmediately(){assertFalse(EndpointReturnPolicy.shouldWait(1200,2800,true,true));}
 @Test public void deadlineAndDisableEndWait(){assertFalse(EndpointReturnPolicy.shouldWait(2800,2800,true,false));assertFalse(EndpointReturnPolicy.shouldWait(1200,2800,false,false));}
 @Test public void transientSleepAndExpiredDeadlineNeverAuthorizeWrongPanelReturn(){
  assertFalse(EndpointReturnPolicy.safeToReturn(false,false));
  assertFalse(EndpointReturnPolicy.safeToReturn(true,false));
  assertFalse(EndpointReturnPolicy.safeToReturn(false,true));
  assertTrue(EndpointReturnPolicy.safeToReturn(true,true));
 }
 @Test public void leaseArmsNearEitherEndpointOnly(){
  assertFalse(EndpointReturnPolicy.armPower(true,13,172));assertTrue(EndpointReturnPolicy.armPower(true,12,172));
  assertFalse(EndpointReturnPolicy.armPower(false,159,172));assertTrue(EndpointReturnPolicy.armPower(false,160,172));
  assertFalse(EndpointReturnPolicy.armPower(true,Float.NaN,172));
 }
 @Test public void alpha30ClosingReplayWaitsThroughWakeAndOldDimensions(){
  // endpoint=237935779; wake returned at +54ms but mapping was not ready.
  String[] panel={"inner","inner","cover","cover","cover"};
  int[] state={2,1,1,2,2},w={2448,2448,2448,2448,1248},h={1848,1848,1848,1848,1972};
  long[] afterEndpoint={55,1085,3018,3079,3259};
  for(int i=0;i<panel.length;i++){
   boolean mapped=EndpointReturnPolicy.ready("cover",panel[i],state[i],1248,1972,w[i],h[i]);
   assertEquals("return at +"+afterEndpoint[i],i==4,EndpointReturnPolicy.safeToReturn(true,mapped));
  }
 }
}
