package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class UnifiedRendererTest {
 @Test public void defaultsOnForFold7OnlyAndPropertyOverrides(){
  assertTrue(UnifiedRenderer.decide("",true));
  assertFalse(UnifiedRenderer.decide("",false));
  assertTrue(UnifiedRenderer.decide("1",false));
  assertTrue(UnifiedRenderer.decide("true",false));
  assertFalse(UnifiedRenderer.decide("0",true));
  assertFalse(UnifiedRenderer.decide("false",true));
  assertTrue(UnifiedRenderer.decide("garbage",true));
  assertTrue(UnifiedRenderer.decide(null,true));
 }
 @Test public void preSwitchInnerProgressIsContinuousAtTheSwitch(){
  // The pre-switch inner glass uses the same curve as the post-switch glass, so at the Fold 7 switch
  // angle both sides render the same leaf angle.
  for(float handoff:new float[]{HandoffSettings.DEFAULT,98f}){
  float sw=HandoffFadePolicy.blackAnglesFor("SM-F966U1",handoff)[1];
  // The curve saturates at 1 (below ~90 deg), e.g. at the default 85 deg handoff's 88 deg Fold 7 switch.
  assertEquals(Math.min(1f,(172f-sw)/82f),DuoShadeCurve.INSTANCE.progress(sw,true,172f),1e-5f);
  }
  assertEquals(1f,DuoShadeCurve.INSTANCE.progress(45f,true,172f),0f);
 }
}
