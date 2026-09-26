package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class FoldWakePolicyTest {
 @Test public void onlyRecentFoldSleepCanWake(){assertTrue(FoldWakePolicy.recover(1000,1500,13));for(int reason:new int[]{0,1,2,3,4,6,7,8,99})assertFalse(FoldWakePolicy.recover(1000,1500,reason));}
 @Test public void expiredMissingAndFutureLeasesCannotWake(){assertFalse(FoldWakePolicy.recover(0,500,13));assertFalse(FoldWakePolicy.recover(1000,999,13));assertFalse(FoldWakePolicy.recover(1000,6001,13));assertTrue(FoldWakePolicy.recover(1000,6000,13));}
}
