package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SystemBarWorkPolicyTest {
 @Test public void slowOptionalResultExpiresWithoutExtendingDiscovery(){
  var p=new SystemBarWorkPolicy();assertTrue(p.available(100));assertTrue(p.available(1599));
  assertFalse(p.accepts(1600));assertFalse(p.available(2000));assertFalse(p.accepts(99));
 }
 @Test public void allEdgesStopPollingButPartialStatusAllowsLateTaskbar(){
  assertFalse(SystemBarWorkPolicy.complete(true,true,true,false));
  assertTrue(SystemBarWorkPolicy.complete(true,true,false,true));
  assertTrue(SystemBarWorkPolicy.complete(false,true,true,false));
  var p=new SystemBarWorkPolicy();assertTrue(p.available(100));p.complete();assertFalse(p.available(101));
 }
 @Test public void knownCoverDimensionsRejectTransientInnerGeometryWithSameId(){
  assertFalse(SystemBarWorkPolicy.geometry(1248,1972,0,2448,1848,0));
  assertFalse(SystemBarWorkPolicy.geometry(1248,1972,0,1248,1972,1));
  assertTrue(SystemBarWorkPolicy.geometry(1248,1972,0,1248,1972,0));
 }
 @Test public void optionalPixelsScaleWithPinnedOutputAndNeverUpsample(){
  assertEquals(493,SystemBarWorkPolicy.rasterSide(1248,1972,493));
  assertEquals(612,SystemBarWorkPolicy.rasterSide(2448,1848,612));
  assertEquals(1440,SystemBarWorkPolicy.rasterSide(2448,1848,2448));
  assertEquals(200,SystemBarWorkPolicy.rasterSide(120,200,1440));
 }
}
