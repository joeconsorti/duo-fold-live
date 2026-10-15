package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class StripGeometryTest {
 // Plan B3/B4: in unified mode the right pane is exactly the right half and the reflection uses the same
 // uniform scale, so the boundary does not move at the switch and the two halves are mirror images.
 @Test public void mirrorFillsExactlyTheRightHalf(){
  float[] f=LiveMirrorLayout.rightHalf(1080,2520,1968,2184);
  assertEquals(984f/1080f,f[0],1e-5);                 // width-limited: 0.911
  assertEquals(984f,f[1],1e-3);                        // left edge at w/2
  assertEquals(984f,1080*f[0],1e-3);                   // exactly half wide
  assertEquals((2184-2520*f[0])/2f,f[2],1e-3);         // centered vertically (crops ~56 px top/bottom)
 }
 @Test public void reflectionMatchesTheMirrorScale(){
  float[] mirror=LiveMirrorLayout.rightHalf(1080,2520,1968,2184);
  float[] strip=LiveMirrorLayout.fill(1080,2520,984,2184);
  assertEquals(mirror[0],strip[0],1e-6);
  assertEquals(mirror[2],strip[2],1e-3);
  assertEquals(0f,strip[1],1e-3);
 }
 // Plan B2: the strip's pose leaves the 55 deg anti-collapse cap smoothly and reaches the post-switch inner
 // pose at the switch: shader progress hinge/90 equals the inner progress (thr-a)/(thr-90) there.
 @Test public void poseIsIdentityBelowTheCap(){
  for(float a:new float[]{0,20,40,55})assertEquals(a,StripPose.hinge(a,55,101,172),1e-4);
 }
 @Test public void poseReachesTheInnerProgressAtTheSwitch(){
  float inner=(172f-101f)/(172f-90f);
  assertEquals(inner,StripPose.hinge(101,55,101,172)/90f,1e-4);
  assertEquals(inner,StripPose.hinge(110,55,101,172)/90f,1e-4);   // held past the switch (strip is gone by then)
 }
 @Test public void poseIsMonotonicAndNeverEdgeOn(){
  float prev=-1;
  for(int a=0;a<=101;a++){float h=StripPose.hinge(a,55,101,172);assertTrue(h>=prev-1e-4);assertTrue(h<90);prev=h;}
 }
 @Test public void blurFactorRampsToTheInnerFactor(){
  assertEquals(1f,StripPose.blurFactor(55,55,101),1e-6);
  assertEquals(1.25f,StripPose.blurFactor(101,55,101),1e-6);
  float mid=StripPose.blurFactor(78,55,101);assertTrue(mid>1f&&mid<1.25f);
 }
 @Test public void invalidInputsFallBackToTheCap(){
  assertEquals(55,StripPose.hinge(80,55,50,172),1e-4);          // switch below cap: keep the old clamp
  assertEquals(55,StripPose.hinge(Float.NaN,55,101,172),1e-4);
 }
}
