# Alpha44 lightweight fold animation

Base: alpha43 reconstructed, ed34e5118184fc591626ca739cb771e51cfe7644.
Version: 3.5.9-alpha.44-reconstructed-local, versionCode 1297.

The supplied Duo-composition-1793159997761.zip identifies alpha43. It contains
capture-status.txt and README.txt, zero current-build image folders. Standard
animation paused composition comparisons; this is a diagnostic export, not a
capture pass or visual proof of freezing.

The supplied current-process sessions show battery temperature 34.5–35.2 C,
sampled thermal states 1–2, and app CPU 37–44% of one core. The earlier helper
session ended with thermal=3. CPU excludes the helper, compositor and GPU.
These samples do not isolate the cause of heat or measure the new build.

Changes:

- Once on upgrade, select lightweight mip blur and set blur strength to 1.5
  (previous default 0.3). Subsequent user adjustments persist. Mip blur reuses
  prepared image levels instead of the normal 25-tap kernel.
- Default glass buffers use quarter width and height, one-sixteenth of native
  pixels: inner 612x462 and cover 312x493 on the supplied device orientation.
  Normal mode retains opt-in full resolution and supersampling controls.
- Coordinated standard animation fades the physical inner-left half between
  105 and 85 degrees. At/below 85 it is black; opening follows the same curve
  in reverse. Canvas clipping excludes that half before backdrop and shader
  work once fully black. The physical right half remains visible. Rotation
  maps the mask with the physical half, not a fixed screen rectangle. This
  affects the coordinated animation, not the native app after cleanup.
- Coalesce queued panel draws without repeatedly postponing them, and cap
  submissions/refresh requests at 60 fps. Each draw consumes the latest angle.
- Sample the unmask clock after display queries. A commit that arrived during
  those queries was previously compared with the tick's earlier timestamp and
  could be rejected as future-dated, prolonging the static shield. True future,
  stale, wrong-session, wrong-panel and missing coverage remain rejected.
- Add bounded UNMASK_WAIT reasons plus maximum draw/post duration, commit
  callback latency and submission gap while motion is pending. These are
  software measurements, not visible frame rates or optical blackout times.

Alpha43's supplied shield ages were 542–1054 ms in the last trace. Temporary
OFF/ON callbacks and task configuration lag also occur. Lower render work and
the clock fix address avoidable cost/waiting; they do not establish that all
visible freezes are eliminated. Native readiness, both-panel coverage, capture
exclusion, bounded cleanup and severe-thermal shutdown remain required.

Validation: deterministic controller/cleanup/brightness harness includes the
clock-race regression; unit tests cover quarter-size buffers, pacing, fade
endpoints/reversal and all four rotations. The delivery build runs the full
Android unit suite and checks the APK's actual manifest version. No connected
handset or optical validation is available in this environment.

Source recovered from the saved alpha43 Git bundle after transient workspace
cleanup. Debug update signer is retained privately, excluded from this archive.
