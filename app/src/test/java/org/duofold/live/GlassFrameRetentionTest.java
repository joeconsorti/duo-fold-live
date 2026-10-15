package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class GlassFrameRetentionTest {
 // One failed capture ("Panel changed during capture" on a device-state step, "Screen off", a capture timeout)
 // must not clear a good frame that is still renderable.
 @Test public void failedCaptureKeepsRecentGoodFrame(){
  assertTrue(GlassFramePolicy.keepOnFailure(true,0));
  assertTrue(GlassFramePolicy.keepOnFailure(true,GlassFramePolicy.MAX_AGE_MS));
 }
 @Test public void frameTooOldToRenderIsClearedSoFailureStaysVisible(){
  assertFalse(GlassFramePolicy.keepOnFailure(true,GlassFramePolicy.MAX_AGE_MS+1));
  assertFalse(GlassFramePolicy.keepOnFailure(true,-1));
 }
 @Test public void nothingToKeepWithoutAFrame(){assertFalse(GlassFramePolicy.keepOnFailure(false,0));}
 @Test public void keptFrameRetriesQuickly(){assertTrue(GlassFramePolicy.RETRY_WITH_FRAME_MS<=50);}
 // Keeping a frame never extends how long it may be drawn: the render age limit is unchanged.
 @Test public void renderAgeLimitUnchanged(){
  assertEquals(350,GlassFramePolicy.MAX_AGE_MS);
  assertTrue(GlassFramePolicy.usable(1000,1000+GlassFramePolicy.MAX_AGE_MS,1248,1972,1248,1972));
  assertFalse(GlassFramePolicy.usable(1000,1001+GlassFramePolicy.MAX_AGE_MS,1248,1972,1248,1972));
 }
 // Fold 7 renders frames up to KEEP_ON_FAILURE_MS, so a failed capture keeps them that long there.
 @Test public void fold7KeepsFrameForItsRenderAge(){
  long max=GlassFramePolicy.maxAgeFor("SM-F966U1");
  assertEquals(GlassFramePolicy.KEEP_ON_FAILURE_MS,max);
  assertTrue(GlassFramePolicy.keepOnFailure(true,max,max));assertFalse(GlassFramePolicy.keepOnFailure(true,max+1,max));
  assertTrue(GlassFramePolicy.usable(1000,1000+max,1248,1972,1248,1972,max));
  assertFalse(GlassFramePolicy.usable(1000,1001+max,1248,1972,1248,1972,max));
 }
 @Test public void otherDevicesKeepUpstreamWindow(){
  assertEquals(350,GlassFramePolicy.maxAgeFor("SM-F956U"));
  assertFalse(GlassFramePolicy.usable(1000,1100,1248,1972,2448,1848,1500));  // size still checked
 }
}
