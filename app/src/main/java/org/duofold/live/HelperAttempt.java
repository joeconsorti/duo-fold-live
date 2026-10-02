package org.duofold.live;
/** One binding attempt. Old callbacks and duplicate connects cannot revive a finished attempt. */
public final class HelperAttempt {
 public static final long START_TIMEOUT_MS=35000;
 private boolean open=true,connected;
 public boolean connect(){if(!open||connected)return false;connected=true;return true;}
 public boolean finish(){if(!open)return false;open=false;return true;}
 public boolean open(){return open;}
 public boolean connected(){return connected;}
}
