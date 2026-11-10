package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class B8FramePipelineTest {
 @Test public void latchAllowsOverlapWhileCompletionRemainsIndependent(){
  B8FramePipeline p=new B8FramePipeline();long a=p.begin(100);
  assertFalse(p.canBegin());p.latched(a);assertTrue(p.canBegin());
  long b=p.begin(117);p.latched(b);assertFalse(p.canBegin());
  assertEquals(40,p.oldestAge(140)); // newer submissions cannot reset the watchdog
  assertTrue(p.complete(a));assertTrue(p.canBegin());
  assertEquals(23,p.oldestAge(140));assertTrue(p.complete(b));assertFalse(p.pending());
 }
 @Test public void outOfOrderOrDuplicateCompletionCannotRegressEvidence(){
  B8FramePipeline p=new B8FramePipeline();long a=p.begin(100);p.latched(a);long b=p.begin(117);
  assertTrue(p.complete(b));assertFalse(p.complete(a));assertFalse(p.complete(b));
  assertTrue(p.canBegin());assertEquals(0,p.oldestAge(200));
 }
 @Test public void lateLatchCannotUnblockAnotherGpuDraw(){
  B8FramePipeline p=new B8FramePipeline();long a=p.begin(100);p.complete(a);long b=p.begin(117);
  p.latched(a);assertFalse(p.canBegin());p.discard(a);assertFalse(p.canBegin());
  p.discard(b);assertTrue(p.canBegin());assertFalse(p.complete(b));
 }
 @Test public void rejectedFrameFreesPacingWithoutAcknowledgingIt(){
  B8FramePipeline p=new B8FramePipeline();long a=p.begin(100);p.discard(a);
  assertFalse(p.contains(a));assertFalse(p.pending());assertFalse(p.complete(a));
  long b=p.begin(117);assertTrue(b>a);assertTrue(p.contains(b));
 }
 @Test(expected=IllegalStateException.class) public void gpuWorkCannotOverlap(){
  B8FramePipeline p=new B8FramePipeline();p.begin(100);p.begin(117);
 }
 @Test(expected=IllegalStateException.class) public void outstandingPresentationsAreBounded(){
  B8FramePipeline p=new B8FramePipeline();long a=p.begin(100);p.latched(a);long b=p.begin(117);p.latched(b);p.begin(134);
 }
}
