# Current public promotion — 3.6.0

Owner approved alpha.111 for the public stable release on 2026-11-11. Version `3.6.0-reconstructed` (versionCode at least 1366), tag `v3.6.0`, public package `org.duofold.live`. Runtime matches alpha.111 (`8bc14e2828210ca8fa9cccb78d438a9eec9e19eb`) exactly: the added preview darkening is removed, and existing frost, animation, handoff, jitter and rotation-recovery behavior is retained. Public signing must match v3.5.8/v3.5.9. See `docs/3.6.0-reconstructed-CHANGELOG.md`. User-reported rotation recovery in issue #30 is not a universal device/firmware verification.

## Previous public promotion — 3.5.9

Owner approved alpha.109 for the public stable release on 2026-11-09. Version `3.5.9-reconstructed` (versionCode at least 1363), tag `v3.5.9`, public package `org.duofold.live`. Runtime matches alpha.109; promotion changes version metadata, release workflow and documentation only. Release signing must match the public v3.5.8 certificate. The reconstructed-source label is retained, and automated tests are not handset/optical proof.

Alpha.107 remains the preserved earlier user-approved local rollback reference, with its exact original artifacts and tag. See `STABLE_REFERENCE.json` and `docs/3.5.9-reconstructed-CHANGELOG.md`.

# Previous release baseline — 3.5.5

Owner approved stable/main promotion on 2026-10-17. This release retains the alpha.34 runtime, launcher icon, and optional support reminders, with stable version metadata and documentation updates only.

- Approved candidate: **3.5.5-alpha.34**, [CI 204](https://github.com/joeconsorti/duo-fold-live/releases/tag/ci-204-1), source `6156ef4580954b9b2cafdbe32d89648fd419eba3`.
- Stable: [v3.5.5](https://github.com/joeconsorti/duo-fold-live/releases/tag/v3.5.5).
- Preserve alpha.32 below as the pre-performance-cleanup comparison/rollback checkpoint. No APK shrinking.
- Software frame commits do not prove visible pixels. Samsung's panel-switch interval and occasional wallpaper unlock flashing remain known limitations.

# Previous stable baseline — 3.5.4

User approved 3.5.4-alpha.7 cover-close behavior for stable promotion on 2026-10-12 after reporting that the full-close blackout totally disappeared on SM-F971U / Android 17. Stable 3.5.4 adds requested Smooth (120 ms default) / Fast (30 ms) presets and removes the unused closing power-pulse experiment. The tested closure behavior and geometry are retained.

- Tested alpha: [ci-161-1](https://github.com/joeconsorti/duo-fold-live/releases/tag/ci-161-1), source `4320933c746e34f933b4e2fe86c283a7de935640`.
- Stable: [v3.5.4](https://github.com/joeconsorti/duo-fold-live/releases/tag/v3.5.4).
- Preserve all historical releases and signing identity. Optical behavior still requires handset testing; the separate mid-fold switch and wallpaper unlock flash are not claimed fixed.

## 3.5.5 pre-release checkpoints — 2026-10-17

- **Before performance cleanup: 3.5.5-alpha.32**, [CI 202](https://github.com/joeconsorti/duo-fold-live/releases/tag/ci-202-1), source `4d33937b2e0e6a73e0a3f6d1e7c71da7b63fd1e6`. User-approved black reveal and confirmed layer cleanup; preserve as the pre-optimization comparison/rollback build.
- **Retained candidate: 3.5.5-alpha.33**, [CI 203](https://github.com/joeconsorti/duo-fold-live/releases/tag/ci-203-1), source `62e3b1a02076fe39a531ddeeba9e9efa3aeda374`. User approved keeping the performance cleanup. No APK shrinking. The approved launcher icon and support reminder updates were added in alpha.34, then promoted as stable 3.5.5.

## Historical wallpaper rollback record

# Wallpaper rollback baseline — preserve alpha.13

**Current user-approved best version: 3.5.1, based on the user-tested 3.5.1-alpha.9 and approved for main/latest on 2026-09-26.** The user still observes occasional immediate/delayed unlock flashing. Alpha.13 remains the preserved original partly working rollback baseline; do not delete or rewrite it. Automated checks alone do not establish visual correctness.

- Release/tag: [ci-27-1](https://github.com/joeconsorti/duo-fold-live/releases/tag/ci-27-1)
- Original signed APK: [Duo-Fold-Live-ci-27-1.apk](https://github.com/joeconsorti/duo-fold-live/releases/download/ci-27-1/Duo-Fold-Live-ci-27-1.apk)
- Exact source commit: `83e40e827238da11b2eee6a3d95ce038f6b57bea`
- Source branch: `alpha/3.0.4-alpha.13`
- User confirmation: “NO FLASH ANYMORE! WE FIXED IT! (with one issue).”
- Known limitation: the flash remains when the device stays awake at the lock screen for several seconds before unlocking. Quick wake/unlock works according to the user.

Keep the original release, assets, tag and source commit intact. If a later experiment regresses, restore this source in a new build with a higher version code so users can update without uninstalling or losing settings. An original APK downgrade may be refused by Android.

## Rejected experiment and recovery

- Alpha.14 (`ci-28-1`, source `7dd629ebce55afcbeb043299338488937662c875`) was rejected by the user: worse unlock flashing and a new large flicker after applying wallpaper. Do not use it as a baseline.
- Alpha.15 restores alpha.13 runtime source exactly, with a higher version code for installation over alpha.14. It is a recovery build, not a fix for the remaining delayed-unlock issue. Alpha.13 remains the only user-confirmed partly working baseline.
