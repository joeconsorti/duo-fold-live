# Alpha.20 reconstruction — 2026-10-26

Latest candidate: alpha.109 extends the existing jitter-protection toggle to the fully open endpoint. A three-degree start/reset separation blocks near-flat 170–172° readings from starting sessions at the default 172° open endpoint. It retains alpha.108's frosted-preview dimming. Alpha.107 remains the user-approved stable rollback reference. See `docs/3.5.9-alpha.109-reconstructed-CHANGELOG.md`.

Recovery target: Duo Fold Live 3.5.9-alpha.20-local (1273), test package org.duofold.live.test.
Output: 3.5.9-alpha.21-reconstructed-local (1274). This is a source reconstruction plus requested diagnostics, not recovered original source or a byte-identical alpha.20.

The surviving alpha.14 source (355e652) was compared with readable Java and bytecode recovered from the saved alpha.20 APK, and the user's alpha.20 session report. Missing historical source commits were not invented. Kotlin state and rendering code were manually restored; Java policy changes were restored against original source where available. The original alpha.20 APK remains the behavioral reference.

Restored behavior includes source selection/capture overlapping secondary wake, asynchronous brightness preparation, native destination capture before shielding, 75 ms geometry stability, per-session/revision/physical-panel capture acknowledgement, post-remap exact-app readiness checks, and endpoint cleanup. The independent screenshot shield route remains; no live surface mirrors were added. SurfaceView buffer commits, reduced raster sizes, idle heartbeat, capture exclusions, screenshot-mode suppression of duplicate renderers/sensors, wallpaper geometry caching and reattachment backoff were restored.

New diagnostics:
- Wallpaper A/B/A capture samples compare the same physical display with and without our custom photo leaves excluded. Samples run around incoming capture, readiness, and cleanup; only one probe runs at a time. Central 80% is compared, pixels are not saved, and changed geometry, moving scenes or failed capture are inconclusive. Contribution can be legitimate app transparency; this is not proof of incorrect Z order or optical panel visibility. Sampling can miss brief flashes and cannot retroactively remove wallpaper baked into a retained screenshot. Extra capture/window queries can perturb timing; per-sample duration and skips are reported.
- Per-fold thermal status and battery temperature samples, sampled peak, duration, and app CPU time. All new health rows identify the build. CPU excludes the shell helper, compositor and GPU. No current handset measurements were available during reconstruction.

Supplied alpha.20 report: startup PID 25860 at elapsed 213367425 reported battery 29.4 C and thermal status 0. Older PID histories are not evidence of this build's sustained thermals. Repeated historical SurfaceFlinger hierarchy-cycle crashes cannot be attributed to this reconstructed build.

Build: JDK 17, Android SDK 36/build-tools 35, Gradle 8.13. Run `python tools/check_live_b8.py`, then commit and run `python tools/build_local_test.py` with ANDROID_HOME and optionally GRADLE_BIN. The build script runs unit tests and emits APK, complete source ZIP, Git bundle and commit/hash manifest. Back up the source ZIP and APK outside the workspace before delivery. Debug signing keys must remain private and be retained separately for compatible updates.

Validation scope: host unit and fake-platform lifecycle tests plus APK assembly/signature verification. No attached handset was available. Install over the existing test app only with matching signing certificate; do not uninstall merely to bypass a mismatch, because that loses saved settings.


## Alpha.22: wallpaper overlap follow-up

User confirmed alpha.21 reproduces alpha.20 motion but intermittent wallpaper covers incoming content. Exact retained app captures now exclude all registered custom photo leaves/window roots as well as animation surfaces; unavailable active wallpaper exclusions fail the capture for retry rather than silently saving a potentially contaminated image. This addresses baked-in wallpaper but does not claim to fix live layer ordering. Capture requests log photo/overlay exclusion counts. Shell exact-capture limit is 64, matching diagnostic capture; secure capture policy is unchanged.

Delayed AFTER_READY probes no longer depend on the transient primaryCheck ID. They record scheduling and supersession, then independently inspect visible composition and the underlay with animation surfaces excluded. Busy/unavailable probes remain explicit non-passes. No optical visibility claim; probes remain central-80% temporal samples and may miss brief flashes or see legitimate transparency. B8 cleanup now resets the host active flag/strength for the screenshot route. No fold-wake behavior changes.

Validation: run full unit/build and B8 fake-controller checks before delivery. Handset overlap still requires the next user fold test.


## Reconstructed alpha.23 — Home wake and incoming placeholder

User confirmed alpha.22 fixed the captured wallpaper overlap, then reported frequent
lock/unlock wallpaper flashes and an incoming app that stayed mostly black.

- Remove the deliberate black incoming placeholder. Render the verified outgoing
  app screenshot with the existing aspect-preserving composite until the exact
  destination capture arrives. Native capture acknowledgement still requires the
  destination physical identity, revision and committed destination frame.
- Preserve recent Home evidence across empty accessibility teardown samples and
  carry an interrupted eligible Home wake into the next sleep. A positively sampled
  other app clears eligibility; empty samples do not certify Home readiness.
- Begin bounded wallpaper bridge preparation on SCREEN_OFF, including up to two
  seconds while noninteractive/locked. Screen events reset attachment retry backoff
  and canceled old rebind requests. Listen for active/focused window changes too.
- Preserve the existing full one-second unlock hold and bounded verified Home handoff.
- Report source-placeholder/native-panel commits and 32x32 dark-pixel samples of
  exact captures. Legitimate dark app content is not rejected by this diagnostic.

No handset is attached. Lock/unlock ordering, first-frame visibility, and incoming
native captures still require visual testing. Source placeholders retain aspect
ratio and may have black letterbox margins. No optical success is claimed.

## Alpha.24 — bounded incoming screenshot settling

Alpha.23 handset report session 222389496 captured 1024/1024 near-black samples
and committed that same native-panel snapshot. HAS_DRAWN did not establish useful
capture content. The underlying reason for the black display capture remains
unconfirmed (timing/occlusion cannot be distinguished by that report).

Destination captures now retain the existing outgoing presentation and withhold
capturedRevision while every sampled pixel is near-black or sampling fails.
Retry after 150 ms, bounded to five returned candidates or 1200 ms from the
capture route/revision starting. A candidate with non-dark samples proceeds
immediately. At the bound, accept the latest candidate with the explicit
ACCEPT_BOUNDED_FALLBACK diagnostic: legitimate black app content must not block
folds indefinitely. This fallback can still look black if the underlying capture
problem persists; it is not a content-readiness pass. Null/failed captures retain
the existing controller timeout behavior. Stale route/revision/rotation results
are discarded; unpublished rejected bitmap/mip resources are recycled.

The outgoing image is unchanged during retries; native capture acknowledgement
still requires a matching committed native frame. Wallpaper exclusion and wake
preparation behavior are preserved. Tests cover black-to-content recovery,
attempt/time bounds, missing measurements, and revision budget reset. Handset
visual verification is still required; no connected device was available.

## Alpha.25 — accelerate first retry and remap readiness recovery

Alpha.24's last incoming capture required two candidates; observed primary
readiness also retried after a mapping change. Retry the first dark candidate
after 50 ms instead of 150 ms, keeping subsequent delays at 150 ms. The
five-candidate fallback now additionally requires 600 ms of route time (the
previous four 150 ms waits alone required at least this long). The 1200 ms
time bound remains; valid content still proceeds immediately. Tests exercise
rapid dark candidates so acceleration cannot prematurely exhaust protection.

Failed primary readiness checks retry after 50 ms, while successful checks
retain 150 ms spacing. One request remains in flight, and physical identity,
rotation, geometry, HAS_DRAWN and committed native-frame checks are unchanged.
The retained incoming decision adds route, capture and session elapsed times.
No continuous captures, wake changes or live mirrors are introduced.

Expected savings are conditional on a retry: up to roughly 100 ms of scheduled
waiting per accelerated retry, not a measured handset improvement. No device
is connected. Geometry settling and endpoint shields remain unchanged.

## Alpha.26 — missing captures and remap check scheduling

The alpha.25 ChatGPT report showed a failed/no-image incoming request followed
by a 165 ms wait, and repeated geometry verification while the primary mapping
settled. The first failed/mismatched incoming capture now retries after 50 ms;
subsequent failures retain 150 ms spacing. Empty results do not consume the
dark-candidate budget and never authorize reveal.

A changed primary-check token clears the previous retry deadline and readiness
acknowledgement. An old in-flight check cannot install a cooldown or acknowledgement
for its replacement. Only one readiness request remains in flight. The controller
requests focus once per transfer, rather than repeating that mutation every time
geometry is reverified. Actual mapping changes still invalidate readiness and
require the full 75 ms settling interval and a fresh matching HAS_DRAWN result.

Regression checks exercise mapping loss/recovery, rejected old acknowledgements,
and bounded empty-result retries. No optical timing improvement is claimed:
no handset is connected. Rotation release, power, wallpaper exclusion, frame
commit gates and screenshot shielding remain unchanged.

## Alpha.27 reconstructed — content guards and cancellation coverage

- Version 1280 / 3.5.9-alpha.27-reconstructed-local, based on preserved alpha.26 source.
- Selected-task capture/readiness rejects other on-screen task windows on the same logical display; checks before and after capture reuse existing WindowManager dumps. Reports selected task/display and competing task rejection. Explicitly describes display capture, not isolated app pixels; no raw screenshots saved.
- Suppress legacy primary rendering from preparation, rather than after both coordinated windows attach; keep legacy host hidden when screenshot session begins.
- Android cancellation with an existing shield retains coverage for at most 500 ms. Restore selected task once, observe native state/physical geometry, require a new readiness token and matching presentation commit before early release. No concurrent power requests or renewal during cancellation recovery. Timeout is explicitly unconfirmed; lock, disable, stale input and thermal stop still clean up. No new mirror/reparent operations.
- Validation: 103 deterministic controller checks pass, including stale pre-cancel acknowledgements, readiness release, timeout, lock and no power reassertion. Bridge cleanup and brightness harness pass. Added five window guard unit tests; full unit suite runs in delivery build.
- Limits: competing-window checks are conservative software evidence, not pixel provenance. System surfaces or wrong compositor frames may evade checks. Coverage cannot prevent a physical panel OFF interval; the 500 ms fallback can release before readiness. No handset/optical validation available.

## Alpha.79 rotation and blackout candidate

Based on alpha.78 b6d22ae. See docs/3.5.9-alpha.79-reconstructed-CHANGELOG.md for
report timestamps, changed release order, manual destination rotation restoration,
owner-only journal caching, nonblocking fence reuse/retirement and bounded default
metadata/opt-in pixel and policy diagnostics. Keep original posture restoration
API and crash journal; do not claim software lock readback proves orientation
response or an optical blackout cure. Commit before delivery build and preserve
both APK and complete source/history archive with their hash manifest.

## Alpha.102 — brightness timeout on Duo close

The current alpha.101 app closing sessions 719151 and 737682 abort on restored
brightness log-read timeouts. Cancellation of concurrent display ownership
then switches the cover OFF before full close. Replace repeated 30,000-record
reads with complete 512-record snapshots; cancel the child during cleanup and
isolate retry output from late child writes. Keep post-clear physical/SDR
validation and all bounded native-release behavior. Separate Live primary
surface FPS from embedded Duo/secondary painter calls, label age/windows, and
fix interval rollover. See docs/3.5.9-alpha.102-reconstructed-CHANGELOG.md for
the observed timing, FPS comparison, changes and validation limits.


## Alpha.103 — 120 Hz request and angle-selected software recovery

Alpha.102's 512-record dumps still time out. Current closing session 2738263
fails preparation at 2740317 (~42 degrees), releases to Samsung OPENED policy,
verifies the inner screen and powers the cover OFF at 2740669. At 2740732 the
hinge is 35 degrees; cover remains OFF until actual closure. This is a software
fallback before the normal stationary gesture, not a new full-close sleep.

Replace dump-exit-dependent brightness reads with one preparation-scoped stream
of complete fresh physical commands. Keep post-clear SDR validation, one-time
probe restoration and bounded writer/child cleanup. For app software failures,
reuse the bounded pause unwind and native readiness gates while retaining the
below-90 cover / above-90 inner panel rule through the leg. Actual Android
cancellation, lock, stale input and endpoint still release ownership. Remove
Duo's 17 ms limiter and 60 Hz vote; use vsync scheduling and seamless votes up to
120 Hz at the current panel resolution, as Live does. See the alpha.103
changelog. The handset and optical behavior remain unverified here.


## Alpha.107 — native app placement in Duo

Both alpha.106 incidents kill system_server at BLASTSyncEngine.setSyncMethod,
shortly after an explicit app-root move followed by the primary display request.
Live Screen has no such app-root move. Direct Duo now leaves the app on logical
D0 and observes Android placement through remap, readiness, pause and cleanup.
Keep the brightness hold and screenshot pipeline. Preserve the non-direct Lab/
legacy task route. See docs/3.5.9-alpha.107-reconstructed-CHANGELOG.md for exact
incident timing, regression checks and the requirement for handset validation.
