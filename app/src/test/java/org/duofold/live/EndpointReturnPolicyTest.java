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
}
