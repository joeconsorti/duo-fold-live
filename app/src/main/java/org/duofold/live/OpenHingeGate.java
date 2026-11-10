package org.duofold.live;

/** Open-end hysteresis, shared before animation, pause, and display decisions.
 * Reply freshness is not proof of hardware sensor freshness. */
public final class OpenHingeGate {
 private boolean initialized,enabled,latched,blocked;
 private float threshold,raw=Float.NaN,effective=Float.NaN;
 private long suppressedEpisodes;
 public static float startAngle(float open){return FoldThreshold.sanitize(open)-3f;}
 public float filter(float raw,float open,boolean enabled,boolean fresh){
  open=FoldThreshold.sanitize(open);
  if(!initialized||this.enabled!=enabled||threshold!=open){
   initialized=true;latched=true;blocked=false;effective=Float.NaN;
   this.enabled=enabled;threshold=open;
  }
  this.raw=raw;
  if(!enabled){latched=false;blocked=false;effective=raw;return effective;}
  if(!fresh)return effective;
  if(!Float.isFinite(raw))return Float.NaN;
  if(raw>=open)latched=true;
  else if(latched&&raw<=startAngle(open))latched=false;
  boolean suppress=latched&&raw<open;
  if(suppress&&!blocked)suppressedEpisodes++;
  blocked=suppress;
  effective=latched?Math.max(raw,open):raw;
  return effective;
 }
 public void reset(){initialized=enabled=latched=blocked=false;raw=effective=Float.NaN;suppressedEpisodes=0;}
 public long suppressedEpisodes(){return suppressedEpisodes;}
 public String report(){return "Open-hinge jitter protection: "+(enabled?"ON":"OFF")+
  "; close starts <="+startAngle(threshold)+"°, reset >="+threshold+
  "°; raw="+raw+"°; effective="+effective+"°; "+
  (!Float.isFinite(effective)?"waiting for fresh angle":enabled?(latched?"open latch":"motion accepted"):"legacy threshold")+
  "; suppressed excursions="+suppressedEpisodes;}
}
