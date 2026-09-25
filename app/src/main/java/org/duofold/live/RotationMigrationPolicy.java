package org.duofold.live;
/** One repair generation, independent of app version. A successful upgrade never rearms it. */
final class RotationMigrationPolicy {
 static boolean needed(boolean complete,long installed,long updated){return !complete&&updated>installed;}
 static long retryDelay(int failures){return Math.min(300000L,15000L << Math.min(4,Math.max(0,failures-1)));}
}
