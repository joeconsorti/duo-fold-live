package org.duofold.live.root;

import android.os.Binder;

public final class DisplaySettingsRootService extends BaseRootService {
    @Override protected Binder create() { return new org.duofold.live.DisplaySettingsService(); }
}
