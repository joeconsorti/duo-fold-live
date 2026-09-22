package org.duofold.live;
/** One bounded hold per fold/unfold; angle jitter at an endpoint cannot churn settings. */
final class FoldRotationPolicy {
 private boolean holding, blocked;
 private long started, endpointAt=-1;
 boolean update(long now, boolean enabled, boolean fresh, float angle, float open) {
  boolean endpoint=Float.isFinite(angle)&&(angle<=1f||angle>=FoldThreshold.sanitize(open));
  if(!enabled||!fresh||!Float.isFinite(angle)){holding=false;endpointAt=-1;blocked=true;return false;}
  if(endpoint) {
   blocked=false;
   if(holding){if(endpointAt<0)endpointAt=now;if(now-endpointAt>=450)holding=false;}
  } else {
   endpointAt=-1;
   if(!holding&&!blocked){holding=true;started=now;}
  }
  if(holding&&now-started>=30000){holding=false;blocked=true;}
  return holding;
 }
}
