package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SystemBarExperimentPolicyTest {
 @Test public void neverUseOtherPanelEvenWithIdenticalSize(){
  assertTrue(SystemBarExperimentPolicy.matches("local:2",1200,2000,0,"local:2",1200,2000,0));
  assertFalse(SystemBarExperimentPolicy.matches("local:2",1200,2000,0,"local:1",1200,2000,0));
  assertFalse(SystemBarExperimentPolicy.matches("local:2",1200,2000,0,"local:2",2000,1200,0));
  assertFalse(SystemBarExperimentPolicy.matches("local:2",1200,2000,0,"local:2",1200,2000,2));
  assertFalse(SystemBarExperimentPolicy.matches("unknown",1200,2000,0,"unknown",1200,2000,0));
 }
 @Test public void exposeOnlyFullSpanBoundedEdges(){
  assertArrayEquals(new int[]{0,72,1200,1920},SystemBarExperimentPolicy.contentCrop(1200,2000,new int[][]{{0,0,1200,72},{0,1920,1200,2000}}));
  assertArrayEquals(new int[]{0,0,1120,2000},SystemBarExperimentPolicy.contentCrop(1200,2000,new int[][]{{1120,0,1200,2000}}));
  assertArrayEquals(new int[]{0,0,1200,2000},SystemBarExperimentPolicy.contentCrop(1200,2000,new int[][]{{400,1920,800,2000},{0,0,1200,2000},{-1,0,1200,72},{0,100,1200,200}}));
 }
 @Test public void expiredOrFutureGeometryCannotExposeUnderlyingPixels(){
  assertTrue(SystemBarExperimentPolicy.fresh(2000,1000,1000));
  assertFalse(SystemBarExperimentPolicy.fresh(2001,1000,1000));
  assertFalse(SystemBarExperimentPolicy.fresh(999,1000,1000));
  assertFalse(SystemBarExperimentPolicy.fresh(100,0,1000));
 }
 @Test public void statusRefreshPreservesOnlyUnexpiredSamePanelBottomCache(){
  int[] nav={0,1920,1200,2000};int[] status={0,0,1200,72};
  assertTrue(SystemBarExperimentPolicy.keepCached(nav,1200,2000,1000,2000,new int[][]{status}));
  assertFalse(SystemBarExperimentPolicy.keepCached(nav,1200,2000,1000,2000,new int[][]{{0,1800,1200,2000}}));
  assertFalse(SystemBarExperimentPolicy.keepCached(nav,1200,2000,1000,301001,new int[][]{status}));
 }
 @Test public void unsupportedSelectionDefaultsOff(){assertEquals(0,SystemBarExperimentPolicy.mode(4));assertEquals(0,SystemBarExperimentPolicy.mode(-1));assertEquals(3,SystemBarExperimentPolicy.mode(3));}
}
