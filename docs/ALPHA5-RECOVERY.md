# Alpha.5 reconstruction and build ownership

This is a new reconstruction from the published alpha.1 source, not a byte-for-byte
recovery of the alpha.4 source removed during workspace maintenance.

Preserves the baseline saved-wallpaper recovery and explicit saved settings.
Restores public Display.getRealSize photo geometry; refreshes native photo routing
on display changes and replaces old/new child surfaces in one transaction.
Preserves full error causes for native fallback diagnosis.

Keep-awake now uses a dedicated short-lived Shizuku service, independent of glass
capture and hinge reader IPC. Commands target the calling Android user, have
2-second process deadlines, and verify setting readback. A 10-second client
connection deadline clears pending state and allows retry. Default ON and saved
OFF choices are preserved. This cannot promise Samsung Home behavior without a
phone report.

Advanced has an explicitly armed, nonpersistent fixed-mapping test. Close fully
after arming, then begin opening below 80 degrees within 30 seconds. The test holds
the already-owned cover-primary concurrent request for at most 20 seconds,
skipping the normal 98-degree handoff and full-open release. It ends on close,
lock/disable, stale angle, canceled request, lost owned request, or deadline.
Repeated presses cannot extend the deadline. Existing heartbeat expiry also
releases display ownership. Read-only display-state/physical-ID samples appear
in Copy status report; sampled ON is not proof of continuous illumination.

This is a diagnostic experiment. Inner Home, input and navigation migration remain
unresolved. The exit remap can flash; assess the ACTIVE interval separately.
No forced physical-panel power writes are introduced.

Build/sign/publish is owned by .github/workflows/android.yml on GitHub Actions.
It runs release tests and lint, builds, signs from existing repository secrets,
verifies the APK and publishes a prerelease. Source version is 1.8.1-alpha.5;
Actions assigns versionCode 1000 + workflow run number. Keep this workflow's run
number sequence for future updates. Do not deliver a locally versioned lower-code
APK as an upgrade after installing a CI build.

Permanent signer is the NEW alpha.4 key, certificate SHA-256:
b73c9f53f4500766d94e42dcd7f965d0988a8f9888f0be85243e0c031eacecdc
The private key and passwords must never enter Git or public artifacts.

Handset test: update the new-signature alpha.4 without uninstalling; verify saved
wallpaper/settings; verify keep-awake report; arm the continuity test, close then
unfold, and send Copy status report along with whether ACTIVE flashed. A working
APK/build does not establish a blackout fix.
