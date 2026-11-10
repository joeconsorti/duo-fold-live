package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class RenderQualityTest {
 @Test public void resolutionStepsMatchBothPanelDimensionsWithoutRoundingLoss(){
  int[][] expected={{25,612,462,312,493},{50,1224,924,624,986},{75,1836,1386,936,1479},{100,2448,1848,1248,1972}};
  int[] panel={2448,1848,1248,1972};
  for(int[] row:expected)for(int i=0;i<panel.length;i++){
   assertEquals(row[i+1],RenderQuality.bufferDimension(panel[i],row[0],true,0));
   assertEquals(row[i+1],RenderQuality.bufferDimension(panel[i],row[0],true,1));
  }
 }
 @Test public void supersamplingRemainsIndependentOfSharedResolution(){
  assertEquals(1224,RenderQuality.bufferDimension(2448,25,true,2));
  assertEquals(4896,RenderQuality.bufferDimension(2448,100,true,2));
  assertEquals(2448,RenderQuality.bufferDimension(2448,100,false,2));
  assertEquals(1836,RenderQuality.bufferDimension(2448,75,true,99));
 }
 @Test public void invalidResolutionCannotInflateOrZeroTheBuffer(){
  for(int value:new int[]{-100,0,1,24,49,76,101,Integer.MAX_VALUE}){
   assertEquals(100,RenderQuality.resolution(value));
   assertEquals(2448,RenderQuality.bufferDimension(2448,value,false,0));
  }
  assertEquals(1,RenderQuality.bufferDimension(0,25,false,0));
  assertEquals(1,RenderQuality.bufferDimension(1,25,true,2));
  assertEquals(936,RenderQuality.bufferDimension(1249,75,false,0));
  assertEquals(Integer.MAX_VALUE,RenderQuality.bufferDimension(Integer.MAX_VALUE,100,true,2));
 }
 @Test public void captureTimeIsIncludedInFrameBudget(){assertEquals(7,RenderQuality.delay(60,10_000_000));assertEquals(4,RenderQuality.delay(120,5_000_000));assertEquals(0,RenderQuality.delay(60,20_000_000));assertEquals(74,RenderQuality.delay(12,10_000_000));}
 @Test public void invalidValuesCannotCreateUnboundedWork(){assertEquals(120,RenderQuality.fps(10000));assertEquals(.75f,RenderQuality.blur(Float.NaN),0);assertEquals(3,RenderQuality.blur(100),0);assertEquals(.07f,RenderQuality.seam(Float.POSITIVE_INFINITY),0);assertEquals(.15f,RenderQuality.seam(1),0);assertEquals(0,RenderQuality.seam(-1),0);}
 @Test public void seamHasContinuousFeatherAtEveryOffset(){for(int step=0;step<=150;step++){float offset=step/1000f;assertEquals(0,RenderQuality.seamBlend(offset,offset),.00001f);assertEquals(offset==0?0:1,RenderQuality.seamBlend(0,offset),.00001f);float prior=1;for(int i=0;i<=100;i++){float blend=RenderQuality.seamBlend(offset*i/100f,offset);assertTrue(blend<=prior+.00001f);assertTrue(blend>=0&&blend<=1);prior=blend;}}assertEquals(.5f,RenderQuality.seamBlend(.045f,.07f),.00001f);}
 @Test public void seamBuildsFromZeroWithFoldMotion(){assertEquals(0,RenderQuality.motion(172,172),0);assertEquals(0,RenderQuality.motion(180,172),0);assertTrue(RenderQuality.motion(171.9f,172)<.001f);assertEquals(1,RenderQuality.motion(90,172),0);assertEquals(0,RenderQuality.motion(Float.NaN,172),0);}
}
