package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class DeviceCompatibilityTest {
 @Test public void recognizesAllRequestedRegionalFamilies() {
  for(String family:new String[]{"971","976","966"})
   for(String region:new String[]{"U","U1","W","B","B/DS","N","0"}) {
    String model="SM-F"+family+region;
    assertTrue(model,DeviceCompatibility.isRecognized(model));
    assertTrue(model,DeviceCompatibility.isEligible(model,37));
    assertEquals("",DeviceCompatibility.modelWarning(model));
   }
 }
 @Test public void unknownModelsWarnButProceed() {
  for(String model:new String[]{"SM-F956U","unknown","",null}) {
   assertFalse(DeviceCompatibility.isRecognized(model));
   assertFalse(DeviceCompatibility.modelWarning(model).isEmpty());
   DeviceCompatibility.requireEligible(model,37);
  }
 }
 @Test public void preservesOsGate() {
  assertFalse(DeviceCompatibility.isEligible("SM-F966W",36));
  assertFalse(DeviceCompatibility.isEligible("unknown",38));
 }
 @Test(expected=IllegalStateException.class) public void rejectsUnsupportedOs() {
  DeviceCompatibility.requireEligible("SM-F966W",36);
 }
}
