# Alpha.12: independent display diagnostics

Alpha.11 report: restoration was locally rejected because fold-awake verification
owned the shared client busy flag. The one-minute recovery backoff plus the
restoration prerequisite prevented API inspection during the short test.

Display operations now use a distinct Shizuku component, process suffix, worker
and busy state. Their completion/unbind cannot terminate the keep-awake helper.
Read-only API inspection runs independently of display availability, continuity
state and pending restoration. Its result is retained in a dedicated report
field, outside the bounded lifecycle event history. Transient busy recovery
uses a short retry. Existing physical-panel restoration journal remains intact.

This build fixes diagnostic scheduling, not Samsung inner Home/taskbar routing.
After installing, wait 15 seconds with Shizuku connected and copy the connection
report. Folding is not required to enumerate APIs.
