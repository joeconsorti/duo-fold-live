package org.duofold.live;
/** One bounded hold per fold/unfold; angle jitter at an endpoint cannot churn settings. */
final class FoldRotationPolicy {
 private boolean holding, blocked;
 private long started, endpointAt=-1, staleAt=-1;
 boolean update(long now, boolean enabled, boolean fresh, float angle, float open) {
  boolean endpoint=Float.isFinite(angle)&&(angle<=1f||angle>=FoldThreshold.sanitize(open));
  if(!enabled){holding=false;endpointAt=-1;staleAt=-1;blocked=true;return false;}
  if(!fresh||!Float.isFinite(angle)){
   endpointAt=-1;
   if(staleAt<0)staleAt=now;
   // A display handoff can briefly interrupt wallpaper replies. Keep an existing
   // hold through that gap, but release on a real reader failure.
   if(holding&&now-staleAt<1500&&now-started<30000)return true;
   holding=false;blocked=true;return false;
  }
  staleAt=-1;
  if(endpoint) {
   blocked=false;
   holding=false;endpointAt=-1;
  } else {
   endpointAt=-1;
   if(!holding&&!blocked){holding=true;started=now;}
  }
  if(holding&&now-started>=30000){holding=false;blocked=true;}
  return holding;
 }
}
