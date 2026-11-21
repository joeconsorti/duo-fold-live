# DuofoldX Batch 2 — Hinge engine stability

## Audit conclusions

The inherited runtime does not currently consume Android's public hinge sensor as its primary angle source. `LiveAngles` polls a Shizuku-side `AngleReader`, which listens for Samsung `SprWallpaper|FoldInteractive` log events, checks the command token and visibility, rejects old epoch-stamped replies, and exposes the last accepted angle to the client. This Samsung-specific source remains the active path until the target firmware is tested.

The repository also contains a reusable `HingeTracker` sensor adapter. Its original capability classifier treated three distinct in-between integer readings as proof of a continuous sensor, even if those readings were only a few degrees apart. It also accepted non-finite, out-of-range, duplicate-time and out-of-order events. Such inputs could corrupt the visual angle or misclassify noise as a continuous sensor.

## Batch 2 candidate changes

- Ignore invalid angles outside 0–180°, non-finite values, non-positive timestamps, duplicate timestamps and out-of-order sensor events. Ignored events do not mutate raw angle, visual angle, or endpoint latches.
- Require at least three distinct in-between readings and at least 12° total spread before classifying a source as continuous. This reduces false continuous classification from near-endpoint sensor jitter.
- Keep time-based smoothing only after continuous capability is established; stepped readings remain exact and unsmoothed.
- Compile the Samsung angle regex once instead of once per matching log line; guard null/empty inputs and reject non-finite parsed values.
- Add focused JVM tests for sensor ordering, invalid values, capability classification and Samsung log parsing.

## Compatibility and safety

No root requirement, Shizuku protocol change, display-state override, capture behavior, consent behavior or release configuration is changed by this batch. The Samsung logcat reader remains the active source. Public SensorManager behavior on SM-F976B / Android 17 / One UI 9.0 remains a physical-device validation item, not an assumption.

## Verification status

Candidate tests have been authored but not executed in an Android Gradle environment. The container could not resolve GitHub for a local checkout, so no claim of a successful build or test run is made. This candidate is prepared separately and has not been added to the branch.
