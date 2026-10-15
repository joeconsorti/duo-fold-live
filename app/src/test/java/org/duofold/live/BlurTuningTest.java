package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class BlurTuningTest {
 // Plan B10: one sanitized source for the mirror, the hold and the right half.
 @Test public void maxIsClampedToTheCompositorLimit(){
  assertEquals(PreviewBlurPolicy.MAX_RADIUS,BlurTuning.sanitizeMax(1e6f),0);
  assertEquals(0,BlurTuning.sanitizeMax(-5),0);
  assertEquals(BlurTuning.DEFAULT_MAX,BlurTuning.sanitizeMax(Float.NaN),0);
 }
 @Test public void anglesAndGlideFallBackOnNonsense(){
  assertEquals(150,BlurTuning.sanitizeAngle(150,-1),0);
  assertEquals(-1,BlurTuning.sanitizeAngle(200,-1),0);
  assertEquals(BlurTuning.DEFAULT_SMOOTH_MS,BlurTuning.sanitizeSmooth(-1),0);
  assertEquals(0,BlurTuning.sanitizeSmooth(0),0);
 }
}
