# Duo Fold Live v1.5.1

First public source release of Duo Fold Live for Samsung Galaxy Z Fold 8 (tested model SM-F971U, Android 17).

- **iPhone Duo Inspired** is the default animation; **Classic Glass** remains available directly beneath Enable animation.
- New-install onboarding explains the required Samsung wallpaper, offers a custom photo, and guides wireless or USB Shizuku setup.
- After Shizuku authorization, setup applies and verifies Samsung's interactive wallpaper on both inner and cover **home** screens. Lock wallpapers are not changed by onboarding.
- Future upgrades under the new app identity skip onboarding and preserve settings and photos.
- The working screenshot handoff, rendering, 172° endpoint, and smoothing are retained.

This privacy-clean build uses a new neutral application identity and signing certificate. It installs separately from older private builds; settings do not migrate. Disable the old animation/photo host before completing setup in this app. The old installation is not overwritten.

Requires Samsung's installed FoldInteractive engine, Shizuku in ADB mode, overlay access, and accessibility. New automatic wallpaper setup currently supports SM-F971U on Android 17 only. No Samsung APKs or videos are bundled.

Known limitations: the custom wallpaper can still flash the underlying wallpaper during unlock; Shizuku/photo hosting may need restarting after reboot or process termination; actual FPS depends on the device and workload. Fresh-install onboarding needs handset verification; automated tests do not verify Samsung display behavior.

Source is MIT with preserved third-party notices. Thanks to McCal-Codes/folio, chuspeeism/iphone-duo, Duo Launcher contributors, and Shizuku.
