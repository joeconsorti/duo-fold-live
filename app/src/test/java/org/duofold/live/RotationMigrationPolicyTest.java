package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class RotationMigrationPolicyTest {
 @Test public void upgradeNeedsRepairUntilComplete(){assertTrue(RotationMigrationPolicy.needed(false,100,200));assertFalse(RotationMigrationPolicy.needed(true,100,200));}
 @Test public void laterUpgradesCannotRearmCompletedRepair(){assertFalse(RotationMigrationPolicy.needed(true,100,999999));}
 @Test public void firstInstallDoesNotChangeRotation(){assertFalse(RotationMigrationPolicy.needed(false,100,100));}
 @Test public void failuresBackOffAndRemainBounded(){assertEquals(15000,RotationMigrationPolicy.retryDelay(1));assertEquals(30000,RotationMigrationPolicy.retryDelay(2));assertEquals(240000,RotationMigrationPolicy.retryDelay(5));assertEquals(240000,RotationMigrationPolicy.retryDelay(100));}
}
