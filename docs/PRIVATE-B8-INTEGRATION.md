# B8 integration checkpoint

The user authorized public pre-release delivery. Alpha.2 embedded the manual Lab and did not satisfy the requested integration. Alpha.3 replaces its Developer settings entry with an off-by-default automatic **Live B8 handoff (experimental)** toggle. See `3.5.9-alpha.3-CHANGELOG.md` for setup and limits.

`LiveB8Controller` runs in the existing Shizuku angle helper on a separate serialized worker. `AngleReader` supplies live hinge samples, enable state and settings; it suspends competing legacy handoff/preview layers while B8 owns the route. The primary glass renderer remains active. No lab countdown or manual app selection is involved.

`LiveB8Power` owns concurrent-state requests and renewable ON overrides. `LiveB8Task` reuses the tested exclusive fullscreen and corroborated activity-alias guards. The original `LiveWindowBridge` and `PhysicalBrightnessHold` implementations are called directly. `LiveB8Policy` carries timing/hinge decisions; lifecycle tests execute the real controller with a deterministic fake platform.

`B8-BASELINE-SHA256.json` records the original 20 engine hashes. `tools/check_b8_baseline.py` verifies the allowed package relocations and portable guard/recovery tests. Set ANDROID_HOME for the Android core compile. `tools/check_live_b8.py` checks the new live controller separately. Both run in signed-release CI.

The standalone Lab 0.5.11 checkpoint and main/stable are untouched. The source retains alpha.1's stale-angle fix. The public pre-release uses the existing Duo signing workflow; no signing secrets are copied into source. Occasional flash tuning and the historical mirror layer-cycle failure are not claimed fixed. Automatic integration requires handset verification.


Alpha.4 follows a handset report of wallpaper shields, a brightness-source timeout, severe subjective warmth and a restart. Live B8 now calls `LiveB8Bridge` and `LiveB8Brightness`: these retain mirror construction and physical brightness writes but add committed cleanup and pre-shield-only sampling. Original Lab files remain byte-for-byte checkpointed. See `3.5.9-alpha.4-CHANGELOG.md`. The crash cause is unconfirmed; do not describe this as a proven reboot fix.
