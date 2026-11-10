# Next release

## 3.5.9-alpha.66 — October 30, 2026

- Owner approved default-on adaptive resolution after reviewing the provisional comparison. Supersedes the earlier conditional proposal: thermal status >=1 caps resolution at 25% immediately for the next animation; status 0 permits 50%; ten continuous minutes at status 0 restore the selected maximum. Renewed pressure restarts recovery. A fall from 2 to 1 remains at 25%.
- Toggle is directly under the resolution slider. Disabling bypasses the cap without overwriting the slider or cooling history. The slider remains the maximum, including manual 25/50/75% settings.
- New-install and reset defaults: resolution 100%, glass strength 50%, blur amount 75%. Existing saved appearance settings remain selected. Use Reset resolution to leave a saved 25% test setting.
- Both coordinated panels use the same resolution sampled at session start, including remaps. Legacy glass chooses resolution at motion start. No renderer replacement in the middle of a coordinated animation.
- Uses Android thermal callbacks plus readback at animation start and cooldown expiry, with monotonic elapsed time and no wake locks. If the app process is killed while recovering, restart conservatively begins a fresh ten-minute recovery after status clears. Service restarts retain the running timer.
- Reports include selected/next-animation/session resolution, thermal cap, recovery timer, transition logs and resolution on every fold-health row. Timing and raster logs are software evidence; device cooling and visible continuity still require handset testing.

### 100% comparison baseline received October 30

- User estimates approximately six fold/unfold cycles; do not assume an exact count. Recent batch is elapsed 333035362 through 333071641, with 15 recorded controller sessions, including a close-to-open reversal without reaching closed at session 333066723. Older health rows are excluded.
- Settings: alpha.65 reconstructed; resolution 100% (inner raster 2448x1848, cover 1248x1972); glass 49.984207%, blur 0.7509594 (~75%); animation smoothing 120 ms; motion smoothing 90 ms; lightweight AA method 0.
- All 15 recent health rows report battery start/peak 313 tenths C (31.3 C). This is reported battery telemetry, not CPU/GPU temperature or proof of no warming.
- Sampled thermal peaks: first 11 recent sessions 0; last four sessions 1 starting at 333063400. Later gate snapshot at 333075336 reports thermal=2. Preserve distinction between per-session sampled peak and subsequent status; do not claim the session sampler recorded 2.
- Recent app CPU: 5543 ms / 28706 ms session duration = 19.31% of one core, range 15.26–25.39% by session. Excludes helper, compositor and GPU.
- Retained recent presentation-ready values 209, 213, 241 ms; endpoint-to-native-reveal submissions 474 ms (opening), 364 ms (opening after reversal), 235 ms (closing). Latest direct-first-present both panels 190 ms (renderer-local timing). Latest final maxMovingGapMs inner 66 / cover 165; maxPresentMs 165 each. Software observations only; not optical response or steady-state frame rates.
- The subsequent report also retains a post-100% sample at 333080242 (about 8.6 seconds after cleanup): thermal=2, battery=32.3 C, unplugged. Do not attribute this historical sample to the 25% batch.

### 25% comparison received October 30 — provisional thermal benefit

- Twelve new controller sessions, 333362995 through final cleanup 333390584, form six complete unfold/close pairs. Older rows through 333069902 repeat the 100% batch and are excluded. Wall span is 27.589 seconds versus 36.279 seconds for the 100% batch; summed controller durations are 23.071 versus 28.706 seconds.
- Resolution is confirmed at 25% per dimension by inner raster 612x462 and cover 312x493. Glass, blur, smoothing and AA settings match the 100% baseline above.
- All twelve new sessions have sampledPeakThermal=0; the current start gate also reports 0. First eleven sessions report battery 31.2 C; the last reports 31.7 C. There is no equivalent post-test battery sample at roughly nine seconds after completion, so these are not matched delayed peak measurements.
- App CPU totals 4217 ms / 23071 ms = 18.28% of one core, versus 19.31% at 100%. This excludes shell helper, compositor and GPU; the small difference does not establish total device power savings.
- Three retained complete presentation-ready entries are 235, 210, 211 ms (mean 219 ms), versus 209, 213, 241 ms (mean 221 ms) at 100%. Endpoint submissions are 264 ms closing, 426 ms opening, 337 ms closing; direction mix differs from the retained 100% entries. Latest direct-first-present is 169 ms; final maxMovingGapMs is inner 52 / cover 168 and maxPresentMs 165 / 167. These are software observations, not optical latency or steady frame times.
- Interpretation: this 25% run has a better observed thermal result and similar startup timing, but resolution's causal benefit is not established. The 25% batch ends around the elapsed duration where 100% first recorded thermal=1; duration, pace, reversal and starting chip temperature are confounders. Similar starting battery temperatures do not establish identical chip temperatures.
- This comparison did not establish a causal thermal benefit. The owner subsequently approved the alpha.66 adaptive experiment above; matched-duration handset testing remains useful.

- User confirmed on 2026-10-13 that the Windowed Glass retreat is fixed in 3.5.5-alpha.2.
- Glass strength defaults to 40% for new setups and Reset glass strength. Existing saved strengths remain selected. UI, renderer, screenshot path, report fallback and initial setup agree.
- Custom wallpaper unlock flash: partial success / largely resolved in 3.5.5-alpha.5. User reports no flashes after the off/on test, with possibly one on the first unlock, versus every unlock before preparation was implemented. Software logs also show a completed atomic Home handoff after re-enabling. Keep wake preparation ON by default in future builds, with the three-second-hold developer override. Defer residual first-unlock / missing-Home-eligibility investigation until after the main priorities; do not claim every edge case is fixed.

Remaining main priorities (owner order, 2026-10-13):
1. Screenshot mode first (owner update 2026-10-13), using its findings to improve standard mid-fold display handoff. Alpha.6 tests live incoming animation captures after exact app verification; Home routing and native endpoint switching remain unresolved.
2. Performance consistency.
3. Quick-close unanimated cover flash, last of the main priorities.

After the main priorities:
4. Intermittent rotation recovery.
5. On-device validation of direct root support.
6. Revisit residual custom wallpaper unlock flash edge cases.


## 3.5.5-alpha.14 — wallpaper studio preview
Independent cover/inner photos and crop controls, shared rendering selection, and an app visual refresh. Existing advanced wallpaper controls retained. Validate on-device before promotion; remap flash remains tabled. See `3.5.5-alpha.14-CHANGELOG.md`.

## Auto-rotate recovery visibility

Move the existing verified Repair auto-rotate action directly below Folding Mode and above Trouble with Shizuku on the main screen, outside advanced settings. Explain that animation must be off and that repair enables auto-rotate on both panels. Preserve automatic journal-based recovery and the one-time legacy repair; do not force auto-rotate on every launch because a user may deliberately lock rotation.

History: 3.0.4-alpha.6 corrected restoration across physical-panel remaps and incomplete cleanup; 3.5.2-alpha.2 added independent reader-exit recovery. These explain known failure paths, not an unverified new report. A new occurrence needs the affected version and rotation-release diagnostics.
