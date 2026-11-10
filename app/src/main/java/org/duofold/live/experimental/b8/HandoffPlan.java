package org.duofold.live.experimental.b8;
/** Bounded accelerated observation and protected native release. */
final class HandoffPlan {
 static boolean fast(int option){return option==13||option==14;}
 static int delay(int option,long age){return fast(option)&&((age>=9750&&age<14500)||(option==14&&age>=22750&&age<28500))?25:250;}
 static boolean prepareReturn(int option,long age,boolean promoted,boolean attempted){return option==14&&promoted&&!attempted&&age>=23000&&age<24000;}
 static boolean releaseReturn(int option,long age,boolean attempted,boolean ready){return option==14&&!attempted&&ready&&age>=24000&&age<26000;}
 static boolean stable(long age,long since){return since>=0&&age-since>=300;}
 static boolean revealReturn(long age,long verifiedAge){return age>=28000||(verifiedAge>=0&&age-verifiedAge>=750);}
}
