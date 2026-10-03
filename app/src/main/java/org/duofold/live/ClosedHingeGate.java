package org.duofold.live;
/** Shared angle hysteresis before rendering, preview startup, and display requests. */
public final class ClosedHingeGate {
 private boolean initialized,enabled,open,blocked;
 private float closed,raw=Float.NaN,effective=Float.NaN;
 private long suppressedEpisodes;
 public static float startAngle(float closed){return FoldThreshold.sanitizeClosed(closed)+2f;}
 public float filter(float raw,float closed,boolean enabled,boolean fresh){
  closed=FoldThreshold.sanitizeClosed(closed);
  if(!initialized||this.enabled!=enabled||this.closed!=closed){
   initialized=true;open=false;blocked=false;effective=Float.NaN;this.enabled=enabled;this.closed=closed;
  }
  this.raw=raw;
  if(!enabled){open=false;blocked=false;effective=FoldThreshold.effectiveAngle(raw,closed);return effective;}
  // Retain the last filtered value while existing downstream freshness handling applies.
  // A stale sample cannot open or close the latch.
  if(!fresh)return effective;
  if(!Float.isFinite(raw))return Float.NaN;
  if(raw<=closed)open=false;
  else if(!open&&raw>=startAngle(closed))open=true;
  boolean suppress=!open&&raw>closed;
  if(suppress&&!blocked)suppressedEpisodes++;
  blocked=suppress;
  effective=open?raw:0f;
  return effective;
 }
 public void reset(){initialized=false;open=false;blocked=false;raw=effective=Float.NaN;suppressedEpisodes=0;}
 public boolean open(){return open;}
 public long suppressedEpisodes(){return suppressedEpisodes;}
 public String report(){return "Closed-hinge jitter protection: "+(enabled?"ON":"OFF")+
  "; start >="+startAngle(closed)+"°, reset <="+closed+"°; raw="+raw+"°; effective="+effective+
  "°; "+(!Float.isFinite(effective)?"waiting for fresh angle":enabled?(open?"opening accepted":"closed latch"):"legacy threshold")+
  "; suppressed excursions="+suppressedEpisodes;}
}
