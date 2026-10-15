package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticsCadenceTest {
 @Test public void firstPollAlwaysCarriesDiagnostics(){assertTrue(DiagnosticsCadence.due(5,0));}
 @Test public void thenAtMostEveryInterval(){
  long next=1000+DiagnosticsCadence.INTERVAL_MS;
  assertFalse(DiagnosticsCadence.due(1004,next));
  assertTrue(DiagnosticsCadence.due(next,next));
 }
}
