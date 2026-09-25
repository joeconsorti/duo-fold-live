package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class FoldRotationPolicyTest {
 @Test public void unfoldsAndReleasesAtEndpoint(){FoldRotationPolicy p=new FoldRotationPolicy();assertFalse(p.update(0,true,true,0,172));assertTrue(p.update(10,true,true,4,172));assertTrue(p.update(200,true,true,100,172));assertFalse(p.update(300,true,true,172,172));assertFalse(p.update(749,true,true,179,172));assertFalse(p.update(750,true,true,179,172));}
 @Test public void foldsAndRestoresAtClosedEndpoint(){FoldRotationPolicy p=new FoldRotationPolicy();assertFalse(p.update(0,true,true,179,172));assertTrue(p.update(10,true,true,170,172));assertFalse(p.update(100,true,true,0,172));assertFalse(p.update(550,true,true,0,172));}
 @Test public void reversalRetainsHold(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,40,172);p.update(10,true,true,179,172);assertTrue(p.update(200,true,true,150,172));assertTrue(p.update(600,true,true,60,172));}
 @Test public void staleAnglesReleaseAndRequireEndpointBeforeRearming(){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,50,172));assertTrue(p.update(50,true,false,50,172));assertFalse(p.update(1550,true,false,50,172));assertFalse(p.update(1560,true,true,50,172));p.update(1570,true,true,179,172);assertTrue(p.update(1580,true,true,150,172));}
 @Test public void stoppedOrLockedDeviceReleases(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,50,172);assertFalse(p.update(10,false,true,50,172));}
 @Test public void longPartialFoldCannotLeaveRotationLocked(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,50,172);assertFalse(p.update(30000,true,true,50,172));assertFalse(p.update(30001,true,true,50,172));p.update(30002,true,true,0,172);assertTrue(p.update(30003,true,true,10,172));}
 @Test public void invalidAnglesRelease(){FoldRotationPolicy p=new FoldRotationPolicy();p.update(0,true,true,50,172);assertTrue(p.update(10,true,true,Float.NaN,172));assertFalse(p.update(1510,true,true,Float.NaN,172));}
 @Test public void handoffGapDoesNotUnlock(){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,80,172));assertTrue(p.update(100,true,false,80,172));assertTrue(p.update(900,true,true,150,172));assertFalse(p.update(1000,true,true,179,172));assertFalse(p.update(1450,true,true,179,172));}
 @Test public void configuredOpenThresholdReleasesImmediately(){for(float open:new float[]{165,172,179}){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,100,open));assertFalse(p.update(1,true,true,open,open));}}
 @Test public void configuredClosedAngleReleasesImmediately(){FoldRotationPolicy p=new FoldRotationPolicy();assertTrue(p.update(0,true,true,20,172));assertFalse(p.update(1,true,true,FoldThreshold.effectiveAngle(2,2),172));}
 @Test public void endpointJitterDoesNotReacquire(){FoldRotationPolicy p=new FoldRotationPolicy();assertFalse(p.update(0,true,true,0,172));assertFalse(p.update(1,true,true,.4f,172));assertFalse(p.update(2,true,true,179,172));}
}
