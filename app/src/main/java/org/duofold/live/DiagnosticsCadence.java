package org.duofold.live;
/**
 * The code-2 reply carries ~10 diagnostic status strings (built with concatenation, some multi-line) on every
 * 4 ms poll, although they only feed status UI and the recovery log. They are sent at most every INTERVAL_MS; the
 * functional fields (angles, counts, flags, reader state) go out on every poll.
 */
final class DiagnosticsCadence {
 private DiagnosticsCadence(){}
 static final long INTERVAL_MS=250;
 static boolean due(long now,long nextAt){return nextAt<=0||now>=nextAt;}
}
