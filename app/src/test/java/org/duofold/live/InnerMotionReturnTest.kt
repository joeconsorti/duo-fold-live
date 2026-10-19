package org.duofold.live
import org.junit.Assert.*
import org.junit.Test

class InnerMotionReturnTest {
 @Test fun endpointStartsAtLastRenderedSquashThenEasesToExactlyFlat(){
  val r=InnerMotionReturn();r.observe(.8f);assertTrue(r.begin(1000))
  assertEquals(.8f,r.amount(1000),0f)
  assertEquals(.4f,r.amount(1150),.00001f)
  assertEquals(0f,r.amount(1300),0f);assertTrue(r.finished(1300))
  assertEquals(0f,r.amount(2000),0f)
 }
 @Test fun pollingAndRepeatedEndpointSamplesCannotRestartOrOverwriteReturn(){
  val r=InnerMotionReturn();r.observe(.6f);r.begin(1000)
  r.observe(0f);assertFalse(r.begin(1200))
  assertTrue(r.amount(1200)>0f);assertTrue(r.finished(1300))
 }
 @Test fun fastAndSlowFrameRatesGiveTheSameBoundedMonotonicReturn(){
  for(step in intArrayOf(8,16,33,100)){
   val r=InnerMotionReturn();r.observe(1f);r.begin(0);var last=1f
   for(t in 0..300 step step){val next=r.amount(t.toLong());assertTrue(next in 0f..last);last=next}
   assertEquals(0f,r.amount(300),0f)
  }
 }
 @Test fun cancelAndImmediateStopDoNotLeaveAPendingReturn(){
  val r=InnerMotionReturn();r.observe(.8f);r.begin(1000);r.cancel(1150)
  assertFalse(r.pending);assertEquals(.4f,r.lastAmount,.00001f)
  r.observe(.7f);r.begin(1200);r.clear()
  assertFalse(r.pending);assertEquals(0f,r.lastAmount,0f)
 }
 @Test fun alreadyFlatDoesNotStartAnOverlayHold(){
  val r=InnerMotionReturn();assertFalse(r.begin(1000));assertFalse(r.pending)
 }
}
