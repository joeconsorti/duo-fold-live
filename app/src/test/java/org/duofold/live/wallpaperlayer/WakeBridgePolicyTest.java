package org.duofold.live.wallpaperlayer;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.duofold.live.wallpaperlayer.WakeBridgePolicy.State.*;
public class WakeBridgePolicyTest {
 @Test public void lockedScreenNeverShowsPhotoEvenAfterLongWait(){WakeBridgePolicy p=new WakeBridgePolicy();assertEquals(WAITING,p.observe(true,0));assertEquals(WAITING,p.observe(true,100000));assertEquals(SHOW,p.observe(false,100001));}
 @Test public void repeatedUnlockEventsCannotExtendTimeout(){WakeBridgePolicy p=new WakeBridgePolicy();assertEquals(SHOW,p.observe(false,100));assertEquals(SHOW,p.observe(false,2099));assertEquals(EXPIRED,p.observe(false,2100));assertEquals(EXPIRED,p.observe(false,2200));}
 @Test public void relockingHidesImmediatelyWithoutExtendingLifetime(){WakeBridgePolicy p=new WakeBridgePolicy();p.observe(false,100);assertEquals(WAITING,p.observe(true,200));assertEquals(SHOW,p.observe(false,300));assertEquals(EXPIRED,p.observe(true,2100));}
 @Test public void newSleepSessionResetsDeadline(){WakeBridgePolicy p=new WakeBridgePolicy();p.observe(false,100);assertEquals(EXPIRED,p.observe(false,2200));p.reset();assertEquals(WAITING,p.observe(true,2300));assertEquals(SHOW,p.observe(false,3000));}
 @Test public void longLockWaitDoesNotConsumeUnlockedFailureBudget(){WakeBridgePolicy p=new WakeBridgePolicy();for(long now=0;now<=60000;now+=8)assertEquals(WAITING,p.observe(true,now));assertEquals(SHOW,p.observe(false,60008));assertEquals(SHOW,p.observe(false,62007));assertEquals(EXPIRED,p.observe(false,62008));}
}
