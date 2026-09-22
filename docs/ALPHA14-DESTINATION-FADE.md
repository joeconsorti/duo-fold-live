# Alpha.14: destination-first black reveal

Priority: make the black fade visible after both unfold and fold handoffs.
Keep original animation and successful angle-driven outgoing fade unchanged.
No frame-rate optimization or alternate switching mechanism changes.

Alpha.13 could spend its reveal timer before stable destination ON, and cleared
the mask immediately if the angle stream briefly became stale. These are code
paths that can explain the report, not an optical confirmation of its exact cause.

Now track physical unique ID as well as geometry; hold full black until the
primary reports continuously ON for 120 ms and a matching overlay draw occurs
after that interval. Reveal with the same smoothstep curve reversed, 180 ms.
An OFF event during reveal restarts black/settle/reveal. Draw fallback begins
900 ms after ON, not after the mapping change. Preserve the last fresh angle
for up to 1500 ms, with independent heartbeat expiry, disable and keyguard
cleanup. A brief missing-primary interval retains black for up to 500 ms.
Overscan black crops to avoid exposed edges while display dimensions change.

Tests cover both directions, stale/wrong-side/pre-settle draws, OFF during
reveal, long OFF before ON, mapping-before-geometry, and invalid/missing switch.
A device visual check is still required; reported ON/draw is not photon visibility.
Test normal mode with Continuity test OFF; send report if destination fade still
fails, since its phase is now included in the Handoff fade status.
