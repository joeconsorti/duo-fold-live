# Fold 7 remediation plan (2026-10-04)

Branch `feat/android16-fold7`, baseline `3.5.2-a16.7` (versionCode 1078). Source: four parallel read-only
audits of the working tree (device-state handoff, service-process concurrency, app-process rendering,
cross-cutting consistency) plus frame-by-frame captures of both panels on an SM-F966U1 / One UI 8.5.

Status key: **V** = verified by reading the code path end to end after the audit; **R** = reported with a
traced sequence, still to be confirmed (by test or on device) before the fix lands. Every fix lands
test-first where the logic is pure, behind `DeviceCompatibility.isFold7` unless marked "all models".

Device facts the plan depends on: device states CLOSED 0, TENT 1, HALF_OPENED 2, OPENED 3,
CONCURRENT_INNER 4, CONCURRENT_OUTER 5. Releasing a concurrent override while the base state is still
HALF_OPENED goes 5->2->0 (or 4->2->0), and Samsung treats 2->0 as `sleepDevice=true`: the cover blanks.
Effective angle maps <= 2 deg to 0, which fires 9-103 ms before the base state reports CLOSED.

## Progress

| Phase | Version | Status | Notes |
| --- | --- | --- | --- |
| Tooling | — | Done | Lint baseline + warnings-as-errors, `.editorconfig`, markdownlint, ruff (commit `chore(tooling)`). |
| A | 3.5.2-a16.8 | Done | A1-A8 implemented test-first; C3 (cached, rate-limited base-state query) landed with it. A3: the client still unbinds with `remove=true`; the service-side bounded teardown wait covers the destroy it triggers. |
| B | 3.5.2-a16.9 | Done | B1-B10 and F1. B2 uses the pose blend (progress, motion, blur and darkening equal at the switch) rather than switching the strip to the inner leaf model, which needs a texture remap that cannot be validated off-device. B3 also aligns the post-switch hold. |
| C | 3.5.2-a16.10 | Done | C1-C6 (C3 in a16.8). C5: mip pyramid moved off the main thread; per-frame SharedPreferences reads measured as negligible and left as is. C6: rate-limited to 50 ms instead of a separate thread (same effect on the poll, no new threading). D5 and D8 landed here. |
| D | 3.5.2-a16.11 | Done | D1-D4, D6, D7 (D5, D8 in a16.10). D1 via a generation ticket + `ReentrantLock.tryLock` revoke. D4 copies handles with the framework's (hidden) copy constructor, falling back to the originals if it is missing. D6 hands over after the old hold releases rather than reusing the instance. |
| E | 3.5.2-a16.12 | Done | E1, E2, E6 with the plan; E3 decided: gate, cadence and race fixes stay on all models (labelled in the CHANGELOG); E4 `HandoffPolicy.HOLD_ANGLE`/`RELEASE_ANGLE`; E5 endpoint uses `freshWindowFor`; E7/E9 in external capture tooling (sheets and docs); E8 tests plus `ReleaseDeferral` extraction. |
| F | 3.5.2-a16.13 | Done | F1 (a16.9) and F7 (B10). F2: closing cap 300 -> 380 ms from the capture timings. F3: decided against a zero-copy path (captures take 58-146 ms; the live frame feeds software consumers). F4: already guaranteed by the inner/cover identity; pinned by a test proven to fail without the check. F5: diagnostics cadence 250 ms. F6: `summary.txt` in the external sheet tool. |

**Status: every item in phases A-F is done or decided (2026-10-05); on-device verification against the pass criteria
below is next.**

## Results that motivated this plan

| Capture | Sleep-path closes | Gate defers | Visible empty glass frames | Median black (log) |
| --- | --- | --- | --- | --- |
| a16.6 run | 1 of 8 (via ungated `DirectHandoffPolicy` RELEASE) | 0 | ~21 after reveal | ~300 ms open / ~350 ms close |
| a16.7 run | 0 of 10 | 1 (correct case) | 0 (36 at endpoints, 0.4-5 % effect) | ~370 ms open / ~400 ms close |

Visual review of the a16.7 run found the right half drawn in perspective mid-fold (52-69 deg). Cause:
`debug.duofold.record_visible=1` un-skipped Duo's main glass surface, and skip-screenshot is also what keeps
that surface out of the live cover->inner mirror, so captures changed what the user saw. It also found a
56 px -> 0 px blur jump on the inner right half at the switch (fixed in a16.7 by `RightHalfBlur`).

## Phase A — device-state sleep paths (highest priority)

Every release of a concurrent override must pass `CloseReleaseGate` unless the screen is genuinely off.

| ID | Sev | St | Finding | Fix | Test |
| --- | --- | --- | --- | --- | --- |
| A1 | High | V | Fold-Only mode: `AnimationModePolicy.java:16` sets `direction=1` at angle <= 2, so `effectAllowed` turns false on every close and `AngleReader` calls the ungated `handoff.release()` (5->2->0). A +2 deg hesitation mid-close does the same (release + re-request flicker); Unfold-Only mirrors it on a -2 deg wobble. | Route `!effectAllowed` through a gated `CoverHandoff.releaseGated(...)` while the cover override is held; keep the closing direction at the closed endpoint until the angle rises above 2. | `AnimationModePolicyTest`: 179 -> 60 -> 0 in fold_only stays allowed; pure router `forEffectDisallowed(coverHeld)==GATED`. |
| A2 | Med-High | V | The gate's `!interactive` escape receives "service up && enabled && screen on && unlocked" (`LiveAngles.java:134`), so a keyguard lock, service restart or toggle mid-close releases at base HALF_OPENED with the screen on. | Add a separate `screenOn` (PowerManager only) to the code-2 parcel; pass it to `CloseReleaseGate.allow`. Keep "unlocked" for request decisions only. | Overload `allow(baseClosed=false, screenOn=true, unlocked=false, ...)` == false. |
| A3 | Medium | R | Teardown cancels ungated: `AngleReader.stop()` from the 2.5 s lease timer, Shizuku destroy (16777115), code-1 restart; client `fail()` unbinds with `remove=true` (process death = system cancel). `ensureAnchors()` `addView` is not in try/catch exactly when display 0 changes panels. | Bounded wait (<= `MAX_DEFER_MS`, polling `baseClosed()`) before cancelling a held cover override in `stop()`; try/catch the primary `addView`; unbind with `remove=false` unless the user stops. | Pure `TeardownPolicy.waitBeforeCancel(coverHeld, baseClosed, elapsed)`. |
| A4 | Medium | V | System `onRequestCanceled` clears `owned` but not `policy.cover`/`deferSince` (`CoverHandoff.java:66`), so 5 is never re-requested for the rest of that close -> 2->0 sleep. | On `owned==next`: `policy.reset(); deferSince=0;` (move into `HandoffPolicy.onCanceled()`). | `update(45)==1; onCanceled(); update(44)==1`. |
| A5 | Medium | R | Direct mode: inner-concurrent (4) RELEASE at angle <= 0 bypasses the gate by design (`CloseReleaseGate.gates(RELEASE,true)==false`); a 96 -> 0 jump across the post-switch sample gap cancels 4 at base 2. | `DirectHandoffPolicy`: innerHeld && angle <= 0 && fresh && interactive -> COVER; the next RELEASE is gated. | `next(true,0,true,true,true,172)==COVER` (update `closedThresholdIsInclusive...`). |
| A6 | Medium | R | Dual mode `ConcurrentController` releases the outer (5) session 350 ms after an endpoint <= 1 deg, on `!fresh` (2 s) and on `!unlocked`, all without the base-state gate. | Route outer-held `releaseInternal()` through `CloseReleaseGate.allow(baseClosed(), screenOn, angle>=98, deferred)`; share `baseClosed()`. | Pure `DualReleasePolicy.release(endpointMs=400, baseClosed=false, outer=true)==false`. |
| A7 | Med-Low | R | Continuity probe FINISH (`CoverHandoff.java:35`) releases ungated at angle <= 0 / `!fresh`. | `if(gatedRelease(...))return; releaseOwned();`. | Drive `ContinuityProbePolicy` to FINISH at 0; gated helper defers. |
| A8 | Low-Med | V | `ClosingMirrorFadePolicy` returns 1 forever while the secondary panel is absent (`:13`), so the fade engine never idles while closed (per-vsync ticks) and the next opening starts black-masked. | Deactivate when the secondary is absent, or after 2x `READY_TIMEOUT_MS` with the panel off since cutoff. | `opacity(5000,false,false,-1,0)==0` after cutoff. |

## Phase B — visual continuity: perspective, sizing, blur

Target behaviour: perspective only on the left half; the right half is always a flat full pane whose blur
follows the hinge; no jump in perspective, size, blur or darkening at either switch.

| ID | Sev | St | Finding | Fix |
| --- | --- | --- | --- | --- |
| B1 | High | V | Capture mode alters the user's view: code 7 surface is Duo's main glass (display-0 FrostSurface). a16.7 now always skips it, which keeps it out of the mirror but also out of every capture, so post-switch inner screenshots no longer show the left glass. | Keep the surface in `AngleReader`; per poll set skip = "record visibility off, or the mirror is live" (skip while the cover->inner mirror is live, record otherwise); release on replace/stop. Document in `RecordVisible`, CHANGELOG, capture script. |
| B2 | High | R | Left-strip reflection freezes from 55 deg to 101 deg (`reflectMaxHinge`, cover leaf model, `motion` 0.664), then jumps at the reveal to the inner model (`motion` 0.951; outer-edge blur ~14 -> ~26 px; darkening steps). `tuneInner` swaps only stretch/compression, not the leaf geometry. | Unified mode: draw the strip with the inner model and the same `progress(angle,inner,openThreshold)`; blend leaf angle and motion from the capped pose to the inner pose with `smoothstep(55, blurSwitch, angle)` so values are equal at the switch. Needs B3. |
| B3 | High | R | Pane boundary moves at the switch: before, mirror + blur crop at x 1032..1968 with a hard strip edge; after, `RightHalfBlur` crop 984..1968 and the shader's 0.5 + 0.07 seam feather to ~1122. | Unified mode: right pane = exact right half (`left = maxWidth/2`), mirror fills the right half (scale 984/1080, crop 56 px top/bottom), blur crop w/2..w; render the reflected FrostSurface full width so the shader's own seam discard/feather is identical before and after. |
| B4 | Low-Med | R | Reflection drawn into the 1032x2184 strip non-uniformly (0.956 x 0.867); features ~10 % wider than in the mirror. | Uniform `height/2520` scale via `LiveMirrorLayout`. |
| B5 | Low-Med | V | Post-switch blur rates differ: left shader `smoothstep((thr-a)/(thr-90))`, `RightHalfBlur` `smoothstep((thr-a)/(thr-101))`. | Share one progress function; rescale so the right half is exactly max at the switch angle. |
| B6 | Medium | R | Fallback gradient ignores the unified darkening cap (reaches solid black vs 0.55), flashing near-black when a frame is missing. | `min(alpha, UnifiedTuning.maxDarken())` when unified; hoist the gradient allocation. |
| B7 | Medium | R | `requestFreshCapture()` (global `frame=null`) is called for frozen/secondary FrostSurfaces too, wiping the primary glass at the switch in dual/native modes. | Only for `!reflectedCover && !frozen` surfaces on display 0. |
| B8 | Medium | R | continuityNative mode compares display-1 frames against display 0 size, so glass never renders there. | Compare against the view's own display; store the capture display in `GlassFrame`. |
| B9 | Low | R | Strip uses the physical cover aspect; mirror uses logical sizes — wrong in landscape. | Derive from the same logical fit, or hide the strip unless both panels are at ROTATION_0. |
| B10 | Medium | V | Blur tuning read inconsistently: `preview_blur_max` read per attach (mirror), per prepare (expansion), once per session (right half); right half hard-codes the 40 ms glide instead of `preview_blur_smooth_ms`. | One cached `PreviewBlurPolicy.tuning()` (1 s, like `UnifiedTuning`) used by all three. |

## Phase C — hot paths

| ID | Sev | St | Finding | Fix |
| --- | --- | --- | --- | --- |
| C1 | High | V | `PollCadence` stays at 4 ms indefinitely in the 80-115 deg band (Flex/laptop posture) and re-runs `refreshSecondary` at 125 Hz with keyguard/display binder calls on the main thread. | Band fast only within ~2 s of the last angle change; `displays.getDisplay(1)`; one keyguard read per refresh. |
| C2 | Med-High | R | `DuoLiveShade` (`LiveVeil.kt`) runs `withFrameNanos` forever while fresh and recomposes every motion frame, each doing settings reads and an `isKeyguardLocked` binder call via `onFrame -> usable()`. | Suspend until the next angle when settled; `remember` settings; drop `usable()` from `onFrame`. |
| C3 | Medium | R | `CoverHandoff.baseClosed()` (device_state binder + reflection) runs on every 4 ms poll during the defer, under the AngleReader and CoverHandoff locks. | Short-circuit `reopening`/`!screenOn`/cap first; cache methods; rate-limit to 16-33 ms. |
| C4 | Med-Low | R | `PreviewExpansion.holdBeforeRelease` waits up to 24 ms in code 2 behind a handler doing 3 uncached system_server calls every 8 ms. | Throttle keyguard (200 ms), use cached `DisplayManagerGlobal`, skip the post once the hold started. |
| C5 | Low | R | `DuoGlass.drawFrame` ~14 settings reads + display query + allocation per frame; frozen frames build mip levels on the main thread. | Cache settings per surface (listener), reuse `Point`, build mips off-thread. |
| C6 | Low | R | Continuity probe calls the activity task manager every poll inside code 2. | Run router work on its own thread; code 2 reads cached results. |

## Phase D — service-process concurrency

| ID | Sev | St | Finding | Fix |
| --- | --- | --- | --- | --- |
| D1 | Medium | R | `InnerLiveMirror` attach races `previewAllowed`/`stop()` (mirror can stay attached until the next start) and `revoke()`/`stop()` can still wait out a 250 ms attach. | Generation/supplier re-checked after commit; set `attaching` before calling; non-blocking revoke (`tryLock`) with a pending-revoke flag. |
| D2 | Medium | R | `GlassCapture.close()` synchronized; `command()` spawns `settings` with no timeout — a hung process wedges `stop()` and with it every later poll. | Volatile `closed`, unsynchronized close; `waitFor(2 s)` + `destroyForcibly`. |
| D3 | Low | R | Blur ticker can run two frame loops; reset fields written from the attach thread. | Do the whole reset in one runnable on the ticker. |
| D4 | Low | R | `RecordVisible.exclusions()` hands GlassCapture layers other threads may release (record mode only). | Copy under the lock; release copies after capture. |
| D5 | Low | R | `PreviewExpansion` posts to a quit handler after `close()`: bitmaps never recycled, 24 ms wait wasted. | Check `post()` result; recycle and count down on false. |
| D6 | Low | R | `FoldRotationHold` restart gap (~2 s without a rotation hold; old threads retry). | Hand over the old instance or create after `awaitRelease()` off-thread. |
| D7 | Low | R | Code 16777115 holds the AngleReader lock up to 8 s. | Wait and exit outside the monitor. |
| D8 | Low | R | `HandoffFade.idle` not volatile; a wake can be missed for up to 50 ms. | Post `start` whenever the angle moved (`wakePending` dedups). |

## Phase E — consistency, tests, docs, tooling

| ID | Sev | St | Finding | Fix |
| --- | --- | --- | --- | --- |
| E1 | High | V | External capture script: no `trap`, so an aborted run leaves `record_visible=1` on the phone; a missing mp4 aborts before the warning. | `trap` reset on EXIT; tolerate a missing pull. (Capture tooling, outside this repository.) |
| E2 | Medium | V | CHANGELOG/README/plan behind the code (precapture bullet, closing cap, a16.6/a16.7 missing). | Done in this change. |
| E3 | Medium | V | Fork changes reaching every model without an `isFold7` gate or "all models" label: CloseReleaseGate, cover readiness re-report and closed-cover endpoint, 33 ms still cadence, code-7 skip. | Label in CHANGELOG now (done); decide per item whether to gate. |
| E4 | Med-Low | V | Switch-angle literals (98/94/101, 80-115 band) duplicated; `CoverHandoff` `reopening>=98` must equal `HandoffPolicy`'s release angle. | Expose `HandoffPolicy.RELEASE_ANGLE`/`HOLD_ANGLE`; reference everywhere. |
| E5 | Low-Med | V | Inner fully-open endpoint uses the 350 ms freshness (`GlassFramePolicy.usable` default) while readiness accepts 700 ms on the Fold 7. | Pass `freshWindowFor(MODEL)` or document the deliberate difference. |
| E6 | Low | V | Stale comments ("ON+80 ms", "Debug builds only", "Requires a debug build"). | Done in this change. |
| E7 | Low | V | `transition_sheets.py` ignores DuoState/DuoBlur/DuoFallback/DuoMirror and the `=` angle form. | Add DuoState (defers) and DuoFallback to the sheets; accept both forms. |
| E8 | Medium | V | Untested behaviour: `AngleParser` rewrite (hot path), PreviewExpansion no-readiness 120 ms fade, non-Fold7 `closingReadyTimeoutFor` default, CoverHandoff gating orchestration. | Add tests; extract the orchestration into a pure step. |
| E9 | Med-Low | V | The capture tooling README omitted the capture/sheet/audit scripts and calls all but one read-only. | Capture tooling docs, outside this repository. |

## Phase F — found in captures and earlier audit rounds (not in the four audit reports)

| ID | Sev | St | Finding | Fix |
| --- | --- | --- | --- | --- |
| F1 | Low | V | Endpoint fallback draws: every transition logs `capture missing`/`cleared` at the endpoints (cover 3-10 deg, inner 160-170 deg, effect 0.4-5 %), and one inner screenshot at 6 deg opening showed the left strip black for a frame. | Skip the fallback gradient below a visible effect amount (~0.06) instead of drawing it; for the reflected strip, keep the last frame instead of black. |
| F2 | Low-Med | V | 2-3 of 10 closes still reveal on the ON+300 ms cap ("destination readiness NOT confirmed") rather than a confirmed cover glass frame. | Trace those closes (cover capture latency vs readiness re-report window); raise the cap only if frames arrive just after it. |
| F3 | Medium | V | First capture after a switch takes 150-450 ms and bounds the black; 2 per run still time out at 250 ms with no frame to keep. Captures copy full bitmaps across Binder. | HardwareBuffer-backed captures (no copy), or capture at reduced resolution for the first post-switch frame. |
| F4 | Low | V | Readiness evidence (code 9) carries only an inner/cover flag, not a display id; a late frame from the old panel could count for the new one. | Add the display id to code 9 and to `HandoffFade.Draw`; match it in `HandoffFadePolicy`. |
| F5 | Low | V | Code 2 reply builds a Bundle of ~25 entries and several status strings on every 4 ms poll. | Send only changed diagnostics fields, or build status strings at 250 ms like `HandoffFade.status`. |
| F6 | Low | V | Tooling: screenrecord shows 700-850 ms black per switch in every build (frame drop on layer-stack reassignment), so video black time is not a valid metric; only the DuoHandoff log is. Screenshots run ~1/s, too sparse for single-frame glitches. | Report black time from the log only; add a denser inner screencap mode (or `dumpsys SurfaceFlinger --latency`) for frame-level review. |
| F7 | Low | V | `HandoffFade` reads `debug.duofold.right_blur_clear` and `preview_blur_max` once per fade-engine lifetime. | Covered by B10 (one cached tuning source). |

## Post-plan device verification and follow-ups (2026-10-05)

Device capture on a16.13 met the pass criteria: 0 sleep-path closes, 0 visible empty frames, half/half boundary on the
centre line at every angle, flat right half, matching blur on both sides of each switch. Follow-ups:

| Item | Result |
| --- | --- |
| Shorter post-switch black | Not achievable from the app: the destination's windows are first painted by Android's display-switch transition ~300 ms after the switch, and the first capture completes just after it. A 500 ms post-switch capture timeout was tried and reverted (opening black rose). |
| Left half black at 3-6 deg opening | Open. A warm cover frame while closed plus a pre-created strip still showed it in 2 of 7 samples; withdrawn. Suspected cause: the warm-capture trigger is evaluated at composition from non-Compose state, so it may never start. Next step: log whether it runs, fix the trigger, re-capture. |
| Idle CPU | Done in a16.14: adaptive glass capture rate; Flex at rest 43 % -> 20 % of a core, closed 13.4 % -> 7.4 %. |

## Order of work and verification

1. Phase A (A1, A2, A4, A8 verified first), each with its failing test.
2. B1 (capture fidelity) before any further visual review, then B5, B6, B10, B7, then B2+B3+B4 together
   (they share the strip layout), then B8, B9.
3. C1, C2, C3, then the rest of C and D.
4. E4, E5, E7, E8.
5. Per phase: `./gradlew testReleaseUnitTest assembleFold7test` green, install `fold7test`, one 120 s capture
   started only once the tester is ready to fold the device. Pass criteria: 0 `sleepDevice=true`; 0 visible empty frames;
   angle-ordered inner screenshots show a flat right half at every angle, matching blur on both sides of each
   switch, and no strip-boundary or perspective jump between the last pre-switch and first revealed frame.
