package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class FrameSmoothingTest {
 @Test fun higherSmoothnessAddsLagWithoutOvershoot(){val fast=FrameSmoothing.step(0f,100f,8.33f,12f);val slow=FrameSmoothing.step(0f,100f,8.33f,120f);assertTrue(slow>0f && slow<fast && fast<100f)}
 @Test fun invalidSmoothnessUsesDefault(){assertEquals(12f,FrameSmoothing.sanitize(Float.NaN),0f);assertEquals(12f,FrameSmoothing.sanitize(-1f),0f);assertEquals(120f,FrameSmoothing.sanitize(999f),0f)}

 @Test fun reachesSamePositionAtDifferentRefreshRates(){
  fun run(hz:Int):Float{var a=0f;repeat(hz/5){a=FrameSmoothing.step(a,170f,1000f/hz)};return a}
  assertEquals(run(60),run(120),.02f)
 }
 @Test fun directionChangesDoNotOvershoot(){var a=100f;repeat(30){a=FrameSmoothing.step(a,20f,8.33f);assertTrue(a in 20f..100f)};val prior=a;a=FrameSmoothing.step(a,150f,8.33f);assertTrue(a in prior..150f)}
 @Test fun initializesWithoutAnimatingFromUnknown(){assertEquals(80f,FrameSmoothing.step(Float.NaN,80f,8f),0f)}
}
