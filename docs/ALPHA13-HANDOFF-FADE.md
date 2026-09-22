# Alpha.13: angle-driven handoff fade

User priority: visually soften the existing native display handoff while keeping
the present animation exactly as implemented. Alternative Samsung switching
mechanisms remain a separate research task. This is an alpha for visual tuning,
not a declaration that native panel blackout has been eliminated.

Implementation: two owned black SurfaceControl color layers, one per exposed
logical display stack, above existing preview/animation surfaces. Surfaces are
excluded from capture to avoid feeding the fade back into the glass effect.
No shader, animation geometry, handoff threshold or display-power policy changes.

Normal cover-preview mode only, enabled with the existing animation. Continuity
test and dual-display mode disable the fade. On cover: smoothstep opacity from
88 to 98 degrees. On inner: smoothstep from 104 down to 94 degrees. Retreating
before a switch immediately reverses opacity. Current primary geometry change
starts destination masking at full black, minimum 50 ms, followed by a 180 ms
smoothstep reveal after an ON state and a new primary overlay draw notification.
Draw submission is software evidence, not an optical/display-present guarantee.

The helper retains the masking layers during panel OFF events; it does not
follow the accessibility window teardown. Direction reversal re-arms the mask;
remaining at the arrival angle does not start another fade. No-ready timeout
900 ms starts a reveal; a missing switch at fully black fails open after 1200 ms.
Stale lease, lock, disable and helper shutdown remove the owned surfaces.

Validation covers approach/reversal, both switch directions, stale/wrong-panel
draws, destination reveal, no immediate re-dimming, missing draw/switch and
invalid input. Device testing is still required for physical panel timing,
compositor permission/API compatibility and subjective fade speed.

Handset check: leave Continuity test OFF. Fold/unfold normally at slow and fast
speeds; pause and reverse near the switch. Report whether fade-out begins too
early/late, whether destination first appears black, and fade-in speed. Copy
connection report if an abrupt flash remains; it includes Handoff fade status.
