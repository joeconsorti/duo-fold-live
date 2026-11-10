package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class WindowWorkPriorityTest {
 @Test public void criticalWorkDoesNotAcquireOptionalReservation(){
  var p=new WindowWorkPriority();long bar=p.optionalToken();assertTrue(p.accepts(bar));
  p.enterCritical();assertFalse(p.accepts(bar));assertEquals(-1,p.optionalToken());
  p.leaveCritical();assertFalse(p.accepts(bar));assertTrue(p.accepts(p.optionalToken()));
 }
 @Test public void nestedAndConcurrentCriticalCallsRetainPriorityUntilLastExit(){
  var p=new WindowWorkPriority();p.enterCritical();p.enterCritical();p.leaveCritical();
  assertEquals(-1,p.optionalToken());p.leaveCritical();assertTrue(p.accepts(p.optionalToken()));
 }
 @Test public void completedCriticalWorkStillInvalidatesAnOlderCapture(){
  var p=new WindowWorkPriority();long old=p.optionalToken();p.enterCritical();p.leaveCritical();
  assertFalse(p.accepts(old));assertFalse(p.accepts(-1));
 }
}
