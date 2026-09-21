package org.duofold.live;

/** Eligibility is not proof of a firmware's Samsung wallpaper/API compatibility. */
public final class DeviceCompatibility {
 private DeviceCompatibility() {}
 public static boolean isFold8(String model) {
  return model != null && model.matches("SM-F971[A-Z0-9]+(?:/DS)?");
 }
 public static boolean isEligible(String model, int sdk) {
  return isFold8(model) && sdk == 37;
 }
 public static void requireEligible(String model, int sdk) {
  if (!isEligible(model, sdk)) throw new IllegalStateException(
   "This Samsung profile requires the SM-F971 family on Android 17; received " + model + " / SDK " + sdk);
 }
}
