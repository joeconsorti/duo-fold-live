# Alpha 11 local: coordinated screenshots

Alpha 10 selected the legacy ConcurrentController endpoint route, losing the shielded primary exchange. Alpha 11 selects LiveB8Controller with dual=false and b8_snapshot_test=true. The preference is applied once when the debug activity opens.

The B8 power, brightness, task movement, shield commits, geometry checks, and endpoint cleanup remain intact. The presentation retains its initial exact app capture, and accepts one verified destination app capture per physical panel in the session. It never consumes the continuous GlassFrames feed in this preset. Ordinary capture clients and screen-on warm-up are blocked at the producer, including in-flight delivery invalidation. Snapshot windows redraw at a 16 ms requested interval; actual display cadence is not guaranteed. Mipmaps are built only for discrete captured frames.

This is not the old Screenshot Mode picker route. Status identifies Coordinated Screenshot Test. It is not a guarantee of zero optical blackout. Both panels still render animated GPU composites, and the existing B8 shield/mirror and brightness work remain. Thermal stops remain enabled. Full-close Android cancellation and cleanup can still interrupt continuity. No handset or GPU profiler is attached here.

Validation: debug APK/unit-test build, B8 lifecycle/cleanup/brightness harness, shader bounds check. Device testing is still required; do not repeatedly reproduce rapid heating.
