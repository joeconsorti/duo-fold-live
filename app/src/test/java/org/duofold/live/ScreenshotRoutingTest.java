package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScreenshotRoutingTest {
 @Test public void promotionReversesPrimaryAfterWarmup(){assertTrue(ScreenshotStartupPolicy.incomingInner(0));assertFalse(ScreenshotStartupPolicy.incomingInner(1));}
 @Test public void frameFollowsPhysicalPanelNotLogicalDisplay(){
  assertTrue(ScreenshotStartupPolicy.ownsFrame("local:cover","local:cover"));
  assertFalse(ScreenshotStartupPolicy.ownsFrame("local:cover","local:inner"));
  assertFalse(ScreenshotStartupPolicy.ownsFrame("unknown","unknown"));
  assertFalse(ScreenshotStartupPolicy.ownsFrame(null,"local:cover"));
 }
 @Test public void openEndpointJitterDoesNotStartClosing(){
  for(float angle:new float[]{164,168,170,171,172,175,177})assertFalse(ScreenshotStartupPolicy.mayStart(true,angle,172));
  assertTrue(ScreenshotStartupPolicy.mayStart(true,160,172));
  assertTrue(ScreenshotStartupPolicy.mayStart(false,3,172));
  assertFalse(ScreenshotStartupPolicy.mayStart(false,0,172));
 }
}
