package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewBlurPolicyTest {
 @Test public void sharpUntilStartThenRampsToMaxAtSwitch(){
  assertEquals(0,PreviewBlurPolicy.radius(0,25,101,40),0);
  assertEquals(0,PreviewBlurPolicy.radius(25,25,101,40),0);
  assertEquals(20,PreviewBlurPolicy.radius(63,25,101,40),.001);
  assertEquals(40,PreviewBlurPolicy.radius(101,25,101,40),0);
  assertEquals(40,PreviewBlurPolicy.radius(150,25,101,40),0);
 }
 @Test public void rampIsMonotonicAndSmooth(){
  float last=-1;
  for(float a=0;a<=120;a+=.5f){float r=PreviewBlurPolicy.radius(a,25,101,40);assertTrue(r>=last);last=r;}
  // Smoothstep: slope is zero at both ends, so the blur eases in and arrives without a jolt.
  assertTrue(PreviewBlurPolicy.radius(27,25,101,40)<1);
  assertTrue(40-PreviewBlurPolicy.radius(99,25,101,40)<1);
 }
 @Test public void invalidInputsAreSafe(){
  assertEquals(0,PreviewBlurPolicy.radius(Float.NaN,25,101,40),0);
  assertEquals(0,PreviewBlurPolicy.radius(80,25,101,-5),0);
  assertEquals(PreviewBlurPolicy.MAX_RADIUS,PreviewBlurPolicy.radius(101,25,101,10_000),0);
  assertEquals(40,PreviewBlurPolicy.radius(101,101,101,40),0);   // degenerate ramp: step at the end angle
  assertEquals(0,PreviewBlurPolicy.radius(100,101,101,40),0);
 }
 @Test public void onlyResubmitsOnVisibleChange(){
  assertTrue(PreviewBlurPolicy.changed(-1,0));
  assertFalse(PreviewBlurPolicy.changed(10,10.6f));
  assertTrue(PreviewBlurPolicy.changed(10,11));
  assertTrue(PreviewBlurPolicy.changed(1,0));
 }

 @Test public void glidesTowardTargetFrameByFrame(){
  // Exponential approach with time constant tau: one tau closes ~63% of the gap.
  assertEquals(40+20*(1-Math.exp(-1)),PreviewBlurPolicy.glide(40,60,40,40),.01);
  assertEquals(50,PreviewBlurPolicy.glide(50,50,8,40),0);
  float a=34;for(int i=0;i<200;i++)a=PreviewBlurPolicy.glide(a,76,8,40);
  assertEquals(76,a,.01);
 }
 @Test public void glideNeverOvershootsAndHandlesEdgeCases(){
  float a=PreviewBlurPolicy.glide(10,20,1000,40);assertTrue(a<=20&&a>19.9f);
  assertEquals(20,PreviewBlurPolicy.glide(10,20,8,0),0);       // tau 0: snap
  assertEquals(20,PreviewBlurPolicy.glide(Float.NaN,20,8,40),0); // no history: take target
  assertEquals(10,PreviewBlurPolicy.glide(10,20,0,40),0);       // no time passed
  assertEquals(10,PreviewBlurPolicy.glide(10,Float.NaN,8,40),0); // bad target: hold
 }
}
