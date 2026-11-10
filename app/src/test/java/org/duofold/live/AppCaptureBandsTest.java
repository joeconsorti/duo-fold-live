package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppCaptureBandsTest {
 @Test public void twoRecordedYouTubeFadesRequireAnotherCapture(){
  for(int alpha:new int[]{15,49,191}){
   AppCaptureBands candidate=AppCaptureBands.sample(1248,1972,(x,y)->(alpha<<24)|0x4488cc);
   assertTrue(candidate.hasPixels);
   assertTrue(candidate.fadedFromOpaque(true));
   assertFalse(candidate.fadedFromOpaque(false));
   assertFalse(candidate.opaqueReference());
  }
  AppCaptureBands opaqueBlack=AppCaptureBands.sample(2448,1848,(x,y)->0xff000000);
  assertTrue(opaqueBlack.opaqueReference());
  assertFalse(opaqueBlack.fadedFromOpaque(true));
 }
 @Test public void aSingleTransparentRegionDoesNotRejectTheApp(){
  AppCaptureBands bars=AppCaptureBands.sample(100,100,(x,y)->y>=40&&y<60?0xff000000:0);
  assertFalse(bars.opaqueReference());
  assertFalse(bars.fadedFromOpaque(true));
 }
 @Test public void blackOpaqueContentAndTransparentInteriorStayDistinct(){
  AppCaptureBands black=AppCaptureBands.sample(100,100,(x,y)->0xff000000);
  assertTrue(black.hasPixels);
  assertTrue(black.report().contains("meanAlpha=255/255/255 visibleMeanLuma=0/0/0"));
  AppCaptureBands missing=AppCaptureBands.sample(100,100,(x,y)->y>=40&&y<60?0:0xff808080);
  assertTrue(missing.hasPixels);
  assertTrue(missing.report().contains("meanAlpha=255/0/255 visibleMeanLuma=128/-1/128"));
  assertFalse(AppCaptureBands.sample(100,100,(x,y)->0).hasPixels);
 }
}
