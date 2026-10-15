package org.duofold.live;

/** Eligibility is not proof of a firmware's Samsung wallpaper/API compatibility. */
public final class DeviceCompatibility {
 private DeviceCompatibility() {}
 public static boolean isFold8(String model) {
  return model != null && model.matches("SM-F971[A-Z0-9]+(?:/DS)?");
 }
 public static boolean isRecognized(String model) {
  return model != null && model.matches("SM-F(?:971|976|966)[A-Z0-9]+(?:/DS)?");
 }
 public static String modelWarning(String model) {
  return isRecognized(model) ? "" : "Unrecognized device: " + String.valueOf(model)
   + ". You can continue, but compatibility is unverified and features may not work. Samsung wallpaper and API checks still apply.";
 }
 public static boolean isFold7(String model) {
  return model != null && model.matches("SM-F966[A-Z0-9]+(?:/DS)?");
 }
 /** Android 17 everywhere; Android 16 only on the Fold7, the one device the SDK 36 port is verified on. */
 public static boolean isEligible(String model, int sdk) {
  return sdk == 37 || (sdk == 36 && isFold7(model));
 }
 public static void requireEligible(String model, int sdk) {
  if (!isEligible(model, sdk)) throw new IllegalStateException(
   "This Samsung profile requires Android 17 (Android 16 on the Fold7); received " + model + " / SDK " + sdk);
 }
}
