package org.duofold.live;
/** Probe ownership follows the episode, not the short-lived readiness check. */
final class B8ProbePolicy {
 static boolean accepts(long requestedSession,long latestSession){return requestedSession>0&&requestedSession==latestSession;}
}
