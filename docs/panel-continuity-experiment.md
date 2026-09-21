# v1.8.0-alpha.1: physical inner-panel power continuity

This prerelease starts from stable v1.7.0. Stable is not changed.

Android's LogicalDisplayMapper marks displays as transitioning and requests them OFF before applying some new layouts. The observed ~52 ms logical OFF interval survived a direct concurrent-state switch. More image preparation alone cannot resolve physical blanking.

This experiment resolves the built-in inner panel's physical token before handoff and uses SurfaceControl.setDisplayPowerMode(NORMAL) during a bounded handoff window. It reasserts NORMAL only while the tracked panel reports OFF, at most eight times. The window expires after 650 ms, a 250 ms stale lease, lock/sleep, disabling, or an inner-frame submission. Afterward it attempts to restore the current logical power mode. Calls execute on a separate helper thread; a blocked platform call can delay cleanup. No root, brightness changes, or secure-content capture are introduced.

Only the existing cover-preview path uses this helper. Handoff angles, visual treatment, smoothing, and screenshot mode stay unchanged. A missing or rejected API leaves the normal v1.7.0 behavior and reports the error. No claim is made that Samsung will honor the physical request without blanking.

## Phone test

1. Install over the current public build and open Duo.
2. Keep Cover preview ON and Dual-screen screenshot handoff OFF.
3. Advanced → Inner-panel power continuity (experimental) defaults ON.
4. Unfold once and copy the report. Describe whether the visible middle blackout changed.
5. Turn only the new toggle OFF and repeat for comparison.

The report includes physical ON request counts, logical OFF samples, maximum call duration, cleanup status, and the existing bridge trace. Logical OFF may still be reported while the physical ON request is active. Neither a successful call nor a logical-state sample proves illuminated pixels; a phone observation remains necessary. Disable animation or lock/unlock if the experiment behaves poorly.

## References

- [Android LogicalDisplayMapper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/LogicalDisplayMapper.java): transition sequencing.
- [scrcpy SurfaceControl wrapper](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/SurfaceControl.java): shell-side physical display power API usage.
- [scrcpy DisplayControl wrapper](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java): modern Android physical-display token resolution.

Implementation is local to the experimental helper; no scrcpy binary or server is bundled.
