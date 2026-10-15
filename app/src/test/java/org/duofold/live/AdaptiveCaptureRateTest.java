package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdaptiveCaptureRateTest {
 // 2026-10-05 measurement: resting in Flex (hinge still at 120 deg, glass ~63 %) cost ~43 % of a core because the
 // glass content capture loop kept running at the 120 fps target. Full rate only while the hinge moves.
 @Test public void fullRateWhileMoving(){assertEquals(120,RenderQuality.adaptiveFps(120,100,false));}
 @Test public void dropsWhenStill(){
  assertEquals(RenderQuality.STILL_FPS,RenderQuality.adaptiveFps(120,RenderQuality.STILL_AFTER_MS,false));
  assertEquals(RenderQuality.STILL_FPS,RenderQuality.adaptiveFps(60,60000,false));
 }
 @Test public void postSwitchWindowStaysAtFullRate(){assertEquals(120,RenderQuality.adaptiveFps(120,60000,true));}
 @Test public void neverRaisesALowerSetting(){assertEquals(RenderQuality.STILL_FPS<12?RenderQuality.STILL_FPS:12,RenderQuality.adaptiveFps(12,60000,false));assertEquals(12,RenderQuality.adaptiveFps(12,0,false));}
}
