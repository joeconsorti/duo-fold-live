package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContinuityProbePolicyTest {
 private ContinuityProbePolicy active(){ContinuityProbePolicy p=new ContinuityProbePolicy();p.update(1000,1000,0,true,true,false);assertEquals(1,p.update(1100,1000,20,true,true,true));return p;}
 @Test public void requiresClosedAfterArming(){ContinuityProbePolicy p=new ContinuityProbePolicy();assertEquals(0,p.update(1000,1000,20,true,true,true));assertEquals(0,p.update(1100,1000,180,true,true,true));}
 @Test public void holdsAcrossNormalSwitchAndEndpoint(){ContinuityProbePolicy p=active();assertEquals(1,p.update(1200,1000,98,true,true,true));assertEquals(1,p.update(1300,1000,180,true,true,true));}
 @Test public void expiresAndDoesNotRearm(){ContinuityProbePolicy p=active();assertEquals(2,p.update(21100,1000,180,true,true,true));assertEquals(0,p.update(21200,1000,0,true,true,false));assertEquals(0,p.update(21300,1000,20,true,true,true));}
 @Test public void repeatedPressDoesNotExtendDeadline(){ContinuityProbePolicy p=active();assertEquals(1,p.update(20000,20000,120,true,true,true));assertEquals(2,p.update(21100,20000,120,true,true,true));}
 @Test public void releaseOnCancelLockStaleClosedOrLostRequest(){assertEquals(2,active().update(1200,0,90,true,true,true));assertEquals(2,active().update(1200,1000,90,true,false,true));assertEquals(2,active().update(1200,1000,90,false,true,true));assertEquals(2,active().update(1200,1000,0,true,true,true));assertEquals(2,active().update(1200,1000,90,true,true,false));assertEquals(2,active().update(1200,1000,Float.NaN,true,true,true));}
 @Test public void staleTokenCannotArmAfterHelperRestart(){ContinuityProbePolicy p=new ContinuityProbePolicy();p.update(40000,1000,0,true,true,false);assertEquals(0,p.update(40100,1000,20,true,true,true));}
 @Test public void armedTimeoutAndAbort(){ContinuityProbePolicy p=new ContinuityProbePolicy();p.update(1000,1000,0,true,true,false);assertEquals(0,p.update(31000,1000,20,true,true,true));p.update(32000,32000,0,true,true,false);p.abort();assertEquals(0,p.update(32100,32000,20,true,true,true));}
 @Test public void cannotBeginAboveEightyDegrees(){ContinuityProbePolicy p=new ContinuityProbePolicy();p.update(1000,1000,0,true,true,false);assertEquals(0,p.update(1100,1000,90,true,true,true));}
 @Test public void recordsDistinctExitReasons(){
  ContinuityProbePolicy p=active();p.update(1200,0,90,true,true,true);assertTrue(p.status.contains("Stop requested"));
  p=active();p.update(1200,1000,90,false,true,true);assertTrue(p.status.contains("angle stale"));
  p=active();p.update(1200,1000,90,true,true,false);assertTrue(p.status.contains("request canceled/lost"));
  p=active();p.update(1200,1000,0,true,true,true);assertTrue(p.status.contains("fully closed"));
  p=active();p.update(21100,1000,180,true,true,true);assertTrue(p.status.contains("20-second deadline"));
  String ended=p.status;p.abort();assertEquals(ended,p.status);
 }
}
