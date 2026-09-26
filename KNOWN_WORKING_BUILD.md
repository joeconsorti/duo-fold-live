# Wallpaper rollback baseline — preserve alpha.13

**3.0.4-alpha.13 is the only user-confirmed partly working build for the unlock wallpaper flash, as of 2026-09-26.** Do not replace this baseline based only on automated checks. New experiments remain unverified until the user confirms them.

- Release/tag: [ci-27-1](https://github.com/joeconsorti/duo-fold-live/releases/tag/ci-27-1)
- Original signed APK: [Duo-Fold-Live-ci-27-1.apk](https://github.com/joeconsorti/duo-fold-live/releases/download/ci-27-1/Duo-Fold-Live-ci-27-1.apk)
- Exact source commit: `83e40e827238da11b2eee6a3d95ce038f6b57bea`
- Source branch: `alpha/3.0.4-alpha.13`
- User confirmation: “NO FLASH ANYMORE! WE FIXED IT! (with one issue).”
- Known limitation: the flash remains when the device stays awake at the lock screen for several seconds before unlocking. Quick wake/unlock works according to the user.

Keep the original release, assets, tag and source commit intact. If a later experiment regresses, restore this source in a new build with a higher version code so users can update without uninstalling or losing settings. An original APK downgrade may be refused by Android.
