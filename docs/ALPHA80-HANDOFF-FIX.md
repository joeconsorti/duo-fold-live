# Alpha.80: fast-close animation and remap coverage candidate

Based on the exact alpha.79 reconstructed source, commit 460be60ee8a326619e0cdded157b59365454c7b5. Test application version 1333, 3.5.9-alpha.80-reconstructed-local. This is a candidate fix, not an optically verified blackout cure.

## Report evidence

The user observed two severe blackouts and the final cover animation failing. The report contains four recent Home transitions; software records cannot uniquely identify which two correspond to those visual observations. Process totals (101 OFF transitions, 70 both-OFF episodes) include earlier/idle events and are not a count of these failures.

| Observation | Evidence in alpha.79 |
| --- | --- |
| Opening remap power interruption | Both displays report OFF around 509690032–509690067; ON callbacks follow at 509690152–509690153. These are sampled/callback times, not optical duration. |
| Final closing remap power interruption | Both physical panels sampled OFF at 509693978 and ON at 509694036; cover OFF observation started at 509693936. Device remains interactive. |
| Inconsistent remap geometry | At 509693978 the cover physical identity on D0 is paired with 2448×1848 inner geometry; the correct cover geometry is 1248×1972. |
| Delayed native cover content | At 509694567 raw hinge is 28°, submitted cover pose approximately 57.44°, nativeImage=false, maximum moving gap 166 ms. Valid Home capture arrives at 509694724; readiness follows at 509694758. |
| Final release | HANDOFF_COMPLETE at 509694958 near 6–9°; endpoint at 509695223; immediate Home bypass; last committed cover pose about 5.37° when raw is zero. |

The 17 ms buffer starvation recovered without a fence timeout. No sleep/wake request or thermal stop explains the final close. The older ChatGPT/camera crash entries do not establish a Duo crash.

## Changes

- Independent-buffer smoothing now accounts for up to 250 ms of actual elapsed time after a remap, instead of discarding all time above 50 ms. Remaining angular lag is limited to one quarter of the travel remaining toward the endpoint. It is causal, stays between the previous pose and the current hinge target, preserves reversals, and does not extend endpoint ownership. Legacy rendering keeps its existing smoothing function.
- Transient Home pre/post-draw guard and faded-content rejections retry after 50 ms during the first 1200 ms of each capture route. Other failures retain normal backoff. Accessibility-provided retry delays still take precedence. No content, secure-window, identity, geometry, or capture-readiness guard is relaxed.
- Physical layer-stack exchanges require both identities to match their saved dimensions and rotation. A second pair read immediately before an exchange rejects a changing mapping. Invalid observations retain prior coverage and revoke readiness; they do not hide/detach the roots or create another state request. The second pair is not read on ordinary unchanged ticks.
- Diagnostics distinguish native-texture frame counts and first native-texture commit from total animation frames, record maximum submitted angular lag, and preserve the last successful readiness observation separately from current release gates.

Immediate Home endpoint release, bounded close power tail, native Home sizing, optional passive brightness, no-mirror architecture, lock/disable/lease cleanup, physical CLOSED veto, and the pause behavior remain in force. No new power override or wake path was added.

## Limits and validation

The system's OFF interval during primary remapping remains outside what an image layer can cover. AOSP LogicalDisplayMapper explicitly turns transitioning displays off before applying a different layout; this supports the distinction but does not prove Samsung firmware's exact implementation: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/LogicalDisplayMapper.java

Deterministic tests replay the final-close angle sequence and a 166 ms gap, check both endpoint directions and reversal bounds, transient retry budgets, and mixed/changing mapping rejection. Validation before packaging: 685 unit tests in 126 suites passed with no failures/errors/skips; 768 controller checks, 30 scene mapping/cleanup checks, 34 brightness checks, 60 task checks, 14 power checks, the shield cleanup harness, and 25 compiled buffer-lifecycle checks passed. These use fake Android/compositor environments, not a handset. Final lint, clean-source build and signature results are recorded in the accompanying delivery manifest.

On-device acceptance remains: repeat slow and fast open/close on Home, confirm the cover animation tracks the hinge through the final degrees, check both panels for black flashes, verify rotation after cleanup, and retain a diagnostic ZIP if either symptom remains. A camera recording of the panels is necessary to measure visible blackout duration; software commits alone cannot do so.

## Recovery

The complete source and Git bundle accompany the APK. The separately retained test signing backup is `Duo-Fold-Live-local-signing-backup.zip` (Library identity libfile_7a7964ae2ed48191a617f5fa2810f29a). It is excluded from the source archive. Expected test certificate SHA-256: d4d1b23883a2ee78294eb2c074122e611ad93e52cf1120a15f3a3e3a75e04af9. Build dependencies: JDK 17, Gradle 8.13, SDK 36, Android build-tools 35.0.0. Do not generate a substitute key for an in-place update.
