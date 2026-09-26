package org.duofold.live.wallpaperlayer;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.duofold.live.wallpaperlayer.WakeBridgePolicy.State.*;
public class WakeBridgePolicyTest {
 @Test public void lockedScreenNeverShowsPhotoEvenAfterLongWait(){WakeBridgePolicy p=new WakeBridgePolicy();assertEquals(WAITING,p.observe(true,0));assertEquals(WAITING,p.observe(true,100000));assertEquals(SHOW,p.observe(false,100001));}
 @Test public void repeatedUnlockEventsCannotExtendTimeout(){WakeBridgePolicy p=new WakeBridgePolicy();assertEquals(SHOW,p.observe(false,100));assertEquals(SHOW,p.observe(false,849));assertEquals(EXPIRED,p.observe(false,850));assertEquals(EXPIRED,p.observe(false,900));}
 @Test public void RelockingHidesImmediatelyWithoutExtendingLifetime(){WakeBridgePolicy p=new WakeBridgePolicy();p.observe(false,100);assertEquals(WAITING,p.observe(true,200));assertEquals(SHOW,p.observe(false,300));assertEquals(EXPIRED,p.observe(true,850));}
 @Test public void newSleepSessionResetsDeadline(){WakeBridgePolicy p=new WakeBridgePolicy();p.observe(false,100);assertEquals(EXPIRED,p.observe(false,900));p.reset();assertEquals(WAITING,p.observe(true,1000));assertEquals(SHOW,p.observe(false,2000));}
}
