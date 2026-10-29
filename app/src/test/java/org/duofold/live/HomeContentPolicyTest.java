package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeContentPolicyTest {
 private int[] icons(int alpha,int count){int[] pixels=new int[4096];for(int y=12;y<52;y++)for(int x=10;x<54;x++)if(count-->0)pixels[y*64+x]=(alpha<<24)|0x77aaff;return pixels;}
 private HomeContentPolicy.Sample sample(int alpha,int count){return HomeContentPolicy.measure(icons(alpha,count),64,64);}
 @Test public void fadesAreRejectedWithoutAlphaBoosting(){for(int alpha:new int[]{16,60,128,200,239})assertEquals("HOME_CONTENT_FADED",HomeContentPolicy.rejection(sample(alpha,400),sample(255,500)));}
 @Test public void emptyOrOneIconCannotReplacePopulatedHome(){HomeContentPolicy.Sample source=sample(255,1000);assertEquals("HOME_CONTENT_MISSING",HomeContentPolicy.rejection(sample(255,0),source));assertEquals("HOME_CONTENT_MISSING",HomeContentPolicy.rejection(sample(255,40),source));}
 @Test public void differentNativePanelLayoutNeedNotHaveEqualIconCoverage(){assertEquals("",HomeContentPolicy.rejection(sample(255,210),sample(255,1000)));}
 @Test public void populatedSourceAndLegitimateBlankSourceAreAccepted(){assertEquals("",HomeContentPolicy.rejection(sample(255,1000),null));assertEquals("",HomeContentPolicy.rejection(sample(255,0),sample(255,0)));}
 @Test public void opacityDoesNotDependOnRgbOrWallpaperBrightness(){int[] p=icons(255,600);for(int i=0;i<p.length;i++)p[i]&=0xff000000;assertEquals("",HomeContentPolicy.rejection(HomeContentPolicy.measure(p,64,64),sample(255,1000)));}
 @Test public void statusBarAndEdgesDoNotQualifyMissingIcons(){int[] p=new int[4096];for(int y=0;y<6;y++)for(int x=0;x<64;x++)p[y*64+x]=-1;assertEquals("HOME_CONTENT_MISSING",HomeContentPolicy.rejection(HomeContentPolicy.measure(p,64,64),sample(255,1000)));}
 @Test public void missingMeasurementsCannotPass(){assertEquals("HOME_ALPHA_UNAVAILABLE",HomeContentPolicy.rejection(null,sample(255,100)));}
 @Test public void rejectMalformedSamples(){try{HomeContentPolicy.measure(new int[2],64,64);fail();}catch(IllegalArgumentException expected){}}
 @Test public void innerProjectionPreservesCompleteCoverAndDoesNotMagnifyByWidth(){float[] fit=HomeProjectionPolicy.layout(1248,1972,2448,1848);assertEquals(1848f/1972,fit[0],.00001f);assertEquals(2448,fit[1]+1248*fit[0],.001f);assertEquals(0,fit[2],.001f);assertTrue(fit[1]>0);}
 @Test public void coverProjectionStillFillsAndAppEligibilityIsFalse(){float[] fit=HomeProjectionPolicy.layout(2448,1848,1248,1972);assertTrue(fit[1]<=0);assertEquals(0,fit[2],.001f);assertFalse(HomeProjectionPolicy.project(false,1248,1972,2448,1848));}
}
