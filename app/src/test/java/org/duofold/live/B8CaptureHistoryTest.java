package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8CaptureHistoryTest {
 @Test public void lastUnfoldSurvivesFollowingFold(){B8CaptureHistory h=new B8CaptureHistory();h.add(100,336,"SOURCE ownerUid=10345");h.add(100,579,"DESTINATION unfold");h.add(2000,2200,"DESTINATION fold");String r=h.report();assertTrue(r.contains("session=100"));assertTrue(r.contains("+479ms DESTINATION unfold"));assertTrue(r.contains("session=2000"));}
 @Test public void boundsBothSessionsAndEntries(){B8CaptureHistory h=new B8CaptureHistory();for(int s=1;s<=9;s++)for(int n=0;n<14;n++)h.add(s,s+n,"event="+n+";");String r=h.report();assertFalse(r.contains("session=1\n"));assertTrue(r.contains("session=2\n"));assertFalse(r.contains("event=0;"));assertTrue(r.contains("event=13;"));}
 @Test public void oversizedDiagnosticsAreBoundedAndIdleIsIgnored(){B8CaptureHistory h=new B8CaptureHistory();h.add(0,2,"idle");h.add(1,2,"x".repeat(3000));assertTrue(h.report().length()<1400);assertTrue(h.report().contains("[truncated]"));assertFalse(h.report().contains("idle"));}
}
