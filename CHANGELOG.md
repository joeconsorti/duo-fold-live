# Unreleased — Handoff fade and unlock flash fixes

- Fix fade dropping out mid-transition: when the fade is disabled, the angle goes stale, the keyguard appears or the primary display is briefly unavailable, visible black layers now ease out over the reveal duration instead of being removed in one frame. If conditions recover first (e.g. a 2° wobble flipping Fold-Only/Unfold-Only), the fade resumes without forgetting the in-flight display switch.
- Shorter black after a full unfold: when the phone is fully open and the inner screen's ready signal is missing, the unconfirmed fallback reveal now starts 350 ms after the panel turns on instead of 900 ms. Mid-fold switches keep the 900 ms fallback, and stale frames still never count as confirmed readiness. Status reports now keep a "last reveal" line saying how long the black lasted and why.
- The "no switch arrived" 1.2 s black hold now eases out instead of popping to 0.
- Active fade ramps use a 16 ms fallback tick instead of 80 ms, so a stalled VSYNC around the panel switch can no longer drop the reveal to ~12 fps.
- Reduce unlock wallpaper flashes: a Home sample up to 4 s before SCREEN_OFF (was 1.5 s) now qualifies for the wake bridge. Sampling cadence plus late SCREEN_OFF delivery could exceed 1.5 s, so no bridge was prepared.
- Samsung's physical display-switch blackout itself is unchanged; these fixes make the fade that covers it more reliable.

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
