package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class DeviceCompatibilityTest {
 @Test public void acceptsRegionalFold8Models() {
  for (String m : new String[]{"SM-F971U", "SM-F971U1", "SM-F971W", "SM-F971B", "SM-F971B/DS", "SM-F971N", "SM-F9710"})
   assertTrue(m, DeviceCompatibility.isEligible(m, 37));
 }
 @Test public void doesNotApplyFold8ProfileToOtherDevicesOrOs() {
  for (String m : new String[]{"SM-F966W", "SM-F956U", "SM-S971U", "SM-F971", "SM-F971U extra", "", null})
   assertFalse(DeviceCompatibility.isEligible(m, 37));
  assertFalse(DeviceCompatibility.isEligible("SM-F971W", 36));
  assertFalse(DeviceCompatibility.isEligible("SM-F971U", 38));
 }
 @Test(expected=IllegalStateException.class) public void rejectsFold7BeforeSetup() {
  DeviceCompatibility.requireEligible("SM-F966W", 36);
 }
}
