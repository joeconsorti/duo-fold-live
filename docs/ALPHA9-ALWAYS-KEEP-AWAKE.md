# Alpha.9: keep-awake is an always-on app policy

User explicitly requested hardcoded ON, including overriding older saved OFF.
Persist true at application startup, service start and boot/package-replacement
recovery. Remove the OFF toggle and restore-previous-setting control, and reject
the old restore action in the dedicated setting helper.

Keep the sticky foreground supervisor alive when animation and wallpaper stop.
The notification action stops animation/wallpaper, not keep-awake. Verify actual
Samsung setting every 15 seconds; observe setting changes and screen events for
immediate checks, retry failed helpers after 2 seconds, and recheck on Shizuku
binder/permission restoration. Preserve a recheck arriving during an in-flight
request rather than delaying it behind the normal interval. Writes still have
readback verification and bounded helper/command timeouts.

This preserves the app preference across normal in-place APK updates; clear-data
also defaults to ON on next launch. It cannot guarantee privileged access after
Shizuku stops, a reboot requiring Shizuku restart, force-stop, revoked permission,
or Android restricting service startup. The saved Samsung setting usually
persists without the helper, but repair requires authorized access. No fake
verified status while access is absent. Does not keep the phone awake against
explicit power-button sleep, bypass a lock screen, or eliminate continuity-test
exit panel-power cycles. Alpha.8 continuity changes remain included.

## Latest alpha.8 handset result (preserved for next continuity work)

User confirms functional apps, working animation and no black flash during the
hold, but the inner screen retains the cover Home layout and lacks the inner
taskbar. Cover stays on. Report confirms native overlay attached; Home root 1
moved to display 1, while sampled global focus remains Duo task 11625 on display
0. Inner flags=0x608b, hasNavigationBar=false, shouldShowSystemDecors=false,
getDisplayImePolicy=1. This is an interactive secondary-display experience, not
yet Samsung's native inner-display configuration. Do not revert working overlay
or claim the inner-layout problem is solved. Alpha.9 changes keep-awake only.
