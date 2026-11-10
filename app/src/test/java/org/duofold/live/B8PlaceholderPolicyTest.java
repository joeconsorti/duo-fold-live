package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class B8PlaceholderPolicyTest {
 @Test public void onlyTheOutgoingAppImageOnTheOtherPhysicalPanelIsAPreview() {
  for(String[] route : new String[][]{{"cover","inner"},{"inner","cover"}}){
   String source=route[0],dest=route[1];
   assertTrue(B8PlaceholderPolicy.appPreview(false,source,source,dest));
   assertFalse(B8PlaceholderPolicy.appPreview(false,source,source,source));
   assertFalse(B8PlaceholderPolicy.appPreview(false,source,dest,dest));
   assertFalse(B8PlaceholderPolicy.appPreview(false,source,"unknown",dest));
   assertFalse(B8PlaceholderPolicy.appPreview(false,source,source,"unknown"));
   assertFalse(B8PlaceholderPolicy.appPreview(true,source,source,dest));
  }
 }

 @Test public void centeredCropCoversEachDestinationInEitherOrientation() {
  int[][] routes={{1248,1972,2448,1848},{2448,1848,1248,1972},
                  {1972,1248,1848,2448},{1848,2448,1972,1248}};
  for(int[] r : routes)for(int divisor : new int[]{1,4}){
   int sw=r[0]/divisor,sh=r[1]/divisor,dw=r[2],dh=r[3];
   float[] layout=B8PlaceholderPolicy.layout(sw,sh,dw,dh,true);
   float w=sw*layout[0],h=sh*layout[0];
   assertTrue(w>=dw-.001f && h>=dh-.001f);
   assertTrue(Math.abs(w-dw)<.001f || Math.abs(h-dh)<.001f);
   assertEquals(dw/2f,layout[1]+w/2f,.001f);
   assertEquals(dh/2f,layout[2]+h/2f,.001f);
  }
 }

 @Test public void nativeImagesRetainTheirExistingFit() {
  assertArrayEquals(new float[]{1,0,0},B8PlaceholderPolicy.layout(1248,1972,1248,1972,false),0f);
  assertEquals(1248f/2448,B8PlaceholderPolicy.layout(2448,1848,1248,1972,false)[0],0f);
 }
}
