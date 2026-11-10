package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class FrameSmoothingTest {
 @Test fun remapGapUsesElapsedTimeInsteadOfDiscarding116ms(){
  val before=120f;val raw=62f
  val delayed=FrameSmoothing.handoffStep(before,raw,166f,120f,172f)
  val ordinary=FrameSmoothing.step(before,raw,50f,120f)
  assertTrue(delayed<ordinary)
  assertTrue(delayed in raw..before)
 }
 @Test fun replayFinalCloseConvergesBeforeImmediateEndpointRemoval(){
  var pose=137f
  for(raw in listOf(62f,28f,18f,9f,5f,4f,0f)){
   pose=FrameSmoothing.handoffStep(pose,raw,32f,120f,172f)
   assertTrue("raw=$raw pose=$pose",pose in raw..raw*1.25f)
  }
  assertEquals(0f,pose,0f)
 }
 @Test fun openingConvergesToConfiguredEndpointAndReversalDoesNotOvershoot(){
  for(open in listOf(165f,172f,178f)){
   var pose=100f
   for(raw in listOf(open-20,open-9,open-4,open)){
    val old=pose;pose=FrameSmoothing.handoffStep(pose,raw,32f,120f,open)
    assertTrue(pose in old..raw);assertTrue(raw-pose<=(open-raw)*.25f+.0001f)
   }
   assertEquals(open,pose,0f)
   val reversed=FrameSmoothing.handoffStep(pose,open-15,32f,120f,open)
   assertTrue(reversed in open-15..pose)
  }
 }
 @Test fun invalidInputAndLongStallsRemainBounded(){
  assertEquals(70f,FrameSmoothing.handoffStep(70f,Float.NaN,100f,120f,172f),0f)
  assertEquals(85f,FrameSmoothing.handoffStep(Float.NaN,85f,17f,120f,172f),0f)
  assertEquals(FrameSmoothing.handoffStep(160f,85f,250f,120f,172f),FrameSmoothing.handoffStep(160f,85f,10000f,120f,172f),0f)
 }
 @Test fun higherSmoothnessAddsLagWithoutOvershoot(){val fast=FrameSmoothing.step(0f,100f,8.33f,12f);val slow=FrameSmoothing.step(0f,100f,8.33f,120f);assertTrue(slow>0f && slow<fast && fast<100f)}
 @Test fun invalidSmoothnessUsesDefault(){assertEquals(120f,FrameSmoothing.sanitize(Float.NaN),0f);assertEquals(12f,FrameSmoothing.sanitize(-1f),0f);assertEquals(120f,FrameSmoothing.sanitize(999f),0f)}

 @Test fun reachesSamePositionAtDifferentRefreshRates(){
  fun run(hz:Int):Float{var a=0f;repeat(hz/5){a=FrameSmoothing.step(a,170f,1000f/hz)};return a}
  assertEquals(run(60),run(120),.02f)
 }
 @Test fun directionChangesDoNotOvershoot(){var a=100f;repeat(30){a=FrameSmoothing.step(a,20f,8.33f);assertTrue(a in 20f..100f)};val prior=a;a=FrameSmoothing.step(a,150f,8.33f);assertTrue(a in prior..150f)}
 @Test fun initializesWithoutAnimatingFromUnknown(){assertEquals(80f,FrameSmoothing.step(Float.NaN,80f,8f),0f)}
 @Test fun standardCoverStartupStartsFromClosedOnlyWhenEasingApplies(){
  assertEquals(0f,FrameSmoothing.start(Float.NaN,6f,false,true,true),0f)
  assertTrue(FrameSmoothing.start(Float.NaN,13f,false,true,true).isNaN())
  assertTrue(FrameSmoothing.start(Float.NaN,6f,true,true,true).isNaN())
  assertTrue(FrameSmoothing.start(Float.NaN,6f,false,false,true).isNaN())
  assertTrue(FrameSmoothing.start(Float.NaN,6f,false,true,false).isNaN())
 }
 @Test fun establishedAngleNeverRestartsStartupDuringReversal(){
  assertEquals(8f,FrameSmoothing.start(8f,6f,false,true,true),0f)
 }
}
