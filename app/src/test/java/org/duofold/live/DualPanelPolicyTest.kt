package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class DualPanelPolicyTest {
 @Test fun onlyTransitionStarts(){assertFalse(DualPanelPolicy.canStart(0f));assertFalse(DualPanelPolicy.canStart(180f));assertFalse(DualPanelPolicy.canStart(Float.NaN));assertTrue(DualPanelPolicy.canStart(2f));assertTrue(DualPanelPolicy.canStart(172f));assertFalse(DualPanelPolicy.canStart(173f));assertFalse(DualPanelPolicy.canStart(175f))}
 @Test fun nativeInnerRequestedAtFirstMovementAndHeldToClosure(){
  assertFalse(DualPanelPolicy.targetInner(0f,false));assertTrue(DualPanelPolicy.targetInner(2f,false));assertTrue(DualPanelPolicy.targetInner(45f,true));assertTrue(DualPanelPolicy.targetInner(1.5f,true));assertFalse(DualPanelPolicy.targetInner(1f,true));assertFalse(DualPanelPolicy.targetInner(Float.NaN,true))
 }
 @Test fun releaseEndpoints(){assertTrue(DualPanelPolicy.endpoint(0f));assertTrue(DualPanelPolicy.endpoint(180f));assertTrue(DualPanelPolicy.endpoint(175f));assertFalse(DualPanelPolicy.endpoint(174f));assertFalse(DualPanelPolicy.endpoint(90f))}
 @Test fun releasedCoverCannotRearmAtOpenEnd(){
  for(angle in listOf(175f,176f,179f,180f,179f,175f,174f,173f)) assertFalse(DualPanelPolicy.canStart(angle))
  assertTrue(DualPanelPolicy.canStart(172f))
 }
 @Test fun closedStartupDoesNotNeedFullOpenArming(){
  assertTrue(DualPanelPolicy.canStart(2f))
  assertTrue(DualPanelPolicy.targetInner(100f,false))
  assertTrue(DualPanelPolicy.targetInner(80f,true))
 }
}
