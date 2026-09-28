# Samsung Camera handoff capture — 2026-09-28

Input: Duo-Camera-Handoff-20260928-142953.zip, supplied by Joe.
Firmware: F971USQU3AZI5_OYN3AZI5, Android 17.
Read-only analysis; no app behavior changed.

## Observed
Joe reported no visible blackout when enabling/disabling Camera cover preview, but a black screen when fully closing.

Snapshots:
- Preview off: state 3 OPENED, no override. Logical display 0 stays on inner physical panel ...1459; display 1 is cover ...1204, disabled.
- Preview on: state 4 CONCURRENT_INNER_DEFAULT; requester PID 15824, flags 4. Both panels ON. The same physical/logical assignments remain.
- Preview disabled: state 3, override removed. Same assignments, cover disabled.
- Request stack is DeviceStateManagerService Binder requestState, the same API family used by Duo. Camera APK internals have not been inspected.

Timeline: 85 samples over approximately 30 seconds. State 4 initially; at sample 29 state 0 CLOSED and inner OFF. At sample 35 state 1 TENT; sample 38 state 3 OPENED; sample 46 state 4 returns. Camera's concurrent request was absent while closed and reappeared later. The capture cannot establish why it was canceled or whether reopening automatically re-enabled preview.

Flag 4 was decoded directly from DeviceStateRequest static fields in the previously supplied framework.jar:
- FLAG_CANCEL_WHEN_BASE_CHANGES = 1
- FLAG_NO_CANCEL_WHEN_SCREEN_OFF = 2
- FLAG_NO_USE_NOTIFY = 4
- FLAG_CANCEL_WHEN_FULL_OPEN = 8
- FLAG_NO_CANCEL_WHEN_REQUESTER_NOT_ON_TOP = 16

Thus flags 4 is a notification-related flag by name, not evidence of a seamless-remap option. Do not blindly add it as a blackout fix.

## Interpretation and next test
Camera demonstrates secondary-panel activation without an observed interruption to the primary panel. It does not demonstrate swapping which physical panel is primary without blackout. This matches the distinction between concurrent entry and native primary remapping in prior firmware analysis.

Method 1 has not exposed a distinct state-request mechanism in these diagnostics. Camera could have additional calls not captured here; APK inspection is required to rule that out. APK location: /system/priv-app/SamsungCamera/SamsungCamera.apk.

Next bounded device test: compare native-open -> inner-default concurrent -> native-open and native-closed -> outer-default concurrent -> native-closed. Keep primary physical identity fixed throughout each sequence; observe secondary activation and primary continuity. That isolates the reverse of Camera's demonstrated behavior, not another primary swap. If already equivalent to a prior tested hold, record that rather than call it a novel solution. Full solution still requires native inner layout/input without remap or a genuinely different OEM route.

Capture limitations: filtered logcat file empty; sequential dumps sampled at roughly 350 ms and can miss short OFF intervals. No external optical recording in ZIP. Snapshot ON/committedState ON is not proof of continuous panel illumination. Keep user visual observations and software findings distinct.

## Camera APK follow-up
Inspected SamsungCamera.apk 17.0.00.85 (versionCode 1700085060), SHA-256 21383181dd23bdea2780d33fb9dd8e439e4f38347ccb96cf416cea01d9381b60.

JADX 1.5.2 produced 50 method errors overall. Relevant Camera control methods below were readable. SemWindowManager's wrapper failed structured decompilation, but its emitted instruction listing unambiguously shows the mapping and requestState/cancelStateRequest calls. This is targeted inspection, not a complete audit of native/vendor code.

Traced path:
- l9.h (DualPreviewManager), e(): when main display active and DUAL_PREVIEW enabled, calls a() to create/show the secondary window, then Camera.R.e(4).
- l9.h.a(): enumerates Samsung BUILTIN displays; constructs l9.e using a display/window context; show() is attempted before requesting concurrent state. This is window preparation, not proof a buffer has reached the panel.
- l9.e: display-specific Dialog with SurfaceView, window type 2009 or 2037 chosen by a device check; not focusable (flag 8). It requests the display's current preferred mode.
- k9.d.e(int): calls SemWindowManager.getInstance().setForcedDefaultDisplayDevice(int).
- k9.d.a(): calls that wrapper with 0 to cancel.
- Supplied framework's wrapper maps argument 4 to DeviceStateRequest state 4, flags 4, then DeviceStateManagerGlobal.requestState. Argument 0 cancels. It is not a separate display-power bypass.
- Rear-selfie p9.c uses wrapper argument 6; framework maps 6 to state 5 (CONCURRENT_OUTER_DEFAULT), with ordinary default flags. Wrapper argument 5 maps to state 0, not state 5. These wrapper arguments must never be confused with raw device-state IDs.
- Wrapper argument 7 maps to state 1 with flag 8 (cancel when fully open).

Conclusion: the inspected Camera path has a Samsung wrapper, but its underlying state control is equivalent to the existing concurrent request mechanism. No special primary-remapping exemption was found in this path. The reverse configuration Joe proposed is already represented by Camera rear-selfie/state 5 and Duo's outer-default concurrency; this does not solve returning to native inner primary.

Useful narrow experiment retained: test Camera's secondary-window-first ordering, measuring whether it reduces secondary startup blank frames. Compare against current preparation logic before implementing; classify as content-readiness improvement, not physical blackout elimination. Next major research priority remains the alternate supported-state/layout graph (method 2).
