package org.duofold.live;
/** Timing is independent of hinge thresholds; it runs only after a physical-panel handoff. */
final class PreviewExpansionPolicy {
 static boolean fresh(long stamp,long now){return stamp>0 && now>=stamp && now-stamp<=500;}
 static float progress(long elapsed){float t=Math.max(0f,Math.min(1f,elapsed/240f));return 1f-(1f-t)*(1f-t)*(1f-t);}
 static float opacity(long elapsed,long readyElapsed){
  if(elapsed>=1500)return 0f;
  long fadeStart=readyElapsed<0?1200:Math.max(0,readyElapsed);
  return Math.max(0f,Math.min(1f,1f-(elapsed-fadeStart)/120f));
 }
}
