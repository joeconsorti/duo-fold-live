package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SystemBarPixelRefreshTest {
 @Test public void destinationUsesOneImagePerGeometryRevision(){
  var p=new SystemBarPixelRefresh(1);p.pixels("inner","status");assertEquals("status",p.known("inner"));
  p.pixels("inner","status+taskbar");assertEquals("status+taskbar",p.known("inner"));assertEquals("",p.known("cover"));
 }
 @Test public void initialAndSettlingImagesThenMetadataOnly(){
  var p=new SystemBarPixelRefresh();assertEquals("",p.known("inner"));
  p.pixels("inner","status");assertEquals("",p.known("inner"));
  p.pixels("inner","status");assertEquals("status",p.known("inner"));
 }
 @Test public void arrivingTaskbarGetsOwnSettlingRefresh(){
  var p=new SystemBarPixelRefresh();p.pixels("inner","status");p.pixels("inner","status");
  p.pixels("inner","status+taskbar");assertEquals("",p.known("inner"));
  p.pixels("inner","status+taskbar");assertEquals("status+taskbar",p.known("inner"));
 }
 @Test public void panelAndSessionStateAreIndependentAndMissingSignatureCannotSuppressCapture(){
  var p=new SystemBarPixelRefresh();p.pixels("inner","status");p.pixels("inner","status");
  assertEquals("",p.known("cover"));assertEquals("",new SystemBarPixelRefresh().known("inner"));
  p.pixels("cover",null);p.pixels("cover","");assertEquals("",p.known("cover"));
 }
 @Test public void gestureHandleRefreshDoesNotEraseTaskbarApps(){
  int[] taskbar={100,1800,1100,1960},handle={0,1950,1200,2000},status={0,0,1200,72};
  assertTrue(SystemBarExperimentPolicy.keepSession(taskbar,1000,2000,new int[][]{handle,status}));
  assertFalse(SystemBarExperimentPolicy.keepSession(taskbar,1000,2000,new int[][]{{0,1750,1200,2000}}));
  assertFalse(SystemBarExperimentPolicy.keepSession(taskbar,1000,31001,new int[][]{status}));
  assertEquals("bottom",SystemBarExperimentPolicy.edge(taskbar,1200,2000));
 }
}
