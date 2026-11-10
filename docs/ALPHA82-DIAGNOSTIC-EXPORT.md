# Duo Fold Live alpha.82 — diagnostic export only

Version: `3.5.9-alpha.82-reconstructed-local`, versionCode 1335.
Build requested November 1, 2026. Based on alpha.81, with the diagnostic correction
in commit `0c5eacb` and this version/notes update only.

## Scope

* Save ZIP to Downloads now includes `shell-evidence.zip`, containing bounded
  existing brightness writer/source, refresh, task-route and session logs from
  up to six retained shell sessions.
* `shell-evidence-status.txt` states whether shell collection succeeded. The
  nested archive lists missing files, byte limits, offsets and truncation.
* Runtime state includes rotation-hold state. Timeline entries retain changes
  to rotation-hold status, including errors, labelled with reader receipt time.
* Collection works without arming image captures. It runs on export and does not
  issue fresh captures, power requests or brightness writes.

Animation, rendering, rotation control, brightness timing, display power control,
capture gates and endpoint behavior are unchanged from alpha.81. This is a
diagnostic build, not a claimed blackout fix. Older retained session files are
labelled as potentially historical. No optical panel behavior is verified here.

## Handset check

1. Install this APK over the existing Duo Fold Live Test installation and open it.
2. Keep the same settings. On Home, unfold and close two or three times, including
   a motion that reproduces the blackout. Note whether it happens during the
   switch, near full closure, or both.
3. Immediately use Advanced → Show advanced settings → Developer settings →
   Flashing & system-bar diagnostics → Save ZIP to Downloads.
4. Send the ZIP from Downloads/DuoFoldLive. Arming image captures is unnecessary
   for these writer and rotation logs.

Only six shell sessions are retained, so export promptly. If the helper is
unavailable, the export still contains the app timeline and explicitly reports
the missing shell evidence. A successful software write/commit or capture is
not proof that the physical panel remained lit.

## Build and preservation

Use `tools/build_local_test.py` from a clean committed tree. The delivery includes
the APK, matching source archive with Git history, and hash manifest. Keep the
existing private debug signing key outside every source/archive deliverable.
Host checks and artifact identity/signature verification are recorded in the
delivery manifest. Device export/blackout results require the next handset run.
