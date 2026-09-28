# Display handoff testing master list
Updated 2026-09-28. Research plan only; no new device tests or app changes.

## Objective and evidence
Eliminate visible blackout in both directions while preserving native layout, input, Home/navigation, lock behavior and rotation recovery. Evaluate live and screenshot animation separately.
Prior firmware inspection (FIRMWARE-DISPLAY-FINDINGS.md) found that primary remapping waits for transitioning panels to turn OFF. This establishes a firmware policy, not proof of an immutable panel limitation. A screenshot cannot conceal a genuinely powered-off panel.
Already attempted: outer-primary concurrency, task/root transfer, focus requests, geometry overrides, secondary decorations and prepared replacement buffers. These are not new routes. Returning to native mapping still blacked out.

## Ordered experiments
0. Baseline: external high-speed video synchronized with panel identity/state, WindowManager, SurfaceFlinger transactions/layers, app fade and first-buffer markers. Compare stock folding, Duo live, Duo screenshot; disable only Duo fade in a bounded diagnostic variant. Identify actual OFF versus lit-black versus missing content. Software commit is not optical presentation.
1. Samsung Camera OEM path (previous proposal): inspect and trace cover-preview/dual-display entry, exit and folding. Compare actual requests, state properties, mappings and power sequence. Gate: a distinct accessible route; if it uses the same request and remap, stop.
2. Alternate state-transition graph (new): inspect available firmware states and layouts before testing valid intermediate sequences. Seek a sequence that changes only one panel at a time or avoids changing the visible panel's identity. Gate: an actual differing layout path; do not brute-force IDs or repeat equivalent concurrent requests. Extra states may add blackouts.
3. Inner-primary fixed mapping (previous): keep native inner role while projecting to cover. First test supported closed-state residency; then full touch/focus/keyboard/Home/navigation and sleep/wake. Gate: no cancellation or native-profile regression. Entry/exit blackout counts as failure unless confined to an ordinary screen-off session.
4. Physical compositor routing (new): with fixed mappings, test authorized SurfaceControl layer-stack/projection routing of a test pattern; then live content. Different from moving a WindowManager task. Gate: permission, persistent routing, correct input mapping and restoration. If shell lacks permission, defer to root track; do not repeatedly fight system reassertion.
5. Persistent virtual display (previous): keep a test activity on a stable render target, show output on each physical display under fixed mapping. Gate: #3/#4 or equivalent power-stable route must work first. Then investigate arbitrary-app placement, input, IME, secure-content limits and native Home. Virtual display alone does not prevent physical OFF.
6. System transition screenshot ownership (new): inspect Samsung Shell/SystemUI display-change screenshot and black-background layers. If baseline identifies additional lit-black time, investigate an accessible transition handler or retained buffer at the correct compositor parent. Gate: reduced lit-black frames without missing content; cannot fix physical OFF. Existing prepared app buffer is the comparison, not a new experiment.
7. Display-mode stabilization (new diagnostic): compare allowed stable per-panel refresh modes/SDR test content against adaptive baseline. Trace mode changes and blackout duration. Gate: repeatable reduction correlated with mode switch; do not change app's default 120 FPS selection or introduce automatic capping. Seamless refresh transitions are not seamless panel handoff.
8. Earlier geometry-aware handoff (previous): complete native remap while incoming panel is still visually occluded; screenshot buffers cover surrounding content waits. Sweep slow/fast openings, pauses, reversals and head-on viewing. Gate: no visible darkness or layout jump; closing tested separately. Label concealment, not elimination.
9. Fixed mapping until ordinary sleep (new fallback): retain a validated interactive fixed-mapping session, defer native remap until screens are already off. Only after #3/#4/#5 succeeds; otherwise repeats failed continuity hold. Gate: usable native experience throughout session, acceptable power, clean lock/wake and no deferred visible flash. Do not keep both panels lit indefinitely by default.
10. Root/OEM-only staged remapping (previous, refined): inspect framework/driver constraints, prototype updating mapping and routing without OFF or switching one panel at a time. Removing the OFF wait alone is insufficient. Gate: hardware compositor accepts sequence, input/viewports remain aligned, rollback works. Separate lab branch; root availability unknown.
11. Root/OEM-only retained scanout (new, speculative): inspect panel/driver support for holding the last image or low-power scanout during remapping, instead of OFF. Gate: real vendor support for arbitrary retained content; ordinary AOD/DOZE is not proof. If supported this primarily benefits screenshot mode. No blind panel-power writes.

## Test discipline and stop criteria
- Capability/code inspection first; test pattern second; real app only after basic route works.
- One route per optional diagnostic build, bounded hold and original-state restoration on stop, lock, cancellation and helper death.
- Smoke test 10 slow and 10 fast cycles per direction, then reversals/pauses. Promote successful candidates to 100 cycles per direction across Home, scrolling app, keyboard and video, plus lock/wake and rotation release.
- Record visible-dark frames, sampled OFF duration, first correct frame, input readiness and cleanup outcome separately. No claim of zero blackout from software state alone.
- Test live and screenshot variants of successful routing, not every combination of an already failed physical route.
- A route that merely moves the blackout to exit or breaks native input is not a complete solution.
- Preserve current defaults and release behavior until device validation.

## Primary references
- Android LogicalDisplayMapper (state/layout transition and OFF wait):
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/LogicalDisplayMapper.java
- OEM concurrent/rear-display configuration:
  https://source.android.com/docs/core/display/windowmanager-extensions
- SurfaceControl hidden layer-stack/projection transactions (existence does not establish shell permission):
  https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/SurfaceControl.java
- VirtualDisplay:
  https://developer.android.com/reference/android/hardware/display/VirtualDisplay
- Input routing:
  https://source.android.com/docs/core/display/multi_display/input-routing
- Refresh-mode seamless constraints (not a panel-switch guarantee):
  https://source.android.com/docs/core/graphics/multiple-refresh-rate
- Winscope tracing:
  https://source.android.com/docs/core/graphics/winscope/overview

All proposed Samsung outcomes remain unverified. AOSP main is an architectural reference, not the exact installed Samsung implementation.

## Method 1 result — 2026-09-28
Camera capture and APK inspected; see CAMERA-HANDOFF-FINDINGS-20260928.md. Camera uses SemWindowManager.setForcedDefaultDisplayDevice, which translates to the same DeviceStateManagerGlobal request/cancel route. Dual preview is state 4/flags 4; rear-selfie wrapper argument 6 is state 5. No separate seamless-primary-switch path identified. Secondary window creation precedes the concurrent request and is a narrow readiness comparison candidate. Do not build a supposed blackout fix by merely substituting the wrapper. Next: method 2 layout/state graph.
