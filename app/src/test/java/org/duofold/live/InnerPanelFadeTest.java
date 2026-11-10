package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class InnerPanelFadeTest {
 @Test public void blackBeforeStartAndFullyVisibleAfterFade(){
  assertEquals(0,InnerPanelFade.visibility(0),0);assertEquals(0,InnerPanelFade.visibility(60),0);
  assertEquals(.5f,InnerPanelFade.visibility(72.5f),.00001f);assertEquals(1,InnerPanelFade.visibility(85),0);
  assertEquals(1,InnerPanelFade.visibility(180),0);assertEquals(0,InnerPanelFade.visibility(Float.NaN),0);
 }
 @Test public void reversingUsesSameContinuousCurveWithoutALatch(){
  float previous=0;
  for(int n=0;n<=1800;n++){float angle=n/10f,v=InnerPanelFade.visibility(angle);assertTrue(v>=previous&&v<=1);previous=v;}
  assertTrue(InnerPanelFade.visibility(60.01f)<.00001f);
  assertTrue(InnerPanelFade.visibility(84.99f)>.99999f);
 }
 @Test public void featherCoversExactlyOneFifthOfPhysicalRightHalf(){
  assertArrayEquals(new float[]{500,0,600,0},InnerPanelFade.gradient(1000,800,0),.001f);
  assertArrayEquals(new float[]{0,500,0,400},InnerPanelFade.gradient(800,1000,1),.001f);
  assertArrayEquals(new float[]{500,0,400,0},InnerPanelFade.gradient(1000,800,2),.001f);
  assertArrayEquals(new float[]{0,500,0,600},InnerPanelFade.gradient(800,1000,3),.001f);
  for(int r=0;r<4;r++){
   float[] b=InnerPanelFade.featherBounds(1000,800,r);
   assertEquals(1000*800*.1f,(b[2]-b[0])*(b[3]-b[1]),.1f);
   assertTrue(b[0]>=0&&b[1]>=0&&b[2]<=1000&&b[3]<=800);
  }
 }
 @Test public void onlyPhysicalLeftHalfIsRemovedInAllRotations(){
  assertArrayEquals(new float[]{0,0,1224,1848},InnerPanelFade.bounds(2448,1848,0),0);
  assertArrayEquals(new float[]{0,1224,1848,2448},InnerPanelFade.bounds(1848,2448,1),0);
  assertArrayEquals(new float[]{1224,0,2448,1848},InnerPanelFade.bounds(2448,1848,2),0);
  assertArrayEquals(new float[]{0,0,1848,1224},InnerPanelFade.bounds(1848,2448,3),0);
 }
}
