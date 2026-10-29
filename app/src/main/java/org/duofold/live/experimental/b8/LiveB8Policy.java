package org.duofold.live.experimental.b8;

/** Pure live-hinge decisions. No lab countdown or simulated timeline. */
final class LiveB8Policy {
 static final long STALE_MS=2000, HEARTBEAT_MS=2500, TRANSITION_MS=6000;
 static boolean usable(boolean enabled,long now,long heartbeat,long sample,float angle){
  return enabled&&Float.isFinite(angle)&&sample>0&&now-sample<=STALE_MS&&now-heartbeat<=HEARTBEAT_MS;
 }
 // A 40-degree hold band prevents repeated primary remaps during mid-fold demos.
 static float opening(float handoff,float open){return Math.min(open,handoff+20f);}
 static float closing(float handoff){return Math.max(0f,handoff-20f);}
 static int target(float angle,int current,float handoff,float open){
  if(angle<=0)return 2;
  if(angle>=open)return 1;
  if(current==1)return angle<=closing(handoff)?2:1;
  return angle>=opening(handoff,open)?1:2;
 }
 static int homeInitialTarget(float angle,int current,float handoff,float open){
  if(angle<=0)return 2;
  if(angle>=open)return 1;
  float threshold=current==1?Math.max(handoff,open-20f):Math.min(handoff,20f);
  return current==1?(angle<=threshold?2:1):(angle>=threshold?1:2);
 }
 static boolean endpoint(float angle,float open){return angle<=0||angle>=open;}
 static boolean nativePanelMatches(float angle,float open,String physical,String inner,String cover){
  return angle<=0?cover.equals(physical):angle>=open?inner.equals(physical):true;
 }
 static boolean stable(long now,long since){return since>=0&&now-since>=75;}
 static boolean reveal(long now,long verified){return verified>=0&&now-verified>=150;}
}
