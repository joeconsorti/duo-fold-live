package org.duofold.live.root;

import android.os.Binder;

public final class CameraContinuityRootService extends BaseRootService {
    @Override protected Binder create() { return new org.duofold.live.CameraContinuityService(); }
}
