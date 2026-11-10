package org.duofold.live.experimental.b8;
/** Timing for the live window-layer bridge; no settings or power changes. */
final class BridgePlan {
 static boolean enabled(int option){return option>=9&&option<=14;}
 static boolean stagedReveal(int option){return option>=11&&option<=14;}
 static boolean recoveryDue(int option,long age,boolean verified,boolean bridgeClosed,boolean attempted){return option==12&&verified&&bridgeClosed&&!attempted&&age>=13000&&age<16000;}
 static boolean prepare(long age,boolean outbound,boolean attempted){return outbound&&!attempted&&age>=8000&&age<10000;}
 static boolean remove(long age,long verifiedAge){return age>=14000||(verifiedAge>=0&&age-verifiedAge>=750);}
 static boolean canPromote(boolean submitted,boolean committed,boolean closed){return submitted&&committed&&!closed;}
}
