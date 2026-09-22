# Approved default: Windowed Glass

The user approved 1.9.0-alpha.2 (commit 77e5e978aa01905dc2a6ca806a203f43613b6806).
Windowed Glass is that animation renamed, and is the default for the 2.0 line and future updates unless the user explicitly requests otherwise. Preserve existing saved fade/glass preferences. Do not silently migrate users to a different animation.

Keep the glass shader, HandoffFade, HandoffFadePolicy, ClosingMirrorFadePolicy, FadeSettings, PreviewExpansion, and mirror geometry intact for this default. New style options must gate their own behavior without changing this path. A rare flash is acknowledged, not authorization for a broad animation rewrite.

Duo Classic means the native inner animation with no cover mirror / fake frosted left preview. This is distinct from the legacy Classic Glass shader, which is buried in Advanced. Fold-Only and Unfold-Only mean Windowed Glass restricted to the selected motion direction.
