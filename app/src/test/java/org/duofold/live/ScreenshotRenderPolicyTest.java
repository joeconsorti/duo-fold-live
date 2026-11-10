package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScreenshotRenderPolicyTest {
 @Test public void incomingLiveAndOutgoingFrozenStaySeparate(){assertFalse(ScreenshotRenderPolicy.incoming("cover","cover",true));assertTrue(ScreenshotRenderPolicy.incoming("cover","inner",true));assertTrue(ScreenshotRenderPolicy.incoming("inner","cover",true));assertFalse(ScreenshotRenderPolicy.incoming("cover","inner",false));assertFalse(ScreenshotRenderPolicy.incoming("cover","unknown",true));}
 @Test public void captureFollowsLiveIncomingDisplayThenReturns(){assertEquals(1,ScreenshotRenderPolicy.captureDisplay(false,true,true));assertEquals(0,ScreenshotRenderPolicy.captureDisplay(false,true,false));assertEquals(0,ScreenshotRenderPolicy.captureDisplay(false,false,true));assertEquals(1,ScreenshotRenderPolicy.captureDisplay(true,false,false));}
 @Test public void normalEffectOpacityIsIndependentOfOpaqueScreenshotCoverage(){
  assertFalse(ScreenshotRenderPolicy.opaqueEffect(true,true));
  assertFalse(ScreenshotRenderPolicy.opaqueEffect(true,false));
  assertTrue(ScreenshotRenderPolicy.opaqueEffect(false,true));
  assertFalse(ScreenshotRenderPolicy.opaqueEffect(false,false));
 }
}
