# Duo Fold Live v1.5.0

First public source release of Duo Fold Live for Samsung Galaxy Z Fold 8 (tested model SM-F971U, Android 17).

- **iPhone Duo Inspired** is the default animation; **Classic Glass** remains available directly beneath Enable animation.
- New-install onboarding explains the required Samsung wallpaper, offers a custom photo, and guides wireless or USB Shizuku setup.
- After Shizuku authorization, setup applies and verifies Samsung's interactive wallpaper on both inner and cover **home** screens. Lock wallpapers are not changed by onboarding.
- Existing installations skip onboarding and preserve their settings and photos.
- The working screenshot handoff, rendering, 172° endpoint, and smoothing are retained.

Install the attached APK over your previous signed build. **Do not uninstall first.** Although the public version is labeled 1.5.0, its Android version code is 23, higher than the earlier private 1.6.0 build.

Requires Samsung's installed FoldInteractive engine, Shizuku in ADB mode, overlay access, and accessibility. New automatic wallpaper setup currently supports SM-F971U on Android 17 only. No Samsung APKs or videos are bundled.

Known limitations: the custom wallpaper can still flash the underlying wallpaper during unlock; Shizuku/photo hosting may need restarting after reboot or process termination; actual FPS depends on the device and workload. Fresh-install onboarding needs handset verification; automated tests do not verify Samsung display behavior.

Source is MIT with preserved third-party notices. Thanks to McCal-Codes/folio, chuspeeism/iphone-duo, Duo Launcher contributors, and Shizuku.
