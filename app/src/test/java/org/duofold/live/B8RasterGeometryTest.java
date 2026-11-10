package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class B8RasterGeometryTest {
 @Test public void innerLandscapeRestorationReplacesStartupPortraitGeometry(){
  B8RasterGeometry g=new B8RasterGeometry(1848,2448,3);
  assertFalse(g.observe(true,1972,1248,1,0)); // cover-size transient during remap
  assertFalse(g.observe(true,2448,1848,0,100));
  assertFalse(g.observe(true,2448,1848,0,174));
  assertTrue(g.observe(true,2448,1848,0,175));
  assertEquals(2448,g.width);assertEquals(1848,g.height);assertEquals(0,g.rotation);
  assertFalse(g.observe(true,2448,1848,0,200));
 }
 @Test public void offUnknownAndContradictoryObservationsCannotResize(){
  B8RasterGeometry g=new B8RasterGeometry(1972,1248,1);
  assertFalse(g.observe(false,1248,1972,0,0));
  assertFalse(g.observe(true,1248,1972,0,100));
  assertFalse(g.observe(false,1248,1972,0,150));
  assertFalse(g.observe(true,1248,1972,0,200));
  assertFalse(g.observe(true,1972,1248,0,250)); // wrong orientation for this physical panel
  assertFalse(g.observe(true,1248,1972,0,300));
  assertTrue(g.observe(true,1248,1972,0,375));
 }
 @Test public void repeatedRotationChangesRestartSettlementAndAllOrientationsWork(){
  for(int initial=0;initial<4;initial++)for(int target=0;target<4;target++){
   if(initial==target)continue;
   B8RasterGeometry g=new B8RasterGeometry(initial%2==0?2448:1848,initial%2==0?1848:2448,initial);
   int w=target%2==0?2448:1848,h=target%2==0?1848:2448;
   assertFalse(g.observe(true,w,h,target,0));assertTrue(g.observe(true,w,h,target,75));
   assertEquals(target,g.rotation);assertEquals(w,g.width);assertEquals(h,g.height);
  }
  B8RasterGeometry g=new B8RasterGeometry(1848,2448,3);
  assertFalse(g.observe(true,1848,2448,1,0));
  assertFalse(g.observe(true,2448,1848,0,50));
  assertFalse(g.observe(true,1848,2448,1,100));
  assertFalse(g.observe(true,1848,2448,1,174));
  assertTrue(g.observe(true,1848,2448,1,175));
 }
}
