package org.duofold.live;
/** A request-local commit gate. No sleeps and no success implied by cleanup. */
final class PreviewReleaseGate {
 static final long BUDGET_MS=80;
 final long requestedAt;
 private String outcome="WAITING";
 PreviewReleaseGate(long now){requestedAt=now;}
 synchronized boolean maySubmit(long now){expire(now);return outcome.equals("WAITING");}
 synchronized boolean mayRelease(long now){expire(now);return !outcome.equals("WAITING");}
 synchronized boolean commit(long now){expire(now);if(!outcome.equals("WAITING"))return false;outcome="COMMITTED";return true;}
 synchronized void abort(String reason){if(outcome.equals("WAITING"))outcome="SKIPPED: "+reason;}
 synchronized String outcome(){return outcome;}
 private void expire(long now){if(outcome.equals("WAITING")&&now-requestedAt>=BUDGET_MS)outcome="TIMED_OUT";}
}
