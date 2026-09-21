# Duo Fold Live · v1.5.4

Hinge-driven opening and closing animations for the Samsung Galaxy Z Fold 8, running over One UI and ordinary apps without replacing your launcher. Includes two-screen screenshot handoff and an optional custom photo wallpaper.

The animation follows live hinge measurements; the outgoing screen content is a freeze frame during handoff. This is an Android overlay app, not a system feature supplied by Samsung. See the known issues below before installing.

**v1.5.4 is available in Releases.** Download `Duo-Fold-Live-v1.5.4.apk`. It updates public v1.5.1 without uninstalling. This release removes exact-model restrictions from wallpaper setup and both display controllers. Regional firmware still needs on-device confirmation.

**Automatic setup enabled: regional SM-F971 variants on Android 17 / SDK 37, including U1 and W.** Samsung private APIs and the installed FoldInteractive wallpaper engine are required. Regional support is experimental; only SM-F971U has been tested on-device. Fold 7 (SM-F966, including SM-F966W) is not supported by this wallpaper profile. This is an independent project, unaffiliated with Samsung, Apple, Shizuku, or the upstream animation authors.

## Install and set up

Install the signed release APK. A fresh installation guides you through:

1. **Required fold wallpaper.** Both inner and cover HOME wallpapers must use Samsung's interactive fold wallpaper. The app explains this before making changes. There is no alternative hinge-source selection in setup.
2. **Optional custom photo.** Pick an image to display behind your icons, or keep the Samsung wallpaper visible.
3. **Shizuku.** Use the primary **Download Shizuku+ (Recommended)** button to open [Shizuku+ releases](https://github.com/thejaustin/ShizukuPlus/releases), or choose **Official Shizuku (Alternative)**. Plus compatibility is still being verified. In either case, start it using wireless debugging or USB debugging, and authorize Duo. After authorization, tap **Apply required wallpapers**. The app applies the required home wallpapers to Samsung slots 5 and 17 and verifies the component and video profile in each slot. It does not write the lock-screen slots.
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

## How it works

Samsung’s interactive fold wallpaper runs on both home-screen wallpaper slots, including the cover slot that is normally unavailable through Samsung’s picker. A Shizuku-assisted reader obtains the angle reported by that engine. The animation uses those measurements rather than a fixed-duration playback.

During a handoff, Duo Fold Live captures the outgoing display and animates that frozen content while switching the incoming display into use. The optional photo layer covers the Samsung home wallpaper visually while preserving it as the angle source. This does **not** mean both screens contain independent, fully live apps throughout the transition.

The supported setup uses official Shizuku in **ADB mode** (wireless or USB debugging). Root-mode setup and Shizuku forks have not been verified for this release; the automatic wallpaper helper explicitly requires the ADB-shell identity. No claim is made that other foldables expose unrestricted hinge data.

## Existing users

This privacy-clean release uses the new Android application ID `org.duofold.live` and a new neutral signing certificate. It installs separately from earlier private builds and cannot update or automatically read their private settings. Your old app is left untouched. Disable its animation and custom wallpaper before enabling this app, then complete setup here. Do not run both animation hosts together.

Public version **v1.5.4**, Android `versionCode 27`. Existing-install bypass still applies to future upgrades of this new application ID.

## Controls

- **iPhone Duo Inspired** is the default animation (previously labeled “iPhone Duo Ask”).
- **Classic Glass** preserves the earlier animation.
- The animation selector is directly below Enable animation.
- The default fully-open endpoint is 172°, adjustable in Advanced.
- Keep cover awake on close defaults on for new setup. Future updates under this new app identity preserve settings.
- Screenshot handoff, display switching, smoothing, shaders, and wallpaper renderer are unchanged from the previous private build.
- Custom wallpaper remains an in-memory layer while the host runs. The Samsung home wallpaper stays installed as the hinge source. Disable removes the custom layer; it does not restore the home wallpaper that preceded Samsung's fold wallpaper. Restore another home wallpaper through Samsung Settings if desired; doing so removes the required angle source.

## Known issues and ways to contribute

1. **Brief black screen during screenshot handoff.** Capturing/freezing the outgoing screen and lighting the incoming display can introduce a visible pause or black flash. Earlier testing observed roughly 0.5–1 second in some transitions. Help is welcome with capture timing, retaining a valid frame, and reducing display-switch latency without reintroducing ghosting.
2. **Custom wallpaper flashes on unlock.** Samsung’s underlying live wallpaper may appear briefly before the custom photo. Retaining the photo across rotation has improved, but the unlock flash remains unresolved. Contributions should preserve live angle delivery and keep photo layers below app content.
3. **Shizuku availability.** If the Shizuku service stops, the animation cannot use its live angle source until Shizuku is running and authorized again. Reconnection/recovery improvements are welcome. Reboot also normally requires starting Shizuku again.
4. **Long-term goal: live content on both panels.** Both physical panels have been illuminated in experiments, but two independent live app surfaces throughout the transition are not implemented. The current method deliberately retains screenshot handoff. Work on simultaneous live composition is welcome.
5. **Background photo host.** The photo layer may need enabling again after a reboot or if Android ends the host.
6. **Capture and performance limits.** Secure/restricted content may not be captured. Display behavior depends on Samsung firmware. Refresh requests target supported rates up to 120 Hz; actual FPS is workload dependent.
7. **Device and setup coverage.** The animation has been developed and tested on SM-F971U. The renamed helper components and fresh-install automatic wallpaper setup still need handset verification. Regional SM-F971 models on Android 17 can now attempt setup; component/API checks and wallpaper readback remain required. Other foldables and Android versions are not enabled.

Issues and pull requests are welcome, especially for these items. Include app version, model, Android/One UI version, direction of the fold, and reproduction steps. Review diagnostic logs and recordings for personal information before sharing. Keep upstream licenses and avoid committing signing keys, personal photos, or device logs into source.

## Optional support

Duo Fold Live is free and open source. If you find it useful and would like to support the work, tips are welcome and entirely optional.

- **USD tips:** [Support Duo Fold Live on Ko-fi](https://ko-fi.com/joeconsorti)
- **Bitcoin tips (BTC, Bitcoin mainnet):**

```text
bc1qz86gl559xlg8k3qur45xrlel2x79wu5ecaw0ss
```

Tips do not unlock features or promise fixes. Bug reports, testing, and pull requests are equally appreciated.

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
- `org.duofold.live.wallpaperlayer`: retained custom-photo compositor host and diagnostics.

## Credits and license

Duo Fold Live is the name of this app. The following are separate upstream projects whose work deserves credit:

- **[Folio launcher — McCal-Codes/folio](https://github.com/McCal-Codes/folio)**: the [v0.6.0 release](https://github.com/McCal-Codes/folio/releases/tag/v0.6.0) supplied a starting point for the fold engine and diagnostics. Its upstream lineage includes Duo Launcher. The preserved MIT notice credits Duo Launcher contributors.
- **[iPhone Duo animation concept — chuspeeism/iphone-duo](https://github.com/chuspeeism/iphone-duo)**: the interactive Three.js reference for projected screen content, progressive blur, and darkening. The iPhone Duo Inspired option adapts this work to Android rendering; it does not embed the Three.js demo unchanged. The upstream MIT notice credits jadon7.
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** and its API contributors: the ADB-assisted access used by the live angle reader and helpers.

Thank you to the original authors and contributors. These credits do not imply their endorsement of this app. MIT for project code, subject to preserved third-party licenses; see [LICENSE](LICENSE), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), and the notices bundled under `app/src/main/assets/licenses/`.
