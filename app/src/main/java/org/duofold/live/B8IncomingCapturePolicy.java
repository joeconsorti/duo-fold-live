package org.duofold.live;

/** Bounded settling for the first destination snapshot. Darkness is a suspicion,
 * never proof of an invalid app image. No optical readiness claim is made. */
final class B8IncomingCapturePolicy {
 static final String RETRY="RETRY_KEEP_SOURCE";
 static final String ACCEPT="ACCEPT_CONTENT";
 static final String FALLBACK="ACCEPT_BOUNDED_FALLBACK";
 private long started;
 private int attempts, emptyRetries;
 void reset(long now){started=now;attempts=0;emptyRetries=0;}
 long emptyRetryDelayMs(){return ++emptyRetries==1?50:150;}
 long emptyRetryDelayMs(boolean home,String reason,long now){
  long normal=emptyRetryDelayMs();
  // A known remap/fade is transient. Keep a short, bounded retry cadence;
  // missing content still never becomes valid through timeout or darkness.
  boolean settling=reason!=null&&(reason.contains("CAPTURE_GUARD_BEFORE")||reason.contains("CAPTURE_GUARD_AFTER")||reason.contains("HOME_CONTENT_FADED"));
  boolean fadedApp=!home&&reason!=null&&reason.contains("APP_CAPTURE_FADED");
  // A pre-capture rejection has no pixels to process. Check again briefly
  // while the old task leaves, then back off if both tasks stay visible.
  if(!home && emptyRetries<=3 && elapsed(now)<600 && reason!=null
      && reason.contains("CAPTURE_GUARD_BEFORE COMPETING_TASK selected="))return 50;
  return (home&&settling||fadedApp)&&elapsed(now)<1200?50:normal;
 }
 int attempts(){return attempts;}
 long elapsed(long now){return Math.max(0,now-started);}
 long retryDelayMs(){return attempts==1?50:150;}
 String evaluate(int darkSamples,long now){
  attempts++;
  if(darkSamples>=0&&darkSamples<1000)return ACCEPT;
  // Retain the newest capture at the bound, allowing legitimately dark apps.
  // Unknown pixel measurements also get a bounded retry, never a content pass.
  return (attempts>=5&&elapsed(now)>=600)||elapsed(now)>=1200?FALLBACK:RETRY;
 }
}
