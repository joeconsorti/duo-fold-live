# Duo Fold Live · v1.5.0

Hinge-driven fold transitions for the Samsung Galaxy Z Fold 8, with a two-screen screenshot handoff and an optional custom photo wallpaper.

**Supported automatic setup: SM-F971U, Android 17 / SDK 37.** Samsung private APIs and the installed FoldInteractive wallpaper engine are required. Other models and firmware are not verified. This is an independent project, unaffiliated with Samsung, Apple, Shizuku, or the upstream animation authors.

## Install and set up

Install the signed release APK. A fresh installation guides you through:

1. **Required fold wallpaper.** Both inner and cover HOME wallpapers must use Samsung's interactive fold wallpaper. The app explains this before making changes. There is no alternative hinge-source selection in setup.
2. **Optional custom photo.** Pick an image to display behind your icons, or keep the Samsung wallpaper visible.
3. **Shizuku.** Download it from [the official Shizuku download page](https://shizuku.rikka.app/download/), start it using wireless debugging or USB debugging, and authorize Duo. The app automatically applies the required home wallpapers to Samsung slots 5 and 17 and verifies the component and video profile in each slot. It does not write the lock-screen slots.
4. **Android permissions.** Allow overlays and enable Duo's accessibility service. If Android blocks accessibility, open App info → ⋮ → Allow restricted settings, then return to Accessibility. Finish enables the animation and starts the selected photo layer.

The Samsung wallpaper application and assets must already be installed. They are **not bundled** with this project. Wallpaper setup uses the profile extracted from the tested device (`FoldInteractive`, `video_001.mp4`, thumbnail frame 545). Configuration readback is checked; this alone does not prove that a different firmware can deliver hinge-angle events.

### Wireless debugging

Connect to Wi-Fi. Enable Developer options by tapping Build number seven times in Settings → About phone → Software information. Enable USB debugging and Wireless debugging. In Shizuku, choose Pairing; use Wireless debugging → Pair device with pairing code and enter the code in Shizuku's notification. Return to Shizuku and tap Start, then authorize Duo when asked.

### USB debugging

Download [Google platform-tools](https://developer.android.com/tools/releases/platform-tools), extract them, and open a terminal **inside the extracted platform-tools directory**. Enable USB debugging, connect the phone, and accept its debugging authorization prompt.

Windows PowerShell:

```powershell
.\adb.exe devices
.\adb.exe shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```

On macOS/Linux, replace `.\adb.exe` with `./adb`. If Shizuku shows a different startup command, use that current command. See [Shizuku's official setup guide](https://shizuku.rikka.app/guide/setup/).

Shizuku generally needs to be started again after a reboot. Allow Shizuku and Duo to run in the background. The app cannot silently enable Android debugging or approve its own Shizuku/accessibility access.

## Existing users

Install the release over your existing signed Duo Fold Live app. **Do not uninstall first.** Application ID and signing identity are retained. Existing installations skip onboarding and retain their preferences, photo, wallpaper bindings, and animation state. A setup already in progress resumes after an update.

This is the first public release numbered **v1.5.0**, with Android `versionCode 23`. It succeeds the private build labeled `1.6.0` (`versionCode 22`); Android uses the increasing version code for updates.

## Controls

- **iPhone Duo Inspired** is the default animation (previously labeled “iPhone Duo Ask”).
- **Classic Glass** preserves the earlier animation.
- The animation selector is directly below Enable animation.
- The default fully-open endpoint is 172°, adjustable in Advanced.
- Keep cover awake on close defaults on for new setup. Existing settings remain unchanged.
- Screenshot handoff, display switching, smoothing, shaders, and wallpaper renderer are unchanged from the previous private build.
- Custom wallpaper remains an in-memory layer while the host runs. The Samsung home wallpaper stays installed as the hinge source. Disable removes the custom layer; it does not restore the home wallpaper that preceded Samsung's fold wallpaper. Restore another home wallpaper through Samsung Settings if desired; doing so removes the required angle source.

## Known limitations

- Custom wallpaper can still briefly reveal the underlying Samsung wallpaper during unlock. Rotation retention is implemented. This release does not claim to fix the unlock flash.
- Photo layers may need enabling again after a reboot or if Android ends the host.
- Restricted/secure content may not be captured. Display handoff depends on Samsung firmware behavior.
- Refresh requests target supported display rates up to 120 Hz. Actual FPS is device/workload dependent; no 120 FPS guarantee.
- First-install automatic wallpaper setup is new in this release and needs on-device verification. Existing users do not exercise this code on upgrade.

## Build

Install JDK 17, Android SDK platform 36, and Android build tools. Set `ANDROID_HOME` or a local `sdk.dir` in `local.properties`.

```sh
./gradlew testReleaseUnitTest assembleRelease
```

Without signing environment variables this creates an **unsigned** release APK. Debug builds use your local Android debug key and cannot replace the official signed release.

For a signed release, supply these environment variables privately: `DUO_KEYSTORE` (absolute path), `DUO_STORE_PASSWORD`, `DUO_KEY_ALIAS`, and `DUO_KEY_PASSWORD`. No signing key is included in this repository. A different key cannot update an existing install.

Compile-only framework stubs in `wallpaper-stubs/` describe hidden Android interfaces; they must never be packaged as Android framework replacements. `app/libs/` contains the existing Shizuku client jars with checksums in `DEPENDENCIES.md`.

## Architecture

- `LiveAngles` / `AngleReader`: Shizuku-assisted Samsung wallpaper angle reader.
- `ConcurrentController`, `HandoffFrames`, `FreezePolicy`: dual-panel screenshot handoff.
- `DuoGlass`, `ClassicGlassShader`, `FrameSmoothing`: renderers and frame-rate-independent smoothing.
- `FirstRun`, `OnboardingPolicy`, `SetupActivity`: first-install flow and upgrade preservation.
- `WallpaperSetupService`: short-lived ADB-mode helper for required home-wallpaper setup.
- `com.consorti.wallpaperlayer`: retained custom-photo compositor host and diagnostics.

## License and acknowledgments

MIT for project code, subject to the preserved third-party licenses. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Animation code includes adaptations from [McCal-Codes/folio v0.6.0](https://github.com/McCal-Codes/folio/releases/tag/v0.6.0) and [chuspeeism/iphone-duo](https://github.com/chuspeeism/iphone-duo). Original license notices are bundled in the app.
