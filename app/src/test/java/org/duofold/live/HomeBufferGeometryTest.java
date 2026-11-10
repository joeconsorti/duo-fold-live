package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeBufferGeometryTest {
 @Test public void exactInsetNativeWindowIsAccepted(){assertTrue(HomeBufferGeometry.exactWindow(1248,1900,0,24,1248,1924,1248,1972));}
 @Test public void oldPanelAndAnimatedSizesAreNotInsetMatches(){
  assertFalse(HomeBufferGeometry.exactWindow(2448,1848,0,0,1248,1972,1248,1972));
  assertFalse(HomeBufferGeometry.exactWindow(1248,1972,20,20,1228,1952,1248,1972));
  assertFalse(HomeBufferGeometry.exactWindow(1208,1932,20,20,1228,1952,2448,1848));
 }
 @Test public void offscreenAndPartialWindowsReject(){
  assertFalse(HomeBufferGeometry.exactWindow(1248,1972,-1,0,1247,1972,1248,1972));
  assertFalse(HomeBufferGeometry.exactWindow(624,986,0,0,624,986,1248,1972));
 }
}
