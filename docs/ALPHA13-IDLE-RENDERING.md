# Alpha 13: remove idle legacy rendering in coordinated screenshot mode

Alpha 12 logs confirm B8 route selection and continuous capture disabled, but contain no active B8 handoff. The existing `unlocked` diagnostic is a combined host/enabled/interactive/keyguard/thermal gate, so it cannot establish a lock-screen cause. No start-gate bypass or forced enabling is appropriate.

The coordinated screenshot composable still attached PreparedGlassHost and two ordinary FrostSurface renderers. Replace them with a transparent 1x1-buffer SurfaceView registered solely for capture exclusion. It has no shader, angle listener, vsync callback, or requested refresh rate. B8 panel windows continue registering their own exclusion handles. Legacy rendering outside coordinated snapshot mode is unchanged.

Report each actual start gate and record changes, including the eligibility value received by AngleReader. The dedicated mode card clearly states when animation is off. The mode preset and Enable animation preference are preserved.

This is a concrete idle rendering fix plus missing diagnostic evidence. It does not establish why the alpha 12 handoff never started or demonstrate optical continuity, thermal improvement, or correct animation direction on a device.
