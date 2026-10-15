package org.duofold.live;
/** Completion-paced: never queue overlapping Binder polls or catch-up bursts. */
final class PollCadence {
 static final long FAST_MS=4,STILL_MS=33,SCREEN_OFF_MS=500,MOTION_HOLD_MS=400;
 /**
  * Panel switches happen between these angles on every supported fold; stay fast there through a brief pause
  * (BAND_HOLD_MS) so a resumed fold is caught at once, but not while resting there (Flex posture).
  */
 static final float SWITCH_BAND_LOW=80,SWITCH_BAND_HIGH=115;
 static final long BAND_HOLD_MS=2000;
 static long delay(boolean interactive, long elapsedMs, boolean urgent) {
  return delay(interactive,elapsedMs,urgent,0,Float.NaN);
 }
 /**
  * The 4 ms poll is only useful while the hinge moves: the wallpaper reports a new angle every 30-130 ms.
  * Fast while an angle changed within MOTION_HOLD_MS, near the switch angles, or when the angle is unknown;
  * otherwise 33 ms (one 30 Hz frame of start-of-motion latency, under the wallpaper's own report interval).
  */
 static long delay(boolean interactive, long elapsedMs, boolean urgent, long sinceChangeMs, float angle) {
  if (urgent && interactive) return 0;
  boolean fast=fast(sinceChangeMs,angle);
  long period=!interactive?SCREEN_OFF_MS:fast?FAST_MS:STILL_MS;
  return Math.max(1, period - Math.max(0, elapsedMs));
 }
 static boolean fast(long sinceChangeMs,float angle){
  return !Float.isFinite(angle)||sinceChangeMs<MOTION_HOLD_MS||(sinceChangeMs<BAND_HOLD_MS&&inBand(angle));
 }
 static boolean inBand(float angle){
  return angle>=SWITCH_BAND_LOW&&angle<=SWITCH_BAND_HIGH;
 }
}
