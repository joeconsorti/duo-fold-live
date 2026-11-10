package org.duofold.live;
/** Wallpaper reply time is not a sensor event time. Samsung base CLOSED vetoes motion. */
public final class PhysicalHingeGate {
 private boolean closed,blocked;private long lastClosedAt=-1,rejectedEpisodes;
 private int base=-1;private float raw=Float.NaN,effective=Float.NaN;
 public float filter(float value,long replyAt,int baseState,long observedAt,long now){
  raw=value;base=baseState;
  boolean known=baseState>=0&&observedAt>0&&now>=observedAt&&now-observedAt<=250;
  if(known&&baseState==0){closed=true;lastClosedAt=Math.max(lastClosedAt,observedAt);effective=0f;}
  else if(!known){effective=closed?0f:Float.NaN;}
  else {closed=false;effective=replyAt>lastClosedAt?value:Float.NaN;}
  boolean rejected=Float.isFinite(value)&&value>2f&&(effective==0f||!Float.isFinite(effective));
  if(rejected&&!blocked)rejectedEpisodes++;
  blocked=rejected;return effective;
 }
 public void reset(){closed=blocked=false;lastClosedAt=-1;rejectedEpisodes=0;base=-1;raw=effective=Float.NaN;}
 public String report(){return "Physical hinge guard: base="+base+"; raw wallpaper="+raw+"°; accepted="+effective+"°; rejected episodes="+rejectedEpisodes+"; CLOSED veto independent of jitter setting; reply timestamps are not sensor timestamps";}
}
