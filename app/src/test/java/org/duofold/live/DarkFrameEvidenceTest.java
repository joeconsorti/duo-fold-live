package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;import java.util.Arrays;
public class DarkFrameEvidenceTest {
 private DarkFrameEvidence.Sample sample(int color){int[] p=new int[100];Arrays.fill(p,color);return DarkFrameEvidence.measure(p,10,10);}
 @Test public void transparentPixelsAreNotBlackoutEvidence(){assertFalse(sample(0).nearBlack());assertTrue(sample(0xff000000).nearBlack());}
 @Test public void intentionalBlackHalfIsNotWholePanelBlack(){int[] p=new int[100];Arrays.fill(p,0xffffffff);for(int y=0;y<10;y++)for(int x=0;x<5;x++)p[y*10+x]=0xff000000;DarkFrameEvidence.Sample s=DarkFrameEvidence.measure(p,10,10);assertFalse(s.nearBlack());assertEquals(50,s.leftBlack);assertEquals(0,s.rightBlack);}
 @Test public void brightDarkBrightNeedsSamePhysicalGeometry(){DarkFrameEvidence e=new DarkFrameEvidence();e.observe("local:1","10x10r0",1,sample(-1));e.observe("local:1","10x10r0",300,sample(0xff000000));assertFalse(e.observe("local:2","10x10r0",400,sample(-1)).contains("CANDIDATE"));assertTrue(e.observe("local:1","10x10r0",600,sample(-1)).contains("CANDIDATE"));}
 @Test public void longGapsAndRemapsAreInconclusive(){DarkFrameEvidence e=new DarkFrameEvidence();e.observe("local:1","r0",1,sample(-1));e.observe("local:1","r0",300,sample(0xff000000));assertFalse(e.observe("local:1","r0",3000,sample(-1)).contains("CANDIDATE"));e.observe("local:1","r0",3100,sample(0xff000000));assertFalse(e.observe("local:1","r1",3200,sample(-1)).contains("CANDIDATE"));}
 @Test public void transparentRecoveryIsNotBrightBookend(){DarkFrameEvidence e=new DarkFrameEvidence();e.observe("local:1","r0",1,sample(-1));e.observe("local:1","r0",10,sample(0xff000000));assertFalse(e.observe("local:1","r0",20,sample(0x00ffffff)).contains("CANDIDATE"));}
}
