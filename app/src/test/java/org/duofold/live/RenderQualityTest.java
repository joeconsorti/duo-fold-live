package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class RenderQualityTest {
 @Test public void captureTimeIsIncludedInFrameBudget(){assertEquals(7,RenderQuality.delay(60,10_000_000));assertEquals(4,RenderQuality.delay(120,5_000_000));assertEquals(0,RenderQuality.delay(60,20_000_000));assertEquals(74,RenderQuality.delay(12,10_000_000));}
 @Test public void invalidValuesCannotCreateUnboundedWork(){assertEquals(60,RenderQuality.fps(10000));assertEquals(1.5f,RenderQuality.blur(Float.NaN),0);assertEquals(3,RenderQuality.blur(100),0);assertEquals(.02f,RenderQuality.seam(Float.POSITIVE_INFINITY),0);assertEquals(.06f,RenderQuality.seam(1),0);assertEquals(0,RenderQuality.seam(-1),0);}
}
