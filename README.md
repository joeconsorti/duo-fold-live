![DuoFold Live](docs/assets/duofold-live-logo.svg)

# Duo Fold Live · 3.5.5

**Current official release: 3.5.5.** Occasional unlock wallpaper flashing remains a known issue. For Keep Awake, disable Samsung’s “Lock when folded”; see troubleshooting. [Rollback baseline and release history](KNOWN_WORKING_BUILD.md).

## 📺 Step-by-step installation tutorial
**New here? [Watch the full setup walkthrough on YouTube](https://www.youtube.com/watch?v=8Ucm7ceBDN4).**

Created by a Duo Fold Live user—not by me. Huge thanks for putting this together!

An iPhone Duo-inspired folding animation for the **Samsung Galaxy Z Fold 8**, with windowed glass, live cover previews, and smooth black fades around Samsung’s display handoff. Free and open source. Runs over One UI and ordinary apps without replacing your launcher.

**[Download 3.5.7](https://github.com/joeconsorti/duo-fold-live/releases/latest)** · [3.5.7 release notes](https://github.com/joeconsorti/duo-fold-live/releases/tag/v3.5.7) · [Report an issue](https://github.com/joeconsorti/duo-fold-live/issues)

**Only tested on the Z Fold 8 (SM-F971U).** Fold 8 Ultra, Fold 7, and other devices are unverified. Do not expect compatibility. Model recognition includes regional SM-F971, SM-F976, and SM-F966 families. Unknown models display a warning and may proceed. Android 17 and the existing Samsung wallpaper/API checks are still required. Recognition is not verified compatibility.

## What's new in 3.5.5

- **Smoother cover handoff:** a prepared black layer fades away over 300 ms after fresh cover content commits, with confirmed cleanup and bounded retries.
- **Less repeated runtime work:** cached settings, coalesced preview refreshes, stale-capture rejection, and lighter diagnostic updates preserve the existing animation settings. APK shrinking remains disabled.
- **New launcher icon:** matches the app's mint and periwinkle branding.
- **Optional support reminders:** first eligible after 24 hours of working use, then 3 days after delivery, then weekly. Snooze for 7 days, pause for 21 days after opening Ko-fi, or stop permanently with “I already supported” or “Don't ask again.” Existing opt-outs remain respected.

## What's new in 3.5.4

- **Cover stays on at full closure:** adopts the user-tested Android-managed release behavior with a bounded fallback. The full-close blackout disappeared in SM-F971U testing; the separate mid-fold display switch remains.
- **Smooth / Fast motion presets:** Smooth selects 120 ms and is the new default; Fast selects 30 ms. Both update the slider, which remains available for custom values. Reset selects Smooth. The new default is applied once on upgrade.
- **Landscape previews fixed:** clean and reflected cover content follows the physical hinge and fits the current orientation.
- **Simpler controls:** remove the obsolete closing cover power-pulse experiment.

## What's new in 3.5.3 — since stable 3.5.2

- **Redesigned Duo Classic:** gentler cover stretch, folding toward the hinge with the reverse motion on unfolding, stronger blur, and both the clean and mirrored inner-display cover previews.
- **New Plain Fade:** a moving black wipe over screen dimming, with an opaque inner-screen hold before handoff and no cover previews. Tune the cover and inner displays separately: wipe start/end positions, travel timing, softness, darkness, brightness, and dimming timing. Copy your settings to share a preset.
- **Choose the look and behavior independently:** three animation styles, plus **Fold & Unfold**, **Fold-Only**, or **Unfold-Only** for any style. **Live Screen** remains the default Folding Mode.
- **Quick Settings toggle:** add **Duo Fold Live** using Samsung’s Quick Settings panel editor, then tap to switch animations on or off. Long-press opens app settings.
- **Notification controls:** jump directly to Android’s running-notification category to hide the persistent notice, and independently turn optional support notifications off.
- **Wake and connection recovery:** capture warm-up after unlock addresses missing content on the first fold; helper reconnection and timeout handling are more robust.
- **Closed-hinge jitter protection:** filters tiny closed-phone angle excursions to reduce unintended preview/display activation.
- **Clearer diagnostics and testing boundaries:** a shorter primary status report, optional health history, richer handoff/connection traces, and copyable Plain Fade settings. Screenshot Mode is marked coming soon and gated behind a 10-second hold for testing.

Custom wallpaper unlock flashing and Samsung’s mid-fold display-switch blackout remain unresolved; 3.5.4 addresses the separate full-close blackout. The experimental photo-wallpaper test is not a replacement for the working Samsung hinge-reader setup.

## Updating from an older version

**3.5.2 and recent alphas signed with the current permanent key can update in place.** The signing key changed before those builds; older installs using the previous key require one uninstall/reinstall, followed by setup again. Uninstalling removes local settings and the saved custom wallpaper. Recent alphas using the current key can update directly. Future releases using this permanent key will update in place; another signing-key migration is not planned.

## Quick setup

1. Download and install the APK from Releases.
2. Follow the app’s setup guide: start Shizuku/Shizuku+, authorize Duo, and apply the required Samsung fold wallpapers when prompted.
3. Allow the requested overlay/accessibility permissions and enable the animation. If Android blocks accessibility, use App info → ⋮ → Allow restricted settings.
4. Optionally choose a custom wallpaper in the built-in wallpaper menu.

The app guides setup. Samsung’s interactive wallpaper and Shizuku running in ADB mode are required for hinge measurements. The Samsung assets are not bundled. Shizuku normally needs restarting after a phone reboot. The in-app **Trouble with Shizuku/Fold shutting off?** section covers background operation and offline troubleshooting.

## What Duo Fold Live does

**Your actual screen becomes part of the folding animation.** Duo follows the hinge as you move it, projecting your content through glass, reflections, blur, and a softened display handoff. Duo Mode animates app snapshots; Live Screen Mode (Legacy) can keep supported content moving behind the effect.

### Folding Animation Style — alpha.65 reconstructed pre-release

The top selector offers **Duo Mode** (the renamed Screenshot Mode, default) and
**Live Screen Mode (Legacy)**. Both use **Windowed Glass**. Duo is selected once
on install/upgrade; any later explicit mode selection survives restarts.

Duo Mode uses coordinated app snapshots, faster startup and shared gyro controls.
This pre-release also includes workspace wallpaper and pane visibility fixes. See
[alpha.65 release notes](docs/3.5.9-alpha.65-reconstructed-CHANGELOG.md).

### Independent animation behavior

Select **Fold & Unfold** (default), **Fold-Only**, or **Unfold-Only** independently
of Duo Mode / Live Screen Mode (Legacy). Both modes settle to a usable screen after a
1.25-second hold: cover below 90°, inner above 90°, current screen at exactly 90°.
Move 5° to resume. The hold and jitter behavior are unchanged in this candidate.

### A cover-screen preview inside the inner display

Available in Live Screen Mode (Legacy). Duo Mode uses its own coordinated panel snapshots.

- **Clean right-side preview:** see the cover content without its animation layered into the mirror. The right preview is committed before the left reflection starts.
- **Two left-side appearances:** **Frosted Reflection** is the default blurred, horizontally mirrored image. **Animated Reflection** mirrors the cover animation for a more literal glass-window effect. Select either in Advanced settings.
- **Soft center blending:** blur feathers across the fold into the right side during folding and unfolding. Adjust its position from 0–15% of the inner display width beyond the fold; the default is 7%.
- **Screen-size-aware alignment:** projection follows the cover’s proportions to keep its corners anchored, rather than using a fixed height for one phone.

### Tune the look and motion

- **V2 Window Reveal · experimental** is the default projection, with Original available for comparison. Cover early stretch defaults to 90%, end stretch to 125% (80–150%), and vertical compression to 90%. Separate inner controls default to 30% early stretch, 60% end stretch (0–150%), and 40% vertical compression. Both vertical sliders range from 0–200%; 100% retains the original perspective. Startup easing is on and toggleable.

- **Motion smoothness, blur amount, and glass strength** have separate controls and reset buttons. Shared defaults in both modes: Smooth (120 ms), 150% blur, and 40% glass strength. Saved adjustments carry between modes. Fast selects 30 ms; the slider also supports custom smoothness.
- **Black fade smoothing and gradualness** let you shape the transition around Samsung’s display switch.
- **Manual live-content targets:** 120 FPS experimental by default, or 60 FPS for lower GPU cost. No automatic switching based on the display’s reported refresh rate. These are targets, not guaranteed frame rates.
- **Three anti-aliasing methods** in Advanced: **Lightweight Texture Filtering** (default), **Edge-Adaptive Smoothing**, and **4× Supersampling**. AA can be disabled or adjusted within a narrow strength range, independently of blur and glass strength. The two heavier methods remain experimental.
- **Glass / effect resolution:** 25% (default), 50%, 75%, or 100% of each screen dimension, shared between both modes. The default matches the prior screenshot renderer. Advanced 4× Supersampling still doubles the selected dimensions; higher settings increase GPU work.
- **Adjustable hinge thresholds:** choose when the effect treats the phone as fully open or closed. Defaults: 172° open and 2° closed.

### Your wallpaper, your launcher, your settings

- **Custom photo wallpaper:** choose a photo, preview its folded and unfolded crops, and keep it saved across compatible app updates.
- **Works over One UI and ordinary apps** without replacing your launcher.
- **Temporary orientation locking during transitions**, followed by release, plus an Advanced **Repair auto-rotate** button for recovery.
- **Quick Settings tile:** toggle animation from Samsung’s pull-down panel; wallpaper and animation preferences are retained.
- **Notification preferences:** manage the running-notification category and optional support notifications independently.
- **Guided setup and connection recovery:** wallpaper checks, Shizuku authorization, accessibility guidance, and reconnect controls are built in.
- **Keep Awake support** is enabled and checked in the background. Disable Samsung’s **Lock when folded** setting to keep the cover awake when closing from Home; see troubleshooting.

### Experiments and useful diagnostics

- **Duo Mode candidate:** the coordinated screenshot renderer is the default on install/upgrade, with Live Screen Mode (Legacy) still available. Later selections persist.
- **Photo wallpaper hinge test:** an opt-in Developer-settings experiment with restoration controls. Applying the test wallpaper stopped fresh hinge readings in device testing; it does not fix custom wallpaper unlock flashes. Its wallpaper service can also appear in Samsung’s wallpaper picker.
- **Copyable status reports** include angle-feed latency, measured capture throughput, renderer submission rates, preview/handoff events, AA settings, and 24-hour app-process health samples. Separate wallpaper reports help investigate unlock flashes.
- **Free and open source:** all features are available without donating. Optional support reminders can be snoozed or permanently dismissed.

Actual performance depends on the device, firmware, content, and selected effects. Fades soften Samsung’s display-switch blackout; the underlying panel transition remains. The mirrored preview does not run two independent apps on the two panels.

## Setup recovery in 3.0.3

Installs that previously skipped onboarding without verifying wallpaper setup reopen the setup wizard after updating. Complete wallpaper setup, overlay access, and accessibility. If angles still do not arrive, the main screen offers **Repair wallpaper & check permissions** and a connection report. Shizuku authorization alone does not verify live angles.

[3.0.3 fixes](docs/3.0.3-CHANGELOG.md). Regional and Ultra zero-angle failures still need affected-device verification.

## Known bugs

1. **Black screen when switching between folded and unfolded displays.** Samsung’s display switching can include a black interval; eliminating it is not established. We’re working on workarounds; for now, Duo uses a black fade transition to smooth the handoff in both directions.
2. Unlocking directly onto Home may briefly reveal the underlying live wallpaper before the custom photo. The latest release improves this, but occasional flashes remain.
3. Occasionally part of the unfolding fade does not trigger correctly, so the transition may look choppy.

## Work in progress

- Refining the experimental screenshot handoff to more closely mimic iPhone Duo’s held-image animation, alongside the default live-content handoff.
- Further workarounds for Samsung’s display-switch blackout and smoother fold/unfold handoffs.
- More consistent custom wallpaper visibility during unlock and display changes.
- Refinements to animation timing, responsiveness, and visual consistency.
- Better setup recovery, compatibility checks, and diagnostics across supported devices.

These are active areas of development, not promised release dates.

## Troubleshooting and reports

**Phone locks when you fold it, despite Keep Awake?**

1. Open **Samsung Settings** and search for **Lock when folded**.
2. Turn **Lock when folded OFF**.
3. Return to **Home**, then fold the phone to test.

This Samsung setting can override Keep Awake. The project owner confirmed that disabling it resolved closing-from-Home sleep on their device. If it still happens, check Shizuku below and include a status report.


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

The stable 3.5.5 source is tagged **v3.5.5**. Check out that tag to reproduce this release.

```sh
git checkout v3.5.5
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

## Hobby project disclaimer

Duo Fold Live is a free, open-source hobby project, provided “as is” without warranties. Use it at your own risk. To the extent permitted by applicable law, the authors and contributors are not liable for device damage, data loss, malfunctions, or other harm arising from its use. Not affiliated with or endorsed by Samsung. See [LICENSE](LICENSE) for the full license terms.
