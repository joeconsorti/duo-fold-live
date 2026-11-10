package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppCaptureRasterTest {
 @Test public void followsPanelAndSupersamplingAtThePinnedSessionResolution(){
  assertEquals(612,AppCaptureRaster.side(2448,25));
  assertEquals(493,AppCaptureRaster.side(1972,25));
  assertEquals(1224,AppCaptureRaster.side(2448,50));
  assertEquals(1440,AppCaptureRaster.side(2448,100));
  assertEquals(1440,AppCaptureRaster.side(2448,200));
 }
 @Test public void oldCallersAndSmallPanelsRetainValidSampleSizes(){
  assertEquals(1440,AppCaptureRaster.side(1972,0));
  assertEquals(1440,AppCaptureRaster.side(1972,-1));
  assertEquals(320,AppCaptureRaster.side(720,25));
  assertEquals(240,AppCaptureRaster.side(240,25));
 }
 @Test public void changingTexelCountPreservesBlurRadiusInPanelCoordinates(){
  for(int side:new int[]{720,1972,2448})for(int percent:new int[]{25,50,75,100,200}){
   int pixels=AppCaptureRaster.side(side,percent);
   assertEquals(72f/Math.min(side,1440),72f*AppCaptureRaster.blurScale(side,pixels)/pixels,1e-6f);
  }
 }
}
