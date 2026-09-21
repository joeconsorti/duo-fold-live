package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HandoffStartPolicyTest {
 @Test public void screenshotModeStillRequiresCorrectFrame(){assertEquals(-1,HandoffStartPolicy.target(false,-1,false));assertEquals(-1,HandoffStartPolicy.target(true,0,false));assertEquals(1,HandoffStartPolicy.target(false,0,false));assertEquals(0,HandoffStartPolicy.target(true,1,false));}
 @Test public void liveModeHasNoCaptureGateInEitherDirection(){assertEquals(1,HandoffStartPolicy.target(false,-1,true));assertEquals(0,HandoffStartPolicy.target(true,-1,true));}
}
