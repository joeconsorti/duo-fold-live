![DuoFold Live](docs/assets/duofold-live-logo.svg)

# Duo Fold Live · 3.5.0

**Current official release: 3.5.0.** Occasional unlock wallpaper flashing and Keep Awake remain known issues. [Rollback baseline and release history](KNOWN_WORKING_BUILD.md).

## 📺 Step-by-step installation tutorial
**New here? [Watch the full setup walkthrough on YouTube](https://www.youtube.com/watch?v=8Ucm7ceBDN4).**

Created by a Duo Fold Live user—not by me. Huge thanks for putting this together!

An iPhone Duo-inspired folding animation for the **Samsung Galaxy Z Fold 8**, with windowed glass, live cover previews, and smooth black fades around Samsung’s display handoff. Free and open source. Runs over One UI and ordinary apps without replacing your launcher.

**[Download 3.5.0](https://github.com/joeconsorti/duo-fold-live/releases/latest)** · [3.5.0 release notes](docs/3.5.0-CHANGELOG.md) · [Report an issue](https://github.com/joeconsorti/duo-fold-live/issues)

**Only tested on the Z Fold 8 (SM-F971U).** Fold 8 Ultra, Fold 7, and other devices are unverified. Do not expect compatibility. Model recognition includes regional SM-F971, SM-F976, and SM-F966 families. Unknown models display a warning and may proceed. Android 17 and the existing Samsung wallpaper/API checks are still required. Recognition is not verified compatibility.

## Updating from an older version

The signing key changed. **Older public installs require one uninstall/reinstall**, followed by setup again. Uninstalling removes local settings and the saved custom wallpaper. Recent alphas using the current key can update directly. Future releases using this permanent key will update in place; another signing-key migration is not planned.

## Quick setup

1. Download and install the APK from Releases.
2. Follow the app’s setup guide: start Shizuku/Shizuku+, authorize Duo, and apply the required Samsung fold wallpapers when prompted.
3. Allow the requested overlay/accessibility permissions and enable the animation. If Android blocks accessibility, use App info → ⋮ → Allow restricted settings.
4. Optionally choose a custom wallpaper in the built-in wallpaper menu.

The app guides setup. Samsung’s interactive wallpaper and Shizuku running in ADB mode are required for hinge measurements. The Samsung assets are not bundled. Shizuku normally needs restarting after a phone reboot. The in-app **Trouble with Shizuku/Fold shutting off?** section covers background operation and offline troubleshooting.

## What Duo Fold Live does

**Your actual screen becomes part of the folding animation.** Duo follows the hinge as you move it, projecting your content through glass, reflections, blur, and a softened display handoff. The default path uses live content, so supported content can keep moving behind the effect.

### Four animation styles

- **Windowed Glass:** the default, iPhone Duo-inspired experience with a live cover preview, reflected left side, progressive glass, and a black fade around the screen switch.
- **Duo Classic:** glass over the inner screen’s own content with a black reveal, without the mirrored cover preview.
- **Fold-Only:** Windowed Glass when closing; normal behavior when opening.
- **Unfold-Only:** Windowed Glass when opening; normal behavior when closing.

### A cover-screen preview inside the inner display

- **Clean right-side preview:** see the cover content without its animation layered into the mirror. The right preview is committed before the left reflection starts.
- **Two left-side appearances:** **Frosted Reflection** is the default blurred, horizontally mirrored image. **Animated Reflection** mirrors the cover animation for a more literal glass-window effect. Select either in Advanced settings.
- **Soft center blending:** blur feathers across the fold into the right side during folding and unfolding. Adjust its position from 0–15% of the inner display width beyond the fold; the default is 7%.
- **Screen-size-aware alignment:** projection follows the cover’s proportions to keep its corners anchored, rather than using a fixed height for one phone.

### Tune the look and motion

- **Motion smoothness, blur amount, and glass strength** have separate controls and reset buttons. Defaults: 30 ms, 30%, and 50% respectively.
- **Black fade smoothing and gradualness** let you shape the transition around Samsung’s display switch.
- **Manual live-content targets:** 120 FPS experimental by default, or 60 FPS for lower GPU cost. No automatic switching based on the display’s reported refresh rate. These are targets, not guaranteed frame rates.
- **Three anti-aliasing methods** in Advanced: **Lightweight Texture Filtering** (default), **Edge-Adaptive Smoothing**, and **4× Supersampling**. AA can be disabled or adjusted within a narrow strength range, independently of blur and glass strength. The two heavier methods remain experimental.
- **Optional full-resolution glass** for users who prefer more rendering detail; half-resolution glass is the baseline to reduce GPU work.
- **Adjustable hinge thresholds:** choose when the effect treats the phone as fully open or closed. Defaults: 172° open and 2° closed.

### Your wallpaper, your launcher, your settings

- **Custom photo wallpaper:** choose a photo, preview its folded and unfolded crops, and keep it saved across compatible app updates.
- **Works over One UI and ordinary apps** without replacing your launcher.
- **Temporary orientation locking during transitions**, followed by release, plus an Advanced **Repair auto-rotate** button for recovery.
- **Guided setup and connection recovery:** wallpaper checks, Shizuku authorization, accessibility guidance, and reconnect controls are built in.
- **Keep Awake support** is enabled and checked in the background, but remains unreliable on some folding transitions; see Known bugs.

### Experiments and useful diagnostics

- **Optional dual-screen screenshot handoff** under Advanced → Developer settings, for experimenting with a held image instead of the default live handoff. Off by default.
- **Copyable status reports** include angle-feed latency, measured capture throughput, renderer submission rates, preview/handoff events, AA settings, and 24-hour app-process health samples. Separate wallpaper reports help investigate unlock flashes.
- **Free and open source:** all features are available without donating. Optional support reminders can be snoozed or permanently dismissed.

Actual performance depends on the device, firmware, content, and selected effects. Fades soften Samsung’s display-switch blackout; the underlying panel transition remains. The mirrored preview does not run two independent apps on the two panels.

## Setup recovery in 3.0.3

Installs that previously skipped onboarding without verifying wallpaper setup reopen the setup wizard after updating. Complete wallpaper setup, overlay access, and accessibility. If angles still do not arrive, the main screen offers **Repair wallpaper & check permissions** and a connection report. Shizuku authorization alone does not verify live angles.

[3.0.3 fixes](docs/3.0.3-CHANGELOG.md). Regional and Ultra zero-angle failures still need affected-device verification.

## Known bugs

1. **Black screen when switching between folded and unfolded displays.** This is a Samsung-side hardware limitation, not an issue with the app. We’re working on workarounds; for now, Duo uses a black fade transition to smooth the handoff in both directions.
2. Unlocking directly onto Home may briefly reveal the underlying live wallpaper before the custom photo. The latest release improves this, but occasional flashes remain.
3. **Keep Awake is unreliable when closing the phone, including from Home.** The setting can read as enabled even when the phone sleeps. Further investigation is deferred.
4. Occasionally part of the unfolding fade does not trigger correctly, so the transition may look choppy.

## Work in progress

- Refining the experimental screenshot handoff to more closely mimic iPhone Duo’s held-image animation, alongside the default live-content handoff.
- Further workarounds for Samsung’s display-switch blackout and smoother fold/unfold handoffs.
- More consistent custom wallpaper visibility during unlock and display changes.
- Refinements to animation timing, responsiveness, and visual consistency.
- Better setup recovery, compatibility checks, and diagnostics across supported devices.

These are active areas of development, not promised release dates.

## Troubleshooting and reports

Use **Trouble with Shizuku/Fold shutting off?** beneath the animation options first. Check that Shizuku is running and authorized and Duo’s accessibility service is enabled.

For an issue, include your app version, phone model, Android/One UI version, fold direction, and steps to reproduce. Attach **Copy status report**; for wallpaper issues also use **Copy background report** in the wallpaper menu. A short recording helps. Review reports for personal information before sharing.

All future release announcements and updates will be on GitHub.

## Support the Project

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
