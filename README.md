![DuoFold Live](docs/assets/duofold-live-logo.svg)

# Duo Fold Live · 3.0.0

An iPhone Duo-inspired folding animation for the **Samsung Galaxy Z Fold 8**, with windowed glass, live cover previews, and smooth black fades around Samsung’s display handoff. Free and open source. Runs over One UI and ordinary apps without replacing your launcher.

**[Download 3.0.0](https://github.com/joeconsorti/duo-fold-live/releases/latest)** · [Full changelog since 1.7.0](docs/3.0.0-CHANGELOG.md) · [Report an issue](https://github.com/joeconsorti/duo-fold-live/issues)

**Only tested on the Z Fold 8 (SM-F971U).** Fold 8 Ultra, Fold 7, and other devices are unverified. Do not expect compatibility. Device checks may prevent setup on unsupported models.

## Updating from an older version

The signing key changed. **Older public installs require one uninstall/reinstall**, followed by setup again. Uninstalling removes local settings and the saved custom wallpaper. Recent alphas using the current key can update directly. Future releases using this permanent key will update in place; another signing-key migration is not planned.

## Quick setup

1. Download and install the APK from Releases.
2. Follow the app’s setup guide: start Shizuku/Shizuku+, authorize Duo, and apply the required Samsung fold wallpapers when prompted.
3. Allow the requested overlay/accessibility permissions and enable the animation. If Android blocks accessibility, use App info → ⋮ → Allow restricted settings.
4. Optionally choose a custom wallpaper in the built-in wallpaper menu.

The app guides setup. Samsung’s interactive wallpaper and Shizuku running in ADB mode are required for hinge measurements. The Samsung assets are not bundled. Shizuku normally needs restarting after a phone reboot. The in-app **Trouble with Shizuku/Fold shutting off?** section covers background operation and offline troubleshooting.

## What’s new since v1

- **Windowed Glass** by default: live cover preview, frosted glass, and matched fades around the display switch.
- **Duo Classic, Fold-Only, and Unfold-Only** animation options.
- Lower-latency hinge tracking, lighter rendering, half-resolution glass by default, and high-refresh rendering targeting up to 120 Hz. Actual frame rate depends on workload and firmware.
- Separate controls for glass smoothing, fade smoothing, and fade gradualness.
- Keep-awake ON by default, with background verification and recovery when the privileged connection is available.
- A redesigned app and integrated custom wallpaper menu, with folded/unfolded crop previews and saved-photo recovery across compatible updates.
- A 2° fully-closed default, adjustable down to 1°.
- Advanced → Developer settings keeps experimental controls and diagnostics out of the main flow.
- Optional donations, with weekly support invitations starting after one week of successful use; snooze and permanent dismissal included.

The approved animation is preserved. Fades soften Samsung’s primary-display blackout; they do not eliminate the underlying panel power transition. The mirrored preview is not two independent apps running on both panels.

## Known bugs

1. Unlocking directly onto Home may briefly reveal the underlying live wallpaper before the custom photo. A fix is being worked on.
2. Occasionally part of the unfolding fade does not trigger correctly, so the transition may look choppy.

**Work in progress:** reliable orientation locking during folding and unfolding. The current attempt still needs device validation.

## Troubleshooting and reports

Use **Trouble with Shizuku/Fold shutting off?** beneath the animation options first. Check that Shizuku is running and authorized and Duo’s accessibility service is enabled.

For an issue, include your app version, phone model, Android/One UI version, fold direction, and steps to reproduce. Attach **Copy status report**; for wallpaper issues also use **Copy background report** in the wallpaper menu. A short recording helps. Review reports for personal information before sharing.

All future release announcements and updates will be on GitHub.

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
- `CoverHandoff`, `DirectHandoffPolicy`, `InnerLiveMirror`, `PreviewExpansion`: default preview and direct concurrent handoff.
- `ConcurrentController`, `HandoffFrames`, `FreezePolicy`: optional dual-panel screenshot handoff.
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
