## Unreleased — Galaxy Z Fold 7 (SM-F966) on Android 16 and Android 17

Adds support for the Galaxy Z Fold 7 family on Android 16 / One UI 8.5 and Android 17 / One UI 9.0, and the fixes
found while tuning it on an SM-F966U1 against frame-by-frame recordings of both panels. Fold 7 values are gated by
`DeviceCompatibility.isFold7`; every other model keeps the current values unless marked **all models**. Full value
list: `docs/fold7/tuning-reference.md`; audit findings and rationale: `docs/fold7/remediation-plan-2026-10-04.md`.

Compatibility
- Gate both inner-primary and cover-primary session releases on base device state, matching the generic fixes in PR #17; fresh reopening, screen-off, and bounded-timeout escapes remain available.
- Accept Android 16 (SDK 36) on the SM-F966 family only; Android 17 stays required elsewhere. The Android 16
  framework on SM-F966U1 contains every hidden class the app reflects into, and FoldInteractive answers angle
  commands there.
- Verified on the same SM-F966U1 after its update to Android 17 / One UI 9.0 (`CP2A.260605.016`): every hidden
  framework member the app reflects into resolves with the expected signature, FoldInteractive still logs
  `mCurrentAngle`, and open/close cycles run the full handoff (angles, captures on both panels, fade, close-release
  gate, live mirror). The one Android 17 denial found is fixed above.

Panel switch (Fold 7)
- Full black where the panels actually switch: closing at the hold angle, opening 3 deg after the handoff angle
  (94 / 101 deg at a 98 deg handoff, as measured; they follow the handoff setting), reveal only on a real glass frame of
  the new panel (opening cap ON+600 ms, closing ON+380 ms), 100 ms reveal, per-frame re-checks during a switch,
  700 ms fresh-content window (the first post-switch capture takes up to ~400 ms there).
- Unified renderer (`debug.duofold.unified`, default on for the Fold 7): before the switch the inner panel's left half
  is drawn by the same perspective glass shader as after it (mirrored cover), with the pose eased so progress, blur and
  darkening are equal at the switch; the right half is a flat live mirror of the cover filling exactly the right half,
  with a hinge-driven compositor blur that continues after the switch (`RightHalfBlur`) on the left glass's curve.
- Glass frames survive a failed capture for up to 1.5 s instead of dropping to the empty fallback.

Display overrides (all models)
- `CloseReleaseGate`: a concurrent display override (state 5 / 4) is released only once the base device state is
  CLOSED (or on reopening, screen off, or after 3 s). Releasing it while the hinge still reports HALF_OPENED goes
  5->2->0, which Samsung treats as a sleep transition and blanks the cover. Every release path is gated: the cover
  hold, Fold-Only/Unfold-Only mode changes, dual mode, reader teardown, the continuity probe, and a system cancel
  (which now also allows a re-request).

Android 17 hidden-API access (all models)
- Android 17 denies app-process reflection on `ViewRootImpl.getSurfaceControl()` (`api=blocked ... using reflection:
  denied`). With the cover preview on, Duo's main overlay window was never excluded from the live preview mirror and
  the lookup was retried every 500 ms; the two screenshot-mode lookups failed the same way. All three now go through
  HiddenApiBypass (`WindowSurface`). From PR #17.

Performance and robustness (all models)
- Angle polling 4 ms while moving (or briefly in the 80-115 deg band), 33 ms at rest; glass content capture at the
  configured rate while moving and 10 fps at rest; settled shade loops stop requesting frames; keyguard and display
  queries cached off the per-frame paths. Measured at rest on SM-F966U1 (app + service, % of one core): Flex
  ~43 -> ~20, closed ~13 -> ~7.
- Service races fixed: live-mirror attach vs revoke/stop (generation ticket, non-blocking revoke, attach outside the
  reader monitor and on its own IPC thread), bounded `settings` subprocesses, rotation-hold hand-over after a restart,
  Shizuku destroy waiting outside the monitor, blur ticker reset on its own thread.

Review fixes (PR #18 review)
- Only the Fold 7 unified path gets the new blur: the preview's progressive blur and the hold blur exist only for the
  unified half-pane layout, the post-switch right-half blur also requires `UnifiedRenderer.enabled()`, and the hold
  uses the half-pane layout only when both panels are upright. Fold 8 and non-unified sessions keep their visuals.
- The 600 ms opening cap applies only when inner glass is required; the no-glass path keeps the short draw cap.
- Hold readiness uses the per-model fresh window, so the hold drops on the frame that starts the reveal.
- `AngleParser` token cache published as one immutable snapshot; `ConcurrentController` reopening escape requires a
  fresh sample; a direct-mode state change resets the release deferral; dual-mode toggles start the incoming
  controller only after the outgoing override released; a live-mirror attach re-checks its ticket after unlocking;
  `DuoGlass.sourceSize` clears the reused size first.

Diagnostics
- `fold7test` build type (release-optimized, debug-signed, diagnostic logs) and `BuildConfig.DIAGNOSTICS` for the
  `Duo*` log tags; `debug.duofold.record_visible`, `preview_blur_*`, `right_blur_clear`, `reflect_max_hinge`,
  `max_darken` debug properties (documented in the tuning reference).

# 3.5.4 — Cover-close continuity and motion presets

- Promote the user-tested alpha.7 normal-mode closure behavior: let Android release the cover request at full closure, with the same 1000 ms fallback and cancellation safeguards. User testing on SM-F971U reported that the full-close blackout disappeared. The separate mid-fold display-switch blackout remains.
- Add Smooth (120 ms) and Fast (30 ms) beneath Motion smoothness, using the Animation Behavior selection style. Each preset immediately sets the slider. Manual slider values remain available; Reset smoothness selects Smooth.
- Smooth is the new default, applied once on install/upgrade. Later preset and manual choices persist.
- Remove the obsolete closing cover power-pulse experiment, its toggle, and its saved setting.
- Include the user-confirmed landscape preview fix: clean and reflected cover previews follow the physical hinge and fit their display orientation.
- Retain bounded fold-only wake recovery and closure diagnostics. No change to the tested closure timeout or rendering geometry during stable promotion.
- Known limits: tested on SM-F971U / Android 17. Screenshot mode remains experimental; custom-wallpaper unlock flashing and the mid-fold display switch remain separate issues.

## 1.6.0 — Tested performance release

Promotes 1.6.0-alpha.4 behavior unchanged. Only stable version metadata and release documentation differ.

- 1 ms minimum wait between completed interactive angle polls, without overlapping requests.
- Direct vsync glass rendering, half-resolution by default, optional full resolution.
- Motion smoothness slider: 12–120 ms, default 12 ms.
- Existing handoff angles, screenshot mode, wallpapers, and settings preserved.
- User testing on SM-F971U reported smooth behavior and last-active samples of 55.7 cover / 108.1 inner submitted FPS. These are not guaranteed panel FPS or controlled benchmarks; angle age is not end-to-end latency.
- Screenshot removal and unlock wallpaper flash remain separate future work.

## 1.5.4 — Regional Fold 8 setup

- Enable the SM-F971 regional family on Android 17, including unlocked U1 and Canadian W, in wallpaper setup and both display controllers.
- Keep Samsung component/API checks, named concurrent-display-state discovery, and wallpaper configuration readback. Regional firmware compatibility remains experimental.
- Report model/OS eligibility separately from Shizuku authorization and verified wallpaper configuration.
- Refresh helper process versions after upgrade. No animation, capture, timing, or settings migration changes.
- SM-F966 / Fold 7 on Android 16 remains outside this wallpaper profile.

# Changelog

## v1.5.1 — privacy-clean public release (versionCode 24)

- Neutral application ID, code namespaces, component authorities, diagnostics, and signing certificate.
- Duo Fold Live branding throughout app interfaces and internal app identifiers.
- Installs separately from older private builds; old app data is not overwritten or migrated.
- Retains guided first-install setup, both animations, custom photo wallpaper, and mandatory Samsung hinge wallpaper configuration.
- Upstream license and attribution notices remain intact.

The rendering/handoff pipeline is unchanged. The custom-wallpaper unlock flash remains unresolved. Setup and the renamed helper components require handset verification.
