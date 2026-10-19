package org.duofold.live
import org.junit.Assert.*
import org.junit.Test

class MotionGlassMathTest {
 @Test fun neutralAndInvalidSamplesDoNotShiftContent(){
  assertEquals(0f,MotionGlassMath.offset(0f,.035f),0f)
  assertEquals(0f,MotionGlassMath.offset(Float.NaN,.035f),0f)
 }
 @Test fun tiltContinuesPastThirtyDegreesAndStopsAtSixty(){
  assertEquals(.1f,MotionGlassMath.offset(.5235988f,.1f),.00001f)
  assertEquals(.15f,MotionGlassMath.offset(.7853982f,.1f),.00001f)
  assertEquals(.2f,MotionGlassMath.offset(1.0471976f,.1f),.00001f)
  assertEquals(.2f,MotionGlassMath.offset(10f,.1f),.00001f)
  assertEquals(-.2f,MotionGlassMath.offset(-10f,.1f),.00001f)
 }
 @Test fun axesFollowAllFourDisplayRotations(){
  val out=FloatArray(2)
  val expected=arrayOf(floatArrayOf(1f,2f),floatArrayOf(2f,-1f),floatArrayOf(-1f,-2f),floatArrayOf(-2f,1f))
  for(r in 0..3){MotionGlassMath.rotate(1f,2f,r,out);assertArrayEquals(expected[r],out,0f)}
 }
 @Test fun smoothingIsIndependentOfSensorRate(){
  var sixty=0f;var thirty=0f
  repeat(60){sixty=MotionGlassMath.smooth(sixty,1f,1000f/60,90f)}
  repeat(30){thirty=MotionGlassMath.smooth(thirty,1f,1000f/30,90f)}
  assertEquals(sixty,thirty,.00001f)
  assertEquals(.5f,MotionGlassMath.smooth(0f,.5f,16f,0f),0f)
 }
 @Test fun malformedPreferencesUseSafeDefaults(){
  assertEquals(90f,MotionGlassMath.safe(Float.NaN,90f,0f,300f),0f)
  assertEquals(300f,MotionGlassMath.safe(1000f,90f,0f,300f),0f)
 }
}
