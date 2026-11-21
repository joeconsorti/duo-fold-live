# DuofoldX — Batch 0 source audit and setup

**Baseline:** Duo Fold Live v3.6.0  
**Upstream commit:** `bcbc560ab8edd6b02c82535ec1865589378719f7`  
**Fork branch:** `duofoldx/bootstrap`  
**Target:** Samsung Galaxy Z Fold 8 SM-F976B, Android 17, One UI 9.0  
**Build mode:** non-root, debug-only

## Baseline architecture

- Android Gradle project using Kotlin, Java 17 and Jetpack Compose.
- Hinge tracking and privileged display integration through Shizuku.
- AGSL-based glass rendering and Android graphics buffers.
- Cover/inner display handoff controllers, transition state policies and frame smoothing.
- Thermal-aware adaptive rendering resolution and software frame telemetry.
- Extensive JVM policy tests plus portable B8/shader regression checks.

## Key findings

1. The device compatibility layer recognizes the SM-F976 regional family and requires Android SDK 37 (Android 17). Recognition is not firmware/API verification.
2. The legacy `isFold8` helper matched SM-F971 only. The setup commit extends it to SM-F976 and adds an explicit SM-F976B regression test.
3. Existing handoff logic includes bounded release delays and fades, but the physical Samsung mid-fold display handoff interval is not proven eliminated.
4. Existing FPS reporting measures software submissions/callbacks, not optical panel refresh or independently measured visible frame rate.
5. Screen capture and overlay behavior require explicit consent and careful secure-content exclusions. Diagnostic exports may contain visible app information.
6. The upstream workflow automatically signed and published releases on push. It is replaced on this fork with read-only, test-only CI.

## Initial project decisions

- Application ID: `com.duofoldx.app`; debug application ID: `com.duofoldx.app.debug`.
- Internal Kotlin/Java namespace remains `org.duofold.live` during the first identity migration to avoid a risky package-wide refactor.
- The fork keeps the upstream MIT license and all third-party notices.
- No root, bootloader unlock, firmware flash, system partition changes or System UI replacement.
- No release signing, release upload, GitHub Release or Play Store publication.
- CI runs existing portable guards, unit tests, lint and a debug APK build only.

## Validation still required

- Build and test results from GitHub Actions.
- Installation on the user's actual SM-F976B.
- Shizuku connection and angle-reader verification after reboot.
- Inner/cover panel identity and rotation verification.
- Fold/unfold visual capture, black-frame timing and measured presentation cadence.
- Thermal behavior over repeated transitions.

Software-only checks are not evidence of optical panel behavior. Do not label this target device verified until the above handset tests are complete.
