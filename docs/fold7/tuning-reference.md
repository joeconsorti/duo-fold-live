# Fold 7 tuning reference

Every value this change sets for the Galaxy Z Fold 7 (SM-F966 family, detected by
`DeviceCompatibility.isFold7`), every debug property it reads, and what each was measured against. Device:
SM-F966U1, One UI 8.5, Android 16 (SDK 36); inner panel 1968x2184, cover 1080x2520, both 420 dpi. The values
were measured there. After the phone's update to One UI 9.0 / Android 17 (SDK 37), the panels, display ids, device
states and hinge sensors were the same, and the values were kept unchanged. Every other
model keeps the upstream value in the "Upstream" column. Values marked **all models** apply everywhere.

## Panel-switch fade (`HandoffFadePolicy`, `HandoffFade`)

| Value | Fold 7 | Upstream | Source | Why |
| --- | --- | --- | --- | --- |
| Full-black angle, closing / opening | hold angle / handoff + 3 deg (94 / 101 at a 98 deg handoff; 81 / 88 at the 85 deg default) | hold angle / handoff angle | `blackAnglesFor`, `coverLagFor` | Measured switch at 92-94 deg closing, 101-102 deg opening with a 98 deg handoff (wallpaper angle log vs DisplayManager). Since 3.5.4 the handoff angle is a setting, so both follow it. |
| Reveal cap, no-glass path | ON+40 ms | ON+900 ms | `readyTimeoutFor` | Duo draws only on new angles; the wallpaper sends none for 0.3-0.5 s after a switch. |
| Reveal cap, opening (unified) | ON+600 ms | ON+900 ms | `openingReadyTimeoutFor` | First inner glass frame commits 300-450 ms after the switch; revealing earlier exposed the mirrored hold. |
| Reveal cap, closing (unified) | ON+380 ms | ON+900 ms | `closingReadyTimeoutFor` | The cover draws faster than the inner panel; 300 ms capped 3/10 closes whose frames landed at ON+260-380 ms. |
| Fresh-content window (reveal readiness and the fully-open endpoint) | 700 ms | 350 ms | `freshWindowFor` | The first post-switch capture alone takes up to ~400 ms. |
| Reveal length at 0 % gradualness | 100 ms | 180 ms | `revealBaseFor` | Reads as a short blink rather than a fade. |
| Re-check cadence during a switch | 16 ms | 80 ms | `tickDelayMs` | VSYNC can stop while a panel is OFF. |
| Idle re-check cadence (**all models**) | 50 ms | per vsync | `IDLE_TICK_MS` | Nothing fades; a new angle wakes the tick at once. |
| Keyguard check (**all models**) | every 200 ms | every frame | `HandoffFade` | Binder call; locking takes far longer. |
| Cover glass required for the closing reveal | yes (unified) | no | `coverGlass` | Otherwise the reveal landed on the dark capture fallback. |

## Glass frames (`GlassFramePolicy`, `GlassFrames`, `DuoGlass`)

| Value | Fold 7 | Upstream | Why |
| --- | --- | --- | --- |
| Render age limit for a glass frame | 1500 ms | 350 ms | Kept frames must stay renderable after a failed capture. |
| Keep last frame on a failed capture | up to 1500 ms, retry in 32 ms | cleared at once | "Panel changed during capture" (4->3 step), screen-off and 250 ms timeouts dropped good frames; ~21 visible empty frames per run before, 0 after. |
| Cover readiness re-report window (**all models**) | 1.5 s after the surface appears | once | A single report could be missed, holding black to the cap. |
| Closed cover counts as a reveal endpoint (**all models**) | yes | no | The native closed cover is the destination. |

## Unified renderer (`UnifiedRenderer`, `UnifiedTuning`, `DuoGlass`, `SecondaryShade`)

| Value | Fold 7 | Upstream | Why |
| --- | --- | --- | --- |
| Unified mode | on | off | The glass shader draws the inner left strip before the switch, so perspective corners, blur and timing are continuous. |
| Left-strip perspective profile | inner profile (early/end stretch, vertical compression, startup easing) | cover profile | Matches the post-switch leaf; the inner sliders tune both sides. |
| Reflection maximum hinge | 55 deg, then eased (`StripPose`) to the inner pose at the switch (hinge 77.9 deg at the 101 deg switch of a 98 deg handoff; blur factor 1 -> 1.25) | — | Edge-on collapse ~0.6 s before the switch; holding the cap froze the strip for ~46 deg and jumped at the reveal. |
| Pane layout | left strip 0..w/2, mirror fills w/2..w (scale 984/1080, centered, ~56 px cropped top/bottom); reflection same uniform scale; hold same layout; upright panels only | strip w - h*coverAspect, mirror fitted | The pane edge moved 48 px at the switch; the reflection was stretched ~10 %. |
| Glass edge darkening cap | 0.55 | 1.0 | Blacked the panel out around 104-114 deg while closing. |
| Mirrored-hold fade at reveal | 0 ms when ready, else 120 ms | 120 ms | Removed a double exposure at the cut-over. |

## Inner right half: preview mirror and blur (`InnerLiveMirror`, `PreviewBlurPolicy`, `RightHalfBlur`)

| Value | Fold 7 | Why |
| --- | --- | --- |
| Preview mirror blur, opening (before the switch) | smoothstep 0 px at 0 deg to 56 px at the opening switch angle (101 deg at a 98 deg handoff), glide time constant 40 ms, 8 ms ticks while moving | The full-resolution compositor mirror stayed sharp, then jumped to the blurred glass. |
| Right-half blur after the switch | 56 px at the opening switch angle, then the left glass's curve `smoothstep((172-a)/(172-90))` rescaled to that maximum, 0 at the open threshold; glide = `preview_blur_smooth_ms`; 0 at once when the cover is primary or the panel is rotated | The glass shader leaves the inner right half transparent, exposing the sharp native screen; the blur jumped 56 px -> 0 across the black, and then eased faster than the left half. |
| No-frame fallback | darkening capped at 0.55 (unified); nothing drawn below 6 % glass amount | The fallback reached solid black and was drawn invisibly at the endpoints. |

## Display overrides (`CloseReleaseGate`, `CoverHandoff`, `ConcurrentController`, `BaseDeviceState`) — all models

| Value | Setting | Why |
| --- | --- | --- |
| Release condition for a held override | base state CLOSED, or reopening at or above the handoff angle, or display off, or 3 s | 5->2->0 is a sleep transition on Samsung (`sleepDevice=true`), blanking the cover. |
| "Display off" input | `PowerManager.isInteractive()` only | Locked / service restarting / disabled do not make the sleep harmless. |
| Teardown wait | up to 3 s for CLOSED | Reader stop, Shizuku destroy and restart cancel the override. |
| Base-state query rate | at most every 16 ms, after the cheap escapes | Runs on the 4 ms poll under the reader lock during a defer. |
| Closing-mirror mask bound | 1.8 s after the secondary panel is gone | It stayed active for as long as the phone was closed. |

## Angle polling (`PollCadence`, `LiveAngles`) — all models

| Value | Setting | Why |
| --- | --- | --- |
| Poll period | 4 ms while the angle changed in the last 400 ms, or within 2 s of a change inside the 80-115 deg band; 33 ms otherwise; 500 ms with the screen off | The wallpaper reports every 30-130 ms in 2-3 deg steps; 4 ms polling when still bought nothing, and resting in Flex posture kept it fast forever. |
| Fade engine idle | no per-vsync ticks unless switching, fading or within the same motion/band window | Same reason. |
| Keyguard checks | overlay: 200 ms cache cleared on screen/unlock broadcasts; hold: every 200 ms | Binder call per animation frame / per 8 ms tick. |
| Glass content capture rate | `content_fps` while moving or within the post-switch window; 10 fps once still for 1 s (`RenderQuality.STILL_FPS`) | Resting in Flex captured at 120 fps: 43 % of a core, now 20 %. |
| Shade frame loop | suspended once the smoothed angle settles; wakes on angle or panel change, 250 ms re-check | Ran at 120 Hz whenever the screen was on. |
| Secondary-panel refresh | 8 ms moving, 100 ms still | Each refresh makes several Binder calls on the main thread. |
| Mirror attach IPC | own thread on both sides | The attach waits up to 250 ms for its commit and stalled the angle poll mid-transition. |

## Debug properties

Shell-writable (`adb shell setprop <name> <value>`); no rebuild needed. Read where noted.

| Property | Default | Read | Effect |
| --- | --- | --- | --- |
| `debug.duofold.unified` | on (Fold 7), off elsewhere | per decision | `1`/`0` forces the unified renderer on or off. |
| `debug.duofold.reflect_max_hinge` | 55 | cached 1 s | Maximum hinge angle used for the left-strip reflection. |
| `debug.duofold.max_darken` | 0.55 | cached 1 s | Glass edge darkening cap in unified mode. |
| `debug.duofold.preview_blur_start` | 0 | `BlurTuning`, cached 1 s (applied per mirror attach) | Angle where the preview mirror blur starts. |
| `debug.duofold.preview_blur_max` | 56 | `BlurTuning`, cached 1 s (mirror per attach, hold per prepare, right half live) | Maximum preview, hold and right-half blur radius in px. |
| `debug.duofold.preview_blur_smooth_ms` | 40 | `BlurTuning`, cached 1 s | Glide time constant of the mirror and right-half blur. |
| `debug.duofold.right_blur_clear` | open threshold (172) | `BlurTuning`, cached 1 s | Angle where the right half is sharp again after the switch. |
| `debug.duofold.record_visible` | 0 | cached 1 s, applied live | `1` keeps Duo's overlay layers visible to screen recordings (fade, hold, blur, mirror blur, right-half blur) and the main glass whenever the cover->inner mirror is not attached (skip-screenshot doubles as the mirror exclusion); recorded layers are excluded from Duo's own capture. |

All blur properties are read through `BlurTuning` (plan B10).

## Build types

| Type | Optimized | Debuggable | Signing | Diagnostic logs |
| --- | --- | --- | --- | --- |
| `debug` | no | yes | debug key | yes |
| `release` | yes | no | release key from `DUO_KEYSTORE` env, else unsigned | no |
| `fold7test` | yes (`initWith(release)`) | no | debug key, installs over debug | yes |

Diagnostic log tags (`BuildConfig.DIAGNOSTICS`): `DuoHandoff`, `DuoReady`, `DuoState`, `DuoBlur`, `DuoMirror`,
`DuoFallback`, `DuoHold`.
