# Fold Power Lab 0.3.0

Standalone Android 34+ package `org.duofold.powerlab`, based on Fold Observer 0.1.4. Build with `bash tools/fold-power-lab/build.sh` after installing JDK17 and Android SDK36/build-tools35.0.0. The build runs pure-Java recipe validation tests, compiles the Android application, and packages an unsigned APK. CI signs with the existing release key. No Internet permission; local ZIP exports use the Android document picker.

See [CHANGELOG.md](CHANGELOG.md) for presets, recipe controls, test procedure, cleanup and evidence limits. Disable Duo before overrides. The conflict guard rejects a device state different from base; it cannot detect every other app's power lease. Do not run multiple override tools simultaneously.

The API inventory is broader than callable tests. Unsupported signatures, rejected permissions, missing sensors and capture failures are reported. OEM final integer semantics are unverified; app timers and process-owned token cleanup bound the test, not an assumed OEM timeout. Worker death after cleanup failure can prevent the report from finishing and requires reconnecting.

Recipe format v1 is a comma-separated bounded numeric record: version, durationSeconds, initialPrimary, settleMs, switchSpacingMs, switchLimit, onTarget, onBackend, renewalMs, wakeLock, sampleMs, requestFlags, cancellationPolicy, onOrder, alternate, switchSnapshots. Use the editor to generate/import codes; every custom ZIP contains `recipe.txt`. This format deliberately cannot execute arbitrary commands.
