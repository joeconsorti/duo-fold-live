package org.duofold.live;
/** Only the inner secondary mirror after an inner-to-cover primary switch. */
final class ClosingMirrorFadePolicy {
 private Boolean primaryInner;
 private long lastInner=-1,cutoff=-1,onSince=-1,reveal=-1,offSince=-1;
 private boolean active;
 void reset(){primaryInner=null;lastInner=cutoff=onSince=reveal=offSince=-1;active=false;}
 boolean active(){return active;}
 float opacity(long now,boolean inner,boolean mirrorPanelOn,long mirrorSubmitted,float gradualness){
  if(inner){reset();primaryInner=true;lastInner=now;return 0;}
  if(Boolean.TRUE.equals(primaryInner)){active=true;cutoff=lastInner;onSince=reveal=offSince=-1;}
  primaryInner=false;
  if(!active)return 0;
  // Fully closed: the secondary panel goes away for good. Bound the mask so the fade engine can idle and the
  // next opening does not start black-masked.
  if(!mirrorPanelOn){if(offSince<0)offSince=now;if(now-offSince>=2*HandoffFadePolicy.READY_TIMEOUT_MS){active=false;return 0;}onSince=reveal=-1;return 1;}
  offSince=-1;
  if(onSince<0)onSince=now;
  // Submission is software readiness, not proof of photon visibility. Allow
  // settling after BOTH the panel becomes ON and this closing mirror arrives.
  long readyAt=Math.max(onSince,mirrorSubmitted);
  if(reveal<0&&((mirrorSubmitted>=cutoff&&now-readyAt>=HandoffFadePolicy.ON_SETTLE_MS)
      ||now-onSince>=HandoffFadePolicy.READY_TIMEOUT_MS))reveal=now;
  if(reveal<0)return 1;
  float x=Math.min(1,(now-reveal)/(float)FadeSettings.reveal(gradualness));
  if(x>=1){active=false;return 0;}
  return 1-x*x*(3-2*x);
 }
}
