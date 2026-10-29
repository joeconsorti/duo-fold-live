package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomePreviewPolicyTest {
 @Test public void onlyHomeCrossPanelPreparationIsEligible(){
  assertTrue(HomePreviewPolicy.eligible(true,1248,1972,2448,1848));
  assertTrue(HomePreviewPolicy.eligible(true,1972,1248,1848,2448));
  assertFalse(HomePreviewPolicy.eligible(false,1248,1972,2448,1848));
  assertTrue(HomePreviewPolicy.eligible(true,2448,1848,1248,1972));
  assertTrue(HomePreviewPolicy.eligible(true,1848,2448,1972,1248));
  assertFalse(HomePreviewPolicy.eligible(false,2448,1848,1248,1972));
  assertFalse(HomePreviewPolicy.eligible(true,2448,1848,2448,1848));
  assertFalse(HomePreviewPolicy.eligible(true,1248,1972,1248,1972));
  assertFalse(HomePreviewPolicy.eligible(true,0,1972,2448,1848));
 }
 @Test public void missingNativeContentCannotBecomeClearByTimeoutOrEndpoint(){
  HomePreviewPolicy p=new HomePreviewPolicy();
  assertEquals(1,p.opacity(false,false,10),0);
  assertEquals(1,p.opacity(false,true,30000),0);
  assertFalse(p.animating());
 }
 @Test public void readyAtFirstDrawSkipsPlaceholderEntirely(){
  HomePreviewPolicy p=new HomePreviewPolicy();
  assertEquals(0,p.opacity(true,false,100),0);assertFalse(p.animating());
 }
 @Test public void dissolveBeginsOnNativeArrivalAndEndsIn120ms(){
  HomePreviewPolicy p=new HomePreviewPolicy();p.opacity(false,false,100);
  assertEquals(1,p.opacity(true,false,1000),0);assertTrue(p.animating());
  assertEquals(.5,p.opacity(true,false,1060),.00001);
  assertEquals(0,p.opacity(true,false,1120),0);assertFalse(p.animating());
  assertEquals(0,p.opacity(true,false,1200),0);
 }
 @Test public void nativeRefreshDoesNotRestartOrStepBackTheDissolve(){
  HomePreviewPolicy p=new HomePreviewPolicy();p.opacity(false,false,0);p.opacity(true,false,100);
  float previous=1;for(int t=100;t<400;t+=10){float alpha=p.opacity(true,false,t);assertTrue(alpha<=previous);assertTrue(alpha>=0);previous=alpha;}
  assertEquals(0,previous,0);
 }
 @Test public void endpointSkipsRemainingCosmeticWait(){
  HomePreviewPolicy p=new HomePreviewPolicy();p.opacity(false,false,0);p.opacity(true,false,100);
  assertEquals(0,p.opacity(true,true,101),0);assertFalse(p.animating());
  assertEquals(0,p.opacity(true,false,102),0);
 }
 @Test public void invalidatedNativeLayoutCannotUncoverUnpreparedPixels(){
  HomePreviewPolicy p=new HomePreviewPolicy();p.opacity(false,false,0);p.opacity(true,false,100);
  assertEquals(1,p.opacity(false,false,140),0);assertFalse(p.animating());
  assertEquals(1,p.opacity(true,false,200),0);assertEquals(0,p.opacity(true,false,380),0);
 }
}
