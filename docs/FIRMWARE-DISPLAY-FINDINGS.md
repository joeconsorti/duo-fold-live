# Samsung firmware inspection: fixed-mapping continuity limits

Device reported by user: SM-F971U, Android 17. Analyzed the user's uploaded
framework.jar and services.jar after alpha.12. No new APK was built from these
findings. Alpha.12 remains the latest local build; keep-awake fixes remain intact.

## Inputs and method

- services.jar: SHA-256 `3db55d626a84627c10d7d272b512a397e80ed111177966d2742fcad4e3e4ba04`
- framework.jar: SHA-256 `b2fb3c2efdbc740e00371c679cc15804964f6ea392ebfd6ef8635906ceae03fc`

JADX 1.5.2 decompiled the DEX files. services.jar required disabling JADX's DEX
checksum check for inspection; originals were not modified. Decompiler reported
70 errors for services and 24 for framework overall. Findings below reference
readable methods, not a claim that every firmware method was reconstructed.
No handset execution, optical measurement, or SystemUI/One UI Home APK analysis
was performed. No app data, global settings, or device policy was modified.

## Findings from the actual supplied firmware

1. Configuration.semDisplayDeviceType constants: MAIN=0, SUB=5. Its toString
renders them as dt/m and dt/s. Alpha.12 report retained dt/s after the Home task
changed to sw704dp/w933dp. Geometry changed but the Samsung display type did not.

2. DisplayContent.computeScreenConfiguration assigns main/sub on the default
logical display using FoldDisplayController.isInPrimaryDevice(DisplayInfo).
DisplayContent.onRequestedOverrideConfigurationChanged explicitly assigns
main/sub on default, 10 for external desktop, and -1 for other displays. Thus a
naive per-display main-profile override is overwritten; ordinary secondary
configuration inherits rather than independently selecting the native main role.
This establishes the configuration mismatch, but does not prove every detail of
One UI Home's internal layout selection without inspecting its own APK.

3. WindowManagerService installs FoldDisplayController.AnonymousClass1 as its
ExtraDisplayPolicy. shouldNotTopDisplay(id) returns id == 1. DisplayContent.
canStealTopFocus rejects that policy before consulting optional per-display focus
settings; WindowManagerService.moveDisplayToTopIfAllowed also rejects it.
This explains why task/root focus requests did not establish normal top focus
on logical display 1. It is not evidence of a missed delay or missing retry.

4. DisplayContent.isSystemDecorationsSupported checks display settings/flags.
Its forced-desktop fallback excludes FLAG_REAR (8192). The actual Display.java
confirms 0x608b contains FLAG_REAR and lacks FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS
(64) and FLAG_ALLOWS_CONTENT_MODE_SWITCH (32768). The device's reflected API list
and actual IWindowManager interface do not expose the attempted decoration setter.
getDisplayDecorationSupport is not a replacement setter. Enabling generic desktop
mode is not a verified route to native Samsung inner Home/taskbar behavior.

5. LogicalDisplayMapper.resetLayoutLocked marks displays transitioning if their
physical device changes logical ID, enabled state or layout membership.
setDeviceStateLocked waits for areAllTransitioningDisplaysOffLocked before the
normal mapping transition (with a separate timeout path). DisplayManagerService's
requestDisplayStateInternal path rejects non-OFF requests while the logical
display is transitioning or disabled. This matches the prior logged OFF intervals
at mapping handoff; it is not an exact measure of the visible blackout duration.

6. FoldDisplayController identifies device-state IDs 0, 1, and 5 as folded; 4/5
are dual modes. The app resolves the named concurrent states rather than relying
on an unverified replacement integer. Remaining in outer-default concurrency
therefore also preserves Samsung's folded state semantics, even at a large hinge
angle. Task transfer alone cannot change that state classification.

## Engineering conclusion

The existing fixed outer-default mapping can keep interactive task content and
animation on the inner panel but is not native inner-display mode. Repeating task
focus, geometry, or missing decoration API requests does not resolve these
firmware policies. Returning to native primary mapping restores the intended
Samsung behavior but invokes the observed power transition.

No supported app/Shizuku-only way to obtain both native inner behavior and
continuous panel-on operation has been established. This is a finding about the
examined route, not proof that every possible OEM mechanism has been exhausted.
A genuine zero-blackout/native-layout solution would need an OEM-supported
transition path or system-level changes. Do not present another diagnostic APK
as that solution. Preserve current working keep-awake, native task transfer and
animation while deciding the next development scope.
