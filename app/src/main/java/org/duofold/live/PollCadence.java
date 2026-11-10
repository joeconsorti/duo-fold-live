package org.duofold.live;
/** Completion-paced: never queue overlapping Binder polls or catch-up bursts. */
final class PollCadence {
 static final class Endpoint {
  int period(long now,float angle,float open,boolean fresh,boolean active){
   int next=angle<=2?0:angle>=open?1:-1;
   if(active||!fresh||!Float.isFinite(angle)||next<0)return 16;
   // A screen-on endpoint is the start of the next fold. The former 100 ms
   // idle interval delayed discovering motion before any renderer could start.
   // Screen-off throttling is still applied independently by delay().
   return 32;
  }
 }
 static long delay(boolean interactive, long elapsedMs, boolean urgent) {
  if (urgent && interactive) return 0;
  return Math.max(1, (interactive ? 4 : 500) - Math.max(0, elapsedMs));
 }
}
