# Duo Fold Live: root (KernelSU) port, phases 1 and 2

**Status: written without being able to compile or run it.** No Android SDK, Gradle or Samsung device was
available when this was produced. Treat it as a first draft that needs one build and one on-device test pass.

## What this phase does

Shizuku is gone. Everything it did is done through root (libsu) instead:

| Before (Shizuku, ADB mode) | Now (root) |
| --- | --- |
| `Shizuku.bindUserService(AngleReader…)` | libsu `RootService` hosting the same `AngleReader` Binder class |
| same for `HostLauncher`, `FoldSettingsService`, `DisplaySettingsService` | same, one root service wrapper each (`app/.../root/*RootService.java`) |
| `WallpaperSetupService` (needed uid 2000) | one-shot process started from the root shell that drops to uid/gid 2000 and runs the unchanged class (`ShellOneShot`, `WallpaperSetupProxy`) |
| Start Shizuku after every reboot, wireless/USB debugging | nothing to start; root is requested automatically |
| Manual overlay + accessibility + restricted-settings steps | applied automatically once per version (`RootGrants`), can be switched off with `auto_grants=false` in the `root_access` prefs |

Mechanics: `org.duofold.live.root.Shizuku` is a shim with the same class name and method signatures as the
Shizuku client calls the app used, so the rest of the code only had its import changed. "Binder alive" now
means "root granted".

Other edits: `System.exit(0)` in the helper classes goes through `PrivRuntime.exit`, which does nothing when
several helpers share one root process; the `uid == 2000` gate in `HostLauncher` also accepts 0; the Shizuku
provider, permission, queries entry and `app/libs/*.jar` are removed; the setup/connection screens and status
texts talk about root. The animation, glass renderer, handoff logic and wallpaper layer are untouched.

## What this phase does NOT do

The app still declares `SYSTEM_ALERT_WINDOW` and still runs `StandaloneService` as an enabled accessibility
service. Security apps can still see both. `RootGrants` only stops *you* having to click them. Removing them is
phase 2 (below).

## Build

JDK 17, Android SDK 36, then `./gradlew assembleDebug`. libsu 6.0.0 comes from JitPack (added in
`settings.gradle.kts`). Check that version number and that JitPack is reachable if dependency resolution fails.
Install over the original only if it is signed with the same key; otherwise uninstall first (settings are lost).

## First-run test checklist

1. Open the app, tap **Authorize root**, approve in KernelSU Manager. Status should say root granted.
2. Setup step "Apply required wallpapers" (runs as shell uid 2000 via the one-shot helper). Copy the
   connection report if it fails; the error text from `ShellOneShot` is passed through.
3. Enable the animation. After a few seconds `Angle reader` should report fresh angles
   (`AngleReader` reads Samsung's `FoldInteractive` angle from logcat as root).
4. Settings → Accessibility should now show Duo enabled, and overlay permission granted, without clicking.
5. Reboot. Nothing should need starting; check the angles come back on their own.

## Things most likely to need a fix on the device

* **libsu `android:process=":root"`** on the service declarations follows libsu's own sample. If binding
  never completes, try removing that attribute.
* **Helpers now run as root (uid 0)** instead of shell (uid 2000), except wallpaper setup. Root passes every
  permission check, but code that passes the calling package `com.android.shell` while running as uid 0 is
  rejected by AppOps. That is why the wallpaper helper is kept on uid 2000. If another helper
  (rotation hold, display handoff, capture) throws a "package does not belong to uid" error, look in the
  connection report and tell me which call.
* `HostLauncher` starts `am instrument` as root now. UiAutomation accepts a root owner, but that is the first
  thing to check if the custom wallpaper photo does not start.
* SELinux: the helpers run in KernelSU's `su` domain, not `shell`. Denials would show in `logcat | grep avc`.
* `ShellOneShot` drops uid/gid with `Os.setgid/setuid` and keeps no supplementary groups; fine for the
  wallpaper API, not meant for anything that reads logcat.

## Phase 2 (the part that actually removes the detectable bits)

The code already contains the pieces. The custom-photo layer runs in `PhotoInstrumentation`, an
`am instrument` process that adopts shell permissions through UiAutomation, and `WindowAttachment` already
attaches SurfaceControl overlays through UiAutomation's accessibility connection. UiAutomation is not an
installed accessibility service and does not need the overlay permission.

Plan: move `StandaloneService`'s windows (the Compose overlay on display 0, `NativeInnerOverlay` and
`SecondaryShade` on display 1) into that instrumentation process, render the Compose content through
`SurfaceControlViewHost`, and attach it with `attachAccessibilityOverlayToDisplay`. Then delete
`StandaloneService`, its accessibility config and the `SYSTEM_ALERT_WINDOW` permission. This is a real
rewrite of the render host, should be done only after phase 1 works, and needs a few test rounds on the
Fold 8 because the hidden window APIs differ per firmware.


---

# Phase 2 (this build): no accessibility service, no overlay permission

**Also written without compiling or running it.** Keep the phase-1 APK around as a fallback.

## What changed

| Before | Now |
| --- | --- |
| `StandaloneService` was an enabled accessibility service, the animation was a `TYPE_ACCESSIBILITY_OVERLAY` window | `StandaloneService` is a plain object created by `FoldBackgroundService`. The animation is a top-level compositor layer (`LayerOverlay`: `SurfaceControlViewHost` + `setLayerStack` + `setLayer(MAX)`), no window, no input |
| Needed `SYSTEM_ALERT_WINDOW` and Settings → Accessibility | Manifest no longer declares either; the accessibility service and `accessibility.xml` are deleted |
| Host of the layer | `PhotoInstrumentation`, the registered instrumentation already used by the custom photo. It is started through `HostLauncher` (root) whenever the animation OR the custom photo is on, adopts `ACCESS_SURFACE_FLINGER`, and signals `UiHost.connected()` |
| Display 1 shading (`SecondaryShade`) | unchanged `Presentation`, but built on the app context instead of the accessibility service context; `NativeInnerOverlay` uses the same compositor-layer approach as the main overlay |

UiAutomation (used by the registered host) is not an installed accessibility service and does not appear in
Settings → Accessibility or in `AccessibilityManager.getEnabledAccessibilityServiceList`.

`RootGrants` now removes the accessibility entry the phase-1 build wrote to `Settings.Secure` (it runs once per
version code; bump `versionCode` if you rebuild without changing it, or run the two `settings` commands by hand).

## What is still visible

* Nothing by default. The READ_n wallpaper command that makes Samsung's fold wallpaper report the hinge angle is now
  sent by the root helper (`AngleReader.poke`, `IWallpaperManager.semSendWallpaperCommand` for slots 5 and 17), not
  from 1x1 overlay windows. The old `TYPE_APPLICATION_OVERLAY` anchors in `LiveAngles` are only a fallback, used if the
  root command fails or no fresh angle arrives for 6 s; they need the `SYSTEM_ALERT_WINDOW` app-op (`appops set`,
  not declared in the manifest). Fresh installs hit "permission denied for window type 2038" if that fallback is needed
  and the app-op is not honoured.
* An instrumentation entry (`functionalTest`) in the manifest and a foreground-service notification.
* Root itself, which the usual root hiding has to cover.

## First-run checks

1. Install, open the app, grant root, enable the animation. The connection report should show
   `Window host ready: true` and a status ending in `(compositor layer)`.
2. Settings → Accessibility → Installed apps must **not** list Duo Fold Live.
3. Fold and unfold. If nothing is drawn, send me the report: the status line says which step failed.

## Most likely failure points

* **`am instrument` as root**: the registered host is launched by `HostLauncher` as root now. If the host never
  reports ready, `Window host ready: false` stays and `Wallpaper:`/layer reports in the app show the reason.
* **`setLayerStack` / `ACCESS_SURFACE_FLINGER`**: the layer is only placed if the adopted permission is honoured.
  `LayerOverlay.show` throws a clear `SecurityException` otherwise. (`NativePhotoLayer` already relies on this.)
* **Visibility/z-order of the layer** is verified only on the phone; the layer is placed at `Integer.MAX_VALUE`.
* **Hinge position**: `WindowInfoTracker` needs a UI context, so the hinge falls back to the centred default.
* **Angle trigger**: the connection report has an `Angle trigger:` line. If the engine does not answer the slot
  command while an app covers the wallpaper, angles stall and the window fallback starts; if that is denied (type
  2038), re-add `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>` to the manifest as a test.


## Update after the first phase-2 test on the Fold 8

* The compositor-layer overlay works (`Window host ready: true`, status `Ready — … (compositor layer)`).
* The root-sent slot command (`semSendWallpaperCommand`) did NOT make Samsung's engine report angles (0 replies after
  9355 polls), so it is switched off (`AngleReader.POKE_ENABLED=false`).
* The 1x1 angle anchors (window-based wallpaper command) are the angle trigger again. With only the app-op they were
  refused (type 2038), so the manifest declares `SYSTEM_ALERT_WINDOW` again and root sets the app-op
  (`RootGrants`, key `grants3`). Result: no accessibility service and no manual overlay step, but the app
  does show the overlay permission again.
* Optional step 3 (not done): create the anchors from the registered host with a non-SAW window type that
  INTERNAL_SYSTEM_WINDOW allows, so the manifest can drop the permission again.

## Update: second display (dual handoff and cover preview)

With the Presentation-based `SecondaryShade`, the log said "Secondary Presentation shown / first draw on display 1"
but the outgoing panel kept showing its normal screen: the Presentation window did not end up on top of the panel's own
content. `SecondaryShade` is now drawn into a compositor layer on display 1 (`LayerOverlay`, the same mechanism that
already works for the primary display), so it sits above the panel's content. The Compose content, frozen frame,
live mirror (`LivePanelSurface`) and the lifecycle API used by `StandaloneService` are unchanged.
Not carried over: the Presentation's `FLAG_KEEP_SCREEN_ON` (the handoff lasts a few seconds).

## Diagnostic build note (second panel)

`SecondaryShade.DEBUG_SECOND_PANEL = true` paints the second-panel layer purple with a white label and logs the
centre pixel of the frozen frame. Set it to `false` (one constant) once the second panel works.

## Second panel: attach through the window manager

The free-standing layer on display 1 (layerStack=1) was not shown above that panel's own content. On any display other
than the primary, `LayerOverlay` now attaches its layer with `IAccessibilityServiceConnection.attachAccessibilityOverlayToDisplay`
through the registered host's UiAutomation connection (`DisplayOverlayAttachment`, same pattern as `WindowAttachment`),
so the window manager parents it in that display's overlay hierarchy. If the call is missing or returns a non-zero result
it falls back to the free-standing layer. The primary display keeps the free-standing layer, which works.
Look for `Display overlay requested via window manager` and `Display overlay attach result … 0 (success)` in the events.

## Second panel: layer was never shown

Log: `Display overlay attach result … 0 (success)` but nothing visible on the second panel. A `SurfaceControlViewHost` root
is created hidden and must be shown by the embedder. `LayerOverlay.showLayer()` now shows it on the window-manager path
(immediately and again when the attach result is 0); the free-standing path already did via `place()`.

## Overlay renderer switch (test)

Layers placed by hand or attached through the window manager were unreliable (flash, then gone; second panel never
visible). `LayerOverlay` now has three renderers, chosen by the button "Overlay renderer (test)" in Advanced
(`standalone` prefs, key `overlay_mode`): `window` (default; a `TYPE_SYSTEM_DIALOG` window created by the host with the
trusted-overlay flag, closest to the original accessibility-overlay window), `wm`, `free`. The setting applies to the
primary overlay, the second-panel shade and the native inner overlay.

## Safety changes after "it takes over the phone"

Default renderer is `free` again (the other two were reported to take over the screen), the purple diagnostic is off
(`DEBUG_SECOND_PANEL=false`), and a watchdog dismisses the second-panel overlay after 20 s (and keeps it away for 10 s).
Escape hatch if an overlay sticks: `am force-stop org.duofold.live` (windows and layers belong to the app process).

## Lock-out protection (added after the overlay took over the phone)

* `StandaloneService.watchOverlay()`: effect visible for 45 s -> hidden, animation turned off, event logged.
* `DuoApplication.safeModeCheck()`: starts with the animation OFF (and renderer `free`) on the first run of this build and
  after a restart that happened while the effect was on screen.
* Without opening the app (USB debugging on, from a computer):
  `adb shell am force-stop org.duofold.live` removes the overlay at once;
  `adb shell run-as org.duofold.live cat shared_prefs/recovery_log.xml` prints the event log;
  `adb shell run-as org.duofold.live rm shared_prefs/standalone.xml` resets all settings (animation off).

## Touch blocking

Report: with the `window` / `wm` renderers on the active screen the animation looked right on both panels, but taps did
not reach apps (only the system's bottom-edge swipe worked). A full-screen window from another process that is not a
trusted overlay makes Android drop touches that pass through it (untrusted-touch protection), so the primary display now
always uses the `free` layer (no input channel, never touch-blocking). `overlay_mode` (default `wm`) applies to the second
panel only. The first-run safe mode is re-armed once more (`overlay_safe_v3`).

## Active-screen renderer test switch

New button "Active-screen renderer (TEST, may block touch)" (`primary_mode`: free | wm | window). Default and fallback is
`free`; any other value is reset to `free` each time the app process starts, so a restart or
`adb shell am force-stop org.duofold.live` always returns to the touch-safe renderer. Purpose: check whether the second
panel only appeared to animate earlier because the main overlay was a window that followed display 0 across the panel swap.

## Active-screen test is time limited

Report: with `primary_mode=wm` the animation showed on both panels but taps were blocked (also while the effect was
invisible, so the 45 s visibility watchdog never fired). The test value now expires after 60 s
(`primary_mode_until`, checked by `StandaloneService.checkPrimaryTest()` every 250 ms), then the overlay is rebuilt as
`free`. Diagnostic for the touch block (from a computer): `adb logcat -d | grep -i -E "untrusted|occlu"`; experiment:
`adb shell settings put global block_untrusted_touches 0` (restore with `... 2`).

## Input diagnostics for the touch lock

`block_untrusted_touches 0` made no difference, so the untrusted-touch protection is not the cause. During a wm/window
renderer test the app now captures (as root, 10 s in) `dumpsys input` dispatcher state, Duo/overlay lines of
`dumpsys window` and matching logcat lines into the `diag_input` preference; "Copy input diagnostics" copies it
(it survives the automatic revert to `free`). Observation that shapes the next step: with `wm`/`window` the animation
appears on both panels, with `free` only on the active one, so the second panel seems to show a copy of the window
manager's tree for display 0, not layer stack 0.

## Touch lock found: the embedded window took focus and touches

`dumpsys input` during a `wm` test: `FocusedWindows: displayId=0, name='Embedded{}'` and a window `Embedded{}`
(`ownerUid` = Duo, `inputConfig=TRUSTED_OVERLAY`, `touchableRegion` = the whole display). `SurfaceControlViewHost.setView(view, w, h)` uses
layout flags 0, so the embedded window got an input channel, focus and a full-screen touchable region.
`LayerOverlay` now calls `setView(view, LayoutParams)` with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE` and
`INPUT_FEATURE_NO_INPUT_CHANNEL`. Check after a `wm` test: `dumpsys input | grep -A2 FocusedWindows` must name the real app,
and no `Embedded{}` window with a touchable region should be listed for Duo. The 60 s limit on the test renderer stays until
this is confirmed on the phone.

## Confirmed working (touch + effect on both panels)

After the embedded-window fix, `wm` on the active screen works: touch is normal and the effect shows on both panels. `wm`
is now the persistent default (no 60 s limit, no reset on app start). The button "Active-screen renderer" cycles
wm -> free -> window and stays as an escape hatch together with `adb shell am force-stop org.duofold.live`.
Next open item: the black period during the panel switch is longer than before (see the transition analysis).

## Cleanup: `window` renderer removed

`wm` is the only layer renderer that is touch-safe and shows on both panels; `window` blocked touches and is gone. One setting
remains (`primary_mode`: wm | free, default wm) and applies to every overlay. The input-diagnostics capture and its button, the
second-panel renderer button and the 60 s test limit were removed. Safety nets stay: 45 s visibility watchdog, safe mode after
a restart with the effect on screen, `adb shell am force-stop org.duofold.live`.

## Black moment when the panels switch: what the report shows

Preview-mode trace (uptime ms): handoff requested 17669354; inner panel ON by +105 ms; "Primary physical panel changed;
rebuilding overlay" +133 ms; new overlay attached/exclusion registered +215 ms; fresh inner glass frame committed +576 ms.
So the inner panel is lit ~470 ms before its first glass frame, and until then `DuoGlass` draws the black-fade fallback
("Glass fallback inner: capture missing; amount=0.80" -> "cleared" 336 ms later). Two changes:
* `glass_fallback` setting (black = original, soft, clear) controls what is drawn while the frame is missing.
* `GlassFrames` retries after 24 ms (was 100 ms) while the overlay has not registered its exclusion surface and after
  150 ms (was 600 ms) after a missed capture (unchanged 16 ms during "urgent" fresh-capture windows).
Not done (bigger, riskier): keeping the overlay across the panel switch instead of rebuilding it, and a cheaper capture path.

---

# Update for upstream 3.5.3 (release/3.5.3)

Re-based from 3.5.2 by a word-level three-way merge plus manual re-application where 3.5.3 rewrote the same code.
Differences worth knowing:
* `HelperBinding` / `HelperAttempt` (new upstream) call `Shizuku.bindUserService` and then `unbindUserService(args, c, false)` and
  `(args, c, true)`; the root shim treats both as a plain unbind.
* New helper `CameraContinuityService` gets a root wrapper (`CameraContinuityRootService`, declared in the manifest, mapped in
  `root/Shizuku.java`). Its Bitmap reply now goes through `GlassFrames.decode` (HardwareBuffer), like the glass capture. Untested.
* `SecondaryShade` gained `blackOnly` (Plain Fade opaque inner hold); the layer-based version keeps it.
* `StandaloneService`: rebuilt on top of 3.5.3 (screenshot-startup window, incoming hold and diagnostics code are upstream's).
* `ScreenshotStartupWindow` (experimental "screenshot camera startup" mode, setting-gated, default off) still creates a
  `TYPE_APPLICATION_OVERLAY` window. It needs the SYSTEM_ALERT_WINDOW app-op that root grants, and being a real window it can block
  touches while shown. It was not ported to the layer renderer.
* `GlassFrames` retry delays: 24 ms while no exclusion surface (upstream added recovery bookkeeping there), 150 ms after a missed capture.
