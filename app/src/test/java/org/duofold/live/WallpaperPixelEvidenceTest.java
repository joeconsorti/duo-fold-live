package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class WallpaperPixelEvidenceTest {
 @Test public void identicalIsNotObserved(){int[] p=new int[1000];assertTrue(WallpaperPixelEvidence.analyze(p,p,p).startsWith("NOT_OBSERVED"));}
 @Test public void stableExclusionDifferenceIsContribution(){int[] a=new int[1000],b=new int[1000];Arrays.fill(b,0xffffff);assertTrue(WallpaperPixelEvidence.analyze(a,b,a).startsWith("WALLPAPER_CONTRIBUTION"));}
 @Test public void movingSceneIsInconclusive(){int[] a=new int[1000],c=new int[1000];Arrays.fill(c,0xffffff);assertTrue(WallpaperPixelEvidence.analyze(a,a,c).startsWith("INCONCLUSIVE"));}
 @Test public void mismatchedGeometryIsInconclusive(){assertTrue(WallpaperPixelEvidence.analyze(new int[2],new int[1],new int[2]).startsWith("INCONCLUSIVE"));}
 @Test public void isolatedNoiseDoesNotTrigger(){int[] a=new int[1000],b=new int[1000];b[0]=0xffffff;assertTrue(WallpaperPixelEvidence.analyze(a,b,a).startsWith("NOT_OBSERVED"));}
}
