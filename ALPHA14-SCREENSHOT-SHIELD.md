# 3.5.9-alpha.14-local — independent screenshot shield

The recovery ZIP records SurfaceFlinger aborting with `Cycle detected in LayerHierarchy::traverse`. The live B8 shield created WMS display mirrors, reparented them below standalone roots, and exposed those mirrors during display promotion. The precise transaction responsible for the recorded cycle is not available; this build removes that suspect graph mechanism from the production live B8 route.

The shield now contains only owned containers and captured pixel-buffer children. It creates no display or surface mirrors. Captures remain visible through task relocation and primary promotion, and follow their original physical panels throughout. The existing destination geometry, presentation commit, capture drain, brightness, timeout and removal gates remain. Removal immediately revokes promotion readiness, even before the removal callback.

The glass presentation remains enabled when the user's animation setting is enabled. During the protected switch, its captured frame is held static rather than exposing a live mirrored animation. This can introduce a visible pause or jump when the destination is revealed. Retargeting is sampled, not atomic with Android's display remapping; no claim of blackout-free optical behavior is made.

Recovery collection reads the crash buffer without the old 120-line restriction and retains up to 256 KiB, including partial output on command timeout. Pending cleanup logs STOP once instead of every 25 ms so it does not bury the initiating event.

Scope: the live B8 controller uses this new shield. Historical manually gated Display Lab experiment backends are unchanged and are not the route for this build's Coordinated Screenshot Test.

Validation: 74 fake-platform lifecycle/readiness checks, real-bridge physical mapping/hold/removal checks, brightness checks, architectural guard against mirror APIs in LiveB8Bridge, and the Github debug unit/build tasks. These checks cannot validate the vendor compositor. No handset is attached; system stability and visible transition quality remain unverified.

Local APK only. No GitHub publication. Updating preserves the user's disabled animation preference; it does not automatically start a fold test.
