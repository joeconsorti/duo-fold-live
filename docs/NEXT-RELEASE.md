# Next release

## 3.5.9-alpha.66 — adaptive resolution

Default-on thermal adaptation: selected maximum -> 25% at thermal status >=1; 50% at status 0; selected maximum after ten continuous minutes at status 0. Slider maximum and opt-out persist. Changes apply between animations. New/reset defaults are 100% resolution, 50% glass and 75% blur; existing saved settings are retained. See [alpha.66 release notes](3.5.9-alpha.66-reconstructed-CHANGELOG.md).

- User confirmed on 2026-10-13 that the Windowed Glass retreat is fixed in 3.5.5-alpha.2.
- Glass strength defaults to 40% for new setups and Reset glass strength. Existing saved strengths remain selected. UI, renderer, screenshot path, report fallback and initial setup agree.
- Custom wallpaper unlock flash: partial success / largely resolved in 3.5.5-alpha.5. User reports no flashes after the off/on test, with possibly one on the first unlock, versus every unlock before preparation was implemented. Software logs also show a completed atomic Home handoff after re-enabling. Keep wake preparation ON by default in future builds, with the three-second-hold developer override. Defer residual first-unlock / missing-Home-eligibility investigation until after the main priorities; do not claim every edge case is fixed.

Remaining main priorities (owner order, 2026-10-13):
1. Screenshot mode first (owner update 2026-10-13), using its findings to improve standard mid-fold display handoff. Alpha.6 tests live incoming animation captures after exact app verification; Home routing and native endpoint switching remain unresolved.
2. Performance consistency.
3. Quick-close unanimated cover flash, last of the main priorities.

After the main priorities:
4. Intermittent rotation recovery.
5. On-device validation of direct root support.
6. Revisit residual custom wallpaper unlock flash edge cases.


## 3.5.5-alpha.14 — wallpaper studio preview
Independent cover/inner photos and crop controls, shared rendering selection, and an app visual refresh. Existing advanced wallpaper controls retained. Validate on-device before promotion; remap flash remains tabled. See `3.5.5-alpha.14-CHANGELOG.md`.

## Auto-rotate recovery visibility

Move the existing verified Repair auto-rotate action directly below Folding Mode and above Trouble with Shizuku on the main screen, outside advanced settings. Explain that animation must be off and that repair enables auto-rotate on both panels. Preserve automatic journal-based recovery and the one-time legacy repair; do not force auto-rotate on every launch because a user may deliberately lock rotation.

History: 3.0.4-alpha.6 corrected restoration across physical-panel remaps and incomplete cleanup; 3.5.2-alpha.2 added independent reader-exit recovery. These explain known failure paths, not an unverified new report. A new occurrence needs the affected version and rotation-release diagnostics.
