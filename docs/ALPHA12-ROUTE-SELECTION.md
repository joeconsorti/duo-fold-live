# Alpha 12 local: unambiguous coordinated screenshot selection

Alpha 11 report changed from route=normal at 160753501 to route=screenshot at 160758115, before the first motion. B8 was inactive; legacy incoming live capture and black endpoint fade ran. The UI still exposed Screenshot Mode, which writes dual=true and bypasses B8. The one-time preset did not prevent that selection.

This debug test now replaces the two-mode picker with a Coordinated Screenshot Test card, guards the shared selection handler, and hides conflicting B8/legacy-route toggles. A new one-time preset restores the intended route after installation. Enable animation still controls running; emergency RuntimeControls.stop remains unchanged. A restore-settings button is available after stopping.

No rendering, power, brightness, or task-routing algorithms changed in this build. This corrects selection and does not prove thermal or optical continuity. The posted 10-25 compositor crash is newer than the earlier 10-24 crash, but this report does not establish its cause or associate it with the current folds.
