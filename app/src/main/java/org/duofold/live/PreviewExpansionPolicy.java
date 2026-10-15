package org.duofold.live;
/** Timing is independent of hinge thresholds; it runs only after a physical-panel handoff. */
final class PreviewExpansionPolicy {
 static boolean fresh(long stamp,long now){return fresh(stamp,now,500);}
 /** Hold readiness uses the reveal's per-model window (HandoffFadePolicy.freshWindowFor) so both agree on a frame. */
 static boolean fresh(long stamp,long now,long window){return stamp>0 && now>=stamp && now-stamp<=window;}
 static float progress(long elapsed){float t=Math.max(0f,Math.min(1f,elapsed/240f));return 1f-(1f-t)*(1f-t)*(1f-t);}
 static float opacity(long elapsed,long readyElapsed){return opacity(elapsed,readyElapsed,120);}
 /** fadeMs 0 drops the hold at readiness (Fold 7 unified: the black reveal starts then, so a cross-fade double-exposes). */
 static float opacity(long elapsed,long readyElapsed,long fadeMs){
  if(elapsed>=1500)return 0f;
  long fadeStart=readyElapsed<0?1200:Math.max(0,readyElapsed);
  // Zero fade only when readiness arrived (the black reveal starts then). On the no-readiness timeout the
  // black has long been revealed, so keep the normal cross-fade instead of a one-frame cut.
  if(fadeMs<=0&&readyElapsed>=0)return elapsed>=fadeStart?0f:1f;
  if(fadeMs<=0)fadeMs=120;
  return Math.max(0f,Math.min(1f,1f-(elapsed-fadeStart)/(float)fadeMs));
 }
}
