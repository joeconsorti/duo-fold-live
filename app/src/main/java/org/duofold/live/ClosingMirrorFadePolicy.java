package org.duofold.live;
/** Only the inner secondary mirror after an inner-to-cover primary switch. */
final class ClosingMirrorFadePolicy {
 private Boolean primaryInner;
 private long lastInner=-1,cutoff=-1,onSince=-1,reveal=-1;
 private boolean active;
 void reset(){primaryInner=null;lastInner=cutoff=onSince=reveal=-1;active=false;}
 float opacity(long now,boolean inner,boolean mirrorPanelOn,long mirrorSubmitted,float gradualness){
  if(inner){reset();primaryInner=true;lastInner=now;return 0;}
  if(Boolean.TRUE.equals(primaryInner)){active=true;cutoff=lastInner;onSince=reveal=-1;}
  primaryInner=false;
  if(!active)return 0;
  if(!mirrorPanelOn){onSince=reveal=-1;return 1;}
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
