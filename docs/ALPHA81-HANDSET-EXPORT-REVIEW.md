# Alpha.81 handset export review

Evidence: user-supplied `Duo-composition-1793583457408.zip`, SM-F971U / Android 17,
build `3.5.9-alpha.81-reconstructed-local`. This archive is diagnostic-only: four
entries, no armed image captures. Its timeline retained 4,096 rows and dropped
11,556 older rows. Correlation uses observed elapsed times, not receipt order.

## Export defect and correction

The existing exporter included app timeline/runtime data but never copied the
shell session directory. Neither the archive nor runtime text contains
`physical-brightness-hold.txt`, `brightness-source-prepare.txt`, or
`brightness-source-refreshed.txt`. Asking for this export to establish ongoing
brightness writes was therefore insufficient. Repeating the same export on the
same build cannot supply that evidence.

The source correction adds an authenticated, read-only capture-binder operation
for an archive of existing logs from the latest six shell-UID Duo sessions.
Only nine named log files are allowed. Ordinary logs have a 64 KiB tail limit;
brightness source logs have a 256 KiB tail limit. No fresh shell command, capture,
display request, or brightness write occurs. File/directory symlinks are rejected.
A file descriptor carries the ZIP, avoiding a multi-megabyte Binder payload; the
temporary file is unlinked after opening. The app embeds `shell-evidence.zip`
and a status entry; failure leaves the app timeline export available and explicitly
marks shell evidence unavailable. Session logs may predate the exporting build.

The runtime export also includes rotation-hold state. The timeline now records
changes to the hold's status text; previously only its timestamped release trace
was imported, silently omitting acquisition/failure messages. New status rows are
labelled with receipt timing, not an invented change timestamp.

## Distinct observed failures

* Session 519625664 began with landscape renderers (cover 1972x1248, inner
  1848x2448). Display rotation returned to portrait during the session. The
  renderer sizes stayed fixed. `NATIVE_FALLBACK Prepared destination commits
  stalled` appeared at 519626827. Final cover committed angle was 75.7306 while
  the raw angle reached 174; the inner never committed its own native image.
* Session 519638134 began with portrait renderers. The cover changed to rotation
  1 and the inner to rotation 3 during the handoff, then back to 0 near closure.
  A valid cover Home capture was delivered at 519639796, stamped 519639516,
  during the landscape interval. The cover ended with zero native texture frames.
  Primary verification was restarted as geometry changed. The direct renderer
  stores its initial dimensions/rotation and `LiveB8Scene.matchesGeometry`
  requires those same values: the code cannot adopt a sustained rotated layout.
  The archive does not establish why rotation hold acquisition was delayed.
* Session 519646660 completed native cover handoff at 519648465 (1,804 ms from
  controller start), after two `HOME_CONTENT_FADED` capture retries. One buffer
  shortage recovered in 16 ms. At the endpoint, `coverBrightness=false` confirms
  no cover brightness tail was retained; it does not identify why. The writer's
  two-second bound is relevant, but its actual stop/error log is absent.
* During opening primary remap, both physical displays were sampled OFF at
  519643472. During the last closing remap the cover had an OFF observation and
  a later ON observation 64 ms apart. These are software samples, not optical
  blackout durations. Process totals also include normal idle/off periods.

The export correction changes diagnostic coverage only. It does not repair the
rotation/renderer mismatch or establish a way to prevent firmware display power
transitions during native Home remapping. No new APK or optical-success claim is
part of this correction.

## Verification

Five focused tests passed, exercising unarmed brightness-log collection, explicit missing-file
status, numeric session retention, UID/file allowlists, symlink rejection, byte
limits/offsets, and empty history. Android Java/Kotlin compilation passed for the
Binder file-descriptor and archive integration. These are host checks; device
export and panel behavior remain unverified.
