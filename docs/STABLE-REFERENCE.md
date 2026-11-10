# Stable rollback reference

**3.5.9-alpha.107-reconstructed-local (versionCode 1360)** is the latest user-approved stable reference for the DUO FOLD LIVE - Z-Fold 8 Animation project, designated on **2026-11-09**.

The exact tested code commit is `623561ad49f205a5e144dc31361e77520cfec515`, bookmarked by the annotated tag `stable-reference/3.5.9-alpha.107`. The accompanying reference-record commit changes documentation only. This is an internal rollback checkpoint, not a new binary or public release.

Joe reported smooth folds and unfolds, no noticeable heat, no inner-display glitches, and no crashes so far on SM-F971U / Android 17. The supplied alpha.107 health samples retain the same app PID across approximately 15 hours. The latest complete closing session reports handoff completion, successful endpoint verification, zero buffer misses/fence timeouts on both renderers, sampled thermal status 0, and battery temperature 25.4 degrees C. The pasted alpha.106 crash/recovery sections are historical and are not alpha.107 crash reports.

`STABLE_REFERENCE.json` records the exact APK and source-archive checksums, original persistent file identities, and the settings visible in the user's diagnostic report. It records user-observed handset acceptance separately from build-time validation. Software timings and sampled state do not measure visible pixels. The settings record is not an exported Android preferences file.

## Restore this baseline

For the exact tested binary, retrieve the original alpha.107 APK listed in `STABLE_REFERENCE.json` (or the byte-identical copy in the stable rollback archive) and verify its SHA-256. Use the original binary rather than a newly compiled substitute when comparing behavior. The matching original source archive and build manifest are preserved unchanged.

For source work, start a separate branch/worktree from `stable-reference/3.5.9-alpha.107`, or from the exact commit above. The rollback archive also includes a complete Git bundle with that tag and this documentation. Keep subsequent experiments under a new version; retain this checkpoint until the user explicitly approves a replacement reference.

Android may reject an older versionCode over a newer installed build. Choose the appropriate rollback installation method for the device at that time; do not assume installation will preserve settings or uninstall automatically. Reapply the recorded settings as needed.

The historical reconstruction target remains alpha.20; it is separate from the latest tested rollback reference, alpha.107.
