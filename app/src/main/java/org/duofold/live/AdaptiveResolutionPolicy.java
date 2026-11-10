package org.duofold.live;

/** Thermal status, not battery temperature. Time is monotonic elapsed realtime. */
final class AdaptiveResolutionPolicy {
 static final long RECOVERY_MS=10*60*1000L;
 private int cap=100;
 private long coolSince=-1;
 AdaptiveResolutionPolicy(boolean pendingRecovery){if(pendingRecovery)cap=25;}
 void observe(int status,long now){
  if(status<0){if(cap!=100){cap=25;coolSince=-1;}return;}
  if(status>=1){cap=25;coolSince=-1;return;}
  if(cap==25){cap=50;coolSince=now;}
  if(cap==50&&now-coolSince>=RECOVERY_MS){cap=100;coolSince=-1;}
 }
 int effective(int selected,boolean enabled){return enabled?Math.min(RenderQuality.resolution(selected),cap):RenderQuality.resolution(selected);}
 int cap(){return cap;}
 boolean recovering(){return cap!=100;}
 long remaining(long now){return cap==50?Math.max(0,RECOVERY_MS-(now-coolSince)):-1;}
}
