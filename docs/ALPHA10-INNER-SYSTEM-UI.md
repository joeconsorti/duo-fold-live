# Alpha.10: temporary inner system UI test

Based on alpha.8 report: native Home root really moved to inner display 1,
interactive content and animation worked, but hasNavigationBar=false and
shouldShowSystemDecors=false. The app cannot claim a native Samsung inner Home
profile merely because a task moved. Source and destination task configurations
are now logged to distinguish geometry from Samsung's device-specific profile.

The test asks the authenticated WindowManager API to enable system decorations
on the built-in inner display during native continuity. It does not change
physical display mapping, turn either panel off, force desktop mode, alter
launcher data, spoof hinge state, bypass keyguard, or change input security.
Navigation/taskbar and Samsung's separate inner Home layout remain subject to
OEM support; enabling decorations is not a guarantee either will appear.

Read the original effective setting, durably save a restoration journal in app
preferences BEFORE applying, then request the change and log immediate and later
readbacks. Resolve the same physical unique ID for restoration after fold/Stop/
timeout. Foreground supervision retries restoration if access is unavailable;
on process restart an old journal is restored before any new experiment.
Force-stop can delay restoration until reopening Duo. Only a successful remote
restoration removes the journal. No defaults are guessed on read failure.

All alpha.9 always-on keep-awake changes are retained. New version code 1009.

Reference: https://source.android.com/docs/core/display/multi_display/system-decorations
Android documents that secondary display decorations and launcher support depend
on system components; the testing setter may not create missing windows live.
