package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewReleaseGateTest {
 @Test public void commitIsRequiredBeforeEarlyRelease(){PreviewReleaseGate g=new PreviewReleaseGate(100);assertFalse(g.mayRelease(101));assertTrue(g.maySubmit(102));assertTrue(g.commit(110));assertTrue(g.mayRelease(111));assertEquals("COMMITTED",g.outcome());}
 @Test public void busyQueueCannotStartLatePreparation(){PreviewReleaseGate g=new PreviewReleaseGate(100);assertTrue(g.mayRelease(180));assertFalse(g.maySubmit(190));assertFalse(g.commit(200));assertEquals("TIMED_OUT",g.outcome());}
 @Test public void cleanupIsNotACommit(){PreviewReleaseGate g=new PreviewReleaseGate(100);g.abort("no layer");assertTrue(g.mayRelease(101));assertFalse(g.commit(102));assertEquals("SKIPPED: no layer",g.outcome());}
 @Test public void oldCommitCannotAcknowledgeNextRequest(){PreviewReleaseGate old=new PreviewReleaseGate(100),next=new PreviewReleaseGate(200);old.abort("reset");assertFalse(old.commit(210));assertFalse(next.mayRelease(210));}
 @Test public void deadlineStillBoundsMissingCallback(){PreviewReleaseGate g=new PreviewReleaseGate(100);assertTrue(g.maySubmit(179));assertFalse(g.commit(180));assertTrue(g.mayRelease(180));}
}
