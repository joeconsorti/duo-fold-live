package org.duofold.live;
/** Animation-owned rotation hold; a resumed animation can re-arm at any partial angle. */
final class FoldRotationPolicy {
 private boolean holding;
 private long staleAt=-1;
 static boolean animationEnabled(boolean enabled,boolean unlocked,boolean allowed,
                                 boolean pausedSettled,boolean sceneVisible,boolean probe){
  return enabled&&unlocked&&allowed&&!probe&&(!pausedSettled||sceneVisible);
 }
 boolean update(long now, boolean enabled, boolean fresh, float angle, float open) {
  boolean endpoint=Float.isFinite(angle)&&(angle<=1f||angle>=FoldThreshold.sanitize(open));
  if(!enabled){holding=false;staleAt=-1;return false;}
  if(!fresh||!Float.isFinite(angle)){
   if(staleAt<0)staleAt=now;
   // A display handoff can briefly interrupt wallpaper replies. Keep an existing
   // hold through that gap, but release on a real reader failure.
   if(holding&&now-staleAt<1500)return true;
   holding=false;return false;
  }
  staleAt=-1;
  // Fresh animation heartbeats renew the hold. Pause, endpoint, disabled effect,
  // reader loss and owner-death recovery release it; elapsed animation time alone cannot.
  holding=!endpoint;
  return holding;
 }
}
