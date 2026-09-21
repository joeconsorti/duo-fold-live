package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class LiveAngleMappingTest {
 @Test fun recordedCoverAndInnerSequence() {
  val cover = listOf(0f,4f,22f,61f).map { LiveAngleMapping.strength(it,false) }
  assertEquals(0f,cover.first(),0f)
  assertTrue(cover.zipWithNext().all { (a,b)->b>a })
  assertTrue(LiveAngleMapping.strength(174f,true)<.1f)
  assertEquals(0f,LiveAngleMapping.strength(180f,true),0f)
  assertEquals(0f,LiveAngleMapping.strength(0f,false),0f)
 }
 @Test fun pauseAndReverseDoNotDependOnElapsedTime() {
  val held=LiveAngleMapping.strength(119f,true)
  repeat(100){assertEquals(held,LiveAngleMapping.strength(119f,true),0f)}
  assertTrue(LiveAngleMapping.strength(103f,true)>held)
  assertTrue(LiveAngleMapping.strength(145f,true)<held)
 }
 @Test fun phaseClampsAtEndpoints() {
  assertEquals(1f,LiveAngleMapping.strength(0f,true),0f)
  assertEquals(1f,LiveAngleMapping.strength(100f,false),0f)
  assertEquals(0f,LiveAngleMapping.strength(-1f,false),0f)
 }
}
