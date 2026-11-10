package org.duofold.live;

/** Match the shell's 2 s stale-input ceiling, without treating held angles as fresh. */
final class B8AngleContinuity {
 static long leaseMs(boolean existingDirectFrame){return existingDirectFrame?2000:750;}
 static boolean retain(boolean existingDirectScene,boolean hasFrame,long age){
  return existingDirectScene&&hasFrame&&age>=750&&age<2000;
 }
}
