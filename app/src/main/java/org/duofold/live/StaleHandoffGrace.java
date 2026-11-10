package org.duofold.live;
/** Absolute sample-age limit; repeated stale polls cannot extend ownership. */
final class StaleHandoffGrace {
 static final long MAX_AGE_MS=2000;
 static boolean hold(boolean owned,boolean fresh,boolean interactive,boolean direct,float angle,float open,long sample,long now){
  return owned && !fresh && interactive && direct && Float.isFinite(angle) && angle>0 && angle<open && sample>0 && now>=sample && now-sample<MAX_AGE_MS;
 }
}
