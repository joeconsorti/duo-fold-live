package org.duofold.live.wallpaperlayer;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.duofold.live.wallpaperlayer.WakeBridgePolicy.State.*;
public class WakeBridgePolicyTest {
 @Test public void longLockWaitDoesNotConsumeHold(){WakeBridgePolicy p=new WakeBridgePolicy();assertEquals(WAITING,p.observe(true,60000));assertEquals(SHOW,p.observe(false,60001));assertEquals(1000,p.remaining(60001));}
 @Test public void earlyHomeReadinessMustWaitForFullSecond(){WakeBridgePolicy p=new WakeBridgePolicy();p.observe(false,100);p.shown(100);assertEquals(994,p.remaining(106));assertEquals(SHOW,p.observe(false,1099));assertEquals(EXPIRED,p.observe(false,1100));}
 @Test public void lateShowGetsFullVisibleSubmissionHold(){WakeBridgePolicy p=new WakeBridgePolicy();p.observe(false,100);p.shown(400);assertEquals(SHOW,p.observe(false,1100));assertEquals(EXPIRED,p.observe(false,1400));}
 @Test public void unlockBroadcastCannotShortenVisibleHold(){WakeBridgePolicy p=new WakeBridgePolicy();p.observe(false,100);p.shown(102);p.unlocked(105);assertEquals(1000,p.remaining(105));p.unlocked(500);assertEquals(EXPIRED,p.observe(false,1105));}
 @Test public void repeatedShowsDoNotExtendHoldForever(){WakeBridgePolicy p=new WakeBridgePolicy();p.shown(100);p.shown(500);assertEquals(EXPIRED,p.observe(false,1100));assertEquals(WAITING,p.observe(true,1200));}
 @Test public void sleepResetCancelsPreviousHold(){WakeBridgePolicy p=new WakeBridgePolicy();p.shown(100);p.unlocked(101);p.reset();assertEquals(WAITING,p.observe(true,5000));p.shown(6000);assertEquals(1000,p.remaining(6000));}
}
