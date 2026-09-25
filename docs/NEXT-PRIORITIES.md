# Ordered priorities — September 24, 2026

Preserve existing working functionality and the approved animation. Scope each pass narrowly; do not claim device fixes from compilation alone. Keep the community tutorial near the top of future posts and READMEs: https://www.youtube.com/watch?v=8Ucm7ceBDN4 (made by an app user).

1. Reliable temporary orientation hold in both directions, preserving rotation preferences.
2. Reduce actual black screen/handoff delay so fast unfolds still show the animation.
3. Fix only the first fold after unlocking unfolded showing the black debug effect.
4. Exclude the animation from the first cover mirror after install/reboot.
5. Reduce response latency without regressions.
6. Bounded adjustable handoff-angle slider.
7. Low-overhead edge anti-aliasing.
8. Right-align inner cover preview in landscape.
9. Reliable fade-in on both handoffs; investigate display overlap separately.
10. Reduce battery/CPU/GPU overhead.
11. Apply fade smoothing to fade-in as well as fade-out.
12. Later: lock-screen operation.
13. Later: remove screenshot-mode capture flash.
14. Future blackout-free design: cover preview → frost → defrost into native inner content; reverse on folding.
15. Later: operation without Shizuku/ADB, feasibility unproven.
16. Later: much stronger left-side blur.

## Orientation pass (local, not released)

- Retain an active hold across angle gaps up to 1500 ms, with existing 30-second safety bound and immediate release on disable/lock.
- Hold until the near-open endpoint (178 degrees) instead of the configurable animation endpoint; retain 450 ms endpoint settling.
- Capture secondary panel rotation preferences before primary freezing; apply secondary rotation/fixed policy while it remains secondary. Preserve journaled restoration and primary/per-posture restoration after remapping.
- Java compilation and 18 focused policy/parsing tests pass. No device verification yet; no guarantee that firmware accepts all privileged calls.
- Test auto-rotate ON and OFF, portrait and landscape, both folding directions, interruption/disable, and restored auto-rotate afterward. Inspect Rotation hold status if it fails.
