
## Fold Power Lab 0.3.0 — configurable mid-fold experiments

Install over 0.2.0. Duo stable and Duo animation source are unchanged.

- Two 20-second presets: mid-fold switching baseline and mid-fold switching with both logical displays held ON by Samsung token overrides.
- Both start with concurrent inner-primary, settle (then apply ON tokens in the override preset), then require two distinct physical displays sampled ON/committed ON and the requested device state. A failed readiness gate aborts rather than presenting an invalid comparison. Hold the phone near 90 degrees; do not fold it during these tests.
- Custom recipes: 10–30 second duration, initial primary/unchanged, optional alternation, 1–5 second settling and switch spacing, 1–12 switch cap, D0/D1/both/no ON override, per-display/global token API, apply before initial request or after settling, renewal once or every 250–1000 ms, partial/dim/bright/full/no wake lock, default/cancel-on-base-change device-state flags, stop or up to three cancellation retries, 10–100 ms sampling, optional switch snapshots.
- Recipes save locally, copy/import as a versioned code, and travel with each report. No arbitrary shell commands or untyped Binder transaction numbers.
- Request generations distinguish superseded callbacks from unexpected current-request cancellation. Calls, retries, rejected options, cleanup, exact arguments and sample gaps are logged. Reports include OFF-entry counts by physical ID, plus existing Perfetto/logcat/before-after dumps/API inventory.
- Default custom cancellation policy stops the experiment. Legacy buttons retain their single-request, no-reassertion behavior. An ON/wake option can fail while the rest of the recipe runs; inspect the action log before attributing outcomes to that option.
- Explicit token cleanup at the configured deadline; a separate process-death fallback five seconds later if cleanup fails/blocks. Android scheduling/suspend can delay timers. Watchdog death can prevent report export and requires reconnecting Shizuku.

### First comparison

1. Disable Duo Fold Live and other override tools; authorize/connect Shizuku.
2. Hold the hinge around 90 degrees. Run **Mid-fold switching — baseline** and keep it still until the recording finishes.
3. Save the ZIP. Repeat **Mid-fold switching — both ON**, holding the same angle/orientation and using the same foreground app.
4. Send both ZIPs plus which panel visibly flashed. The initial establishment of concurrent mode and final cleanup are separate from the four marked switches.

### Evidence limits and experimental API semantics

Samsung override calls are typed, reflected methods discovered on this device. Per-display arguments are `(token, 2, logicalId, 10000)`; global arguments are `(token, 2, 10000)`. The meaning of the last OEM integer is **unverified**; it is not treated as a proven timeout. Releases use state 0 and final argument -1. Global scope/effect is experimental. App timers, token release, and the worker watchdog bound the experiment independently. Unsupported methods and permission rejections are recorded.

Both-ON gating and committedState are software observations, not optical measurements. Logical display IDs can remap; samples include physical IDs. An absent logical display is not counted as OFF. The public cancellation callback carries no reason; surrounding device-state and power logs provide context but may still not identify the cause. Optional dumps and faster sampling add overhead. No root, persistent settings, physical panel power-mode commands, forced sleep, or speculative raw Binder codes are used.
