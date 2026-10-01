package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndpointReturnPolicyTest {
 @Test public void waitsWhileOldPrimaryIsStillMapped(){assertTrue(EndpointReturnPolicy.shouldWait(1200,2800,true,false));}
 @Test public void incomingPanelReadyEndsWaitImmediately(){assertFalse(EndpointReturnPolicy.shouldWait(1200,2800,true,true));}
 @Test public void deadlineAndDisableEndWait(){assertFalse(EndpointReturnPolicy.shouldWait(2800,2800,true,false));assertFalse(EndpointReturnPolicy.shouldWait(1200,2800,false,false));}
}
