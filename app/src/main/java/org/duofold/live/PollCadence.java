package org.duofold.live;
/** Completion-paced: never queue overlapping Binder polls or catch-up bursts. */
final class PollCadence {
 static long delay(boolean interactive, long elapsedMs, boolean urgent) {
  if (urgent && interactive) return 0;
  return Math.max(1, (interactive ? 16 : 500) - Math.max(0, elapsedMs));
 }
}
