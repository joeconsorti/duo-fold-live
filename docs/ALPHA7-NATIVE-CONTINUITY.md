# 1.8.1-alpha.7: preserve placed content and animate the inner display

Both alpha.6 reports requested app task 11613, then timed out the combined
placement/global-focus check. Cleanup returned the app before the hold ended.
The user saw native full-screen app content momentarily without a black flash,
then only wallpaper. This supports, but does not prove, a focus-only failure.

Alpha.7 records the selected component, both owning roots and global focus.
Placement and focus are separate: a placed task stays on the inner display for
the bounded hold even if global focus remains elsewhere. One explicit focus
request is attempted. An unplaced app gets one explicit root-transfer fallback,
only if that root contains exclusively the selected task. No unrelated task
root is moved. Initial selection uses the focused cover root, avoiding stale
recent-app selection when Home is visible. Root fallback cleanup is ownership
checked. Closing, folding below 94 degrees, Stop, lock, request loss or the
20-second deadline still ends/returns the test as before; exit flash remains.

The native inner presentation is transparent, nonfocusable and nontouchable.
It renders the existing hinge-driven inner shader over the actual task. During
native continuity, glass capture targets display 1 and excludes animation
surfaces. Capture checks keyguard/protected layers and physical identity as
before. The ordinary normal-handoff expansion remains disabled during this
experiment. Native animation starts when placement is observed after 98 degrees;
the pre-transfer cover mirror is unchanged. Home and touch/navigation still
require handset validation. Software placement is not optical verification.

Local version code is 1006, above alpha.6 CI code 1005. CI preserves a source
version code if it is greater than its run-derived code. Same permanent signer.
