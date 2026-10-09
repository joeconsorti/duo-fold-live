package org.duofold.live.root;

import android.os.Binder;

public final class AngleReaderRootService extends BaseRootService {
    @Override protected Binder create() { return new org.duofold.live.AngleReader(); }
}
