## Unreleased — device-state, concurrency and idle-cost fixes (all models)

No setting, default or version changes. Every item applies to all supported models.

Display overrides (cover blanking on close)

- Releasing a concurrent display override (cover state 5 or inner state 4) while the hinge's base device state is still HALF_OPENED goes 5->2->0 (or 4->2->0), and Samsung treats 2->0 as a sleep transition: the cover blanks. Duo's 2° closed threshold fires before the base state reports CLOSED, so every release of a held override now waits for CLOSED (`CloseReleaseGate`), bounded at 3 s and lifted at once when reopening (at or above the handoff angle) or with the display off.
- Gated paths: the direct-mode release at closed, stale-angle/locked releases, the hold policy's own release, the continuity probe's finish, Fold-Only/Unfold-Only turning the effect off mid-fold, dual mode's outer (cover) session and toggling dual mode. Reader teardown waits up to 3 s for CLOSED before cancelling.
- The gate's "display off" escape uses `PowerManager.isInteractive()` alone, sent as a new trailing field of the angle poll (older clients fall back to the previous flag). Locking, a service restart or disabling Duo mid-close no longer releases with the screen on.
- A system cancel of the cover request resets the hold policy, so the cover is requested again on the next sample. A direct-mode inner hold that reaches closed without passing the closing angle (handoff angle − 4°) hands over to the gated cover hold.
- The base-state query is cached (reflection resolved once, at most one system call per 16 ms) and made only after the cheap escapes.
- The closing-mirror black mask deactivates 1.8 s after the secondary panel is gone; it stayed active for as long as the phone was closed and could start the next opening masked.

Android 17 hidden-API access

- Android 17 denies app-process reflection on `ViewRootImpl.getSurfaceControl()` (`hiddenapi: ... api=blocked ... using reflection: denied`). With the cover preview on, `PreviewTransition.markAnimation` failed for Duo's main overlay window, so that window was never excluded from the live preview mirror, and the call was retried every 500 ms for as long as the preview ran. The two screenshot-mode root lookups failed in the same way. All three now resolve the window surface through HiddenApiBypass (`WindowSurface`), which the app already ships. Verified on Android 17 / One UI 9.0: no denials, no retry loop, and every mirror attach succeeded.

Service concurrency

- Live mirror attach (which waits up to 250 ms for its commit) runs outside the angle reader's lock and on its own IPC thread in the app, so the 4 ms poll that drives the fade and the cover hold no longer stalls behind it. An attach that races a revoke or reader stop closes what it built instead of leaving the mirror attached; the poll's revoke never blocks.
- The fold-setting `settings` subprocess is bounded at 2 s, and the capture helper's close no longer waits behind an in-flight capture; a hung process used to block reader teardown and every later poll.
- After a reader restart the new rotation hold is created once the previous one has restored rotation, instead of failing and leaving about 2 s without a hold. The Shizuku destroy path waits for that restore outside the reader's lock.
- Handoff-hold requests that race the helper's shutdown free their bitmaps and skip the wait.

Idle cost

- Angle polling: 4 ms while the hinge moves (and within 2 s of motion inside the 80-115° switch band), 33 ms when still, 500 ms with the screen off. The secondary-panel refresh follows (8 ms / 100 ms). The wallpaper angle command is sent from the IPC thread instead of the main thread.
- The poll reply carries its diagnostic status strings at most every 250 ms; functional fields still go out on every poll.
- The fade engine stops per-vsync ticks while nothing fades and the hinge is at rest (50 ms re-check; any angle change wakes it), and checks the keyguard every 200 ms. The overlay and the handoff hold cache the keyguard state for 200 ms; the hold reads display info through the cached `DisplayManagerGlobal` and the poll no longer waits on it once the hold has started.
- The live shade stops its per-vsync loop once the smoothed angle settles. Glass content capture drops to 10 fps after the hinge has been still for 1 s (full rate while moving and right after a switch). Frozen frames build their mip levels off the main thread. The continuity probe's task checks run at most every 50 ms. The angle parser compiles its pattern once.

Glass frames

- Only the primary live glass resets the shared capture; frozen snapshots and secondary-display surfaces wiped the primary frame at the switch in dual and continuity-native modes.
- Each frame records its capture display and is accepted only by surfaces drawing that display; continuity-native display-1 frames were checked against display 0 and never rendered.
- A failed capture keeps the last good frame while it is still renderable (the 350 ms render age is unchanged) and retries in 32 ms, instead of dropping the glass to its fallback.

Review fixes

- The angle parser publishes its per-action tokens as one immutable snapshot; two reader threads overlapping after a restart could pair one action's name with another action's tokens and reject every sample.
- A direct-mode state change resets the release deferral, so a new cover hold never inherits an expired window from an earlier close.
- The dual-mode cover session's reopening escape requires a fresh sample, as the cover hold's already did.
- When dual mode is toggled, the incoming controller starts only after the outgoing override has actually released.
- A live-mirror attach re-checks its ticket after unlocking, so a revoke that found the lock held cannot leave the mirror attached.
- The glass source-size lookup clears its reused output first, so an absent display cannot leave stale dimensions.

Tests: `CloseReleaseGateTest`, `ReleaseDeferralTest`, `MirrorAttachGateTest`, `AngleParserTest`, `DiagnosticsCadenceTest`, `AdaptiveCaptureRateTest`, `GlassFrameRetentionTest`, and additions to `HandoffPolicyTest`, `DirectHandoffPolicyTest`, `ClosingMirrorFadePolicyTest` and `PollCadenceTest`.

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
