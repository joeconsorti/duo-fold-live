package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticTimelineTest {
 @Test public void boundsHistoryAndPreservesBothClocks(){DiagnosticTimeline t=new DiagnosticTimeline(2,100);t.add(1,9,"A","first");t.add(2,10,"B","second");t.add(3,11,"C","third");String r=t.report(10);assertTrue(r.contains("droppedOldest=1 retained=2"));assertFalse(r.contains("first"));assertTrue(r.contains("2\t10\tB\tsecond"));assertTrue(r.contains("3\t11\tC\tthird"));}
 @Test public void detailsCannotInjectRowsAndAreBounded(){DiagnosticTimeline t=new DiagnosticTimeline(2,5);t.add(1,1,"A","a\nb\tcdefgh");String r=t.report(2);assertTrue(r.contains("a b c [truncated]"));assertFalse(r.contains("defgh"));}
 @Test public void reportTailDoesNotDeleteFullExport(){DiagnosticTimeline t=new DiagnosticTimeline(4,30);t.add(1,1,"A","earlier");t.add(2,2,"B","later");assertFalse(t.report(1).contains("earlier"));assertTrue(t.report(4).contains("earlier"));}
}
