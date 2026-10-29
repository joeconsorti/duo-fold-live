package org.duofold.live;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class CompositionEvidenceTest {
 private int[] solid(int color){int[] a=new int[10000];Arrays.fill(a,color);return a;}
 @Test public void matchingPixels(){assertTrue(CompositionEvidence.compare(solid(0xff405080),solid(0xff405080),100,100).startsWith("MATCH"));}
 @Test public void blendedWallpaperDetected(){assertTrue(CompositionEvidence.compare(solid(0xff404040),solid(0xff807040),100,100).startsWith("PIXEL_DIFFERENCE"));}
 @Test public void smallRoundingTolerated(){assertTrue(CompositionEvidence.compare(solid(0xff404040),solid(0xff424242),100,100).startsWith("MATCH"));}
 @Test public void broadSmallDifferenceNotPassed(){assertTrue(CompositionEvidence.compare(solid(0xff404040),solid(0xff4a4a4a),100,100).startsWith("PIXEL_DIFFERENCE"));}
 @Test public void edgesExcluded(){int[] b=solid(0xff404040);for(int x=0;x<100;x++)b[x]=0xffffffff;assertTrue(CompositionEvidence.compare(solid(0xff404040),b,100,100).startsWith("MATCH"));}
 @Test public void missingPixelsInconclusive(){assertTrue(CompositionEvidence.compare(new int[0],new int[0],100,100).startsWith("INCONCLUSIVE"));}
 @Test public void wrongShapeInconclusive(){assertTrue(CompositionEvidence.compare(solid(0),new int[200],100,100).startsWith("INCONCLUSIVE"));}
}
