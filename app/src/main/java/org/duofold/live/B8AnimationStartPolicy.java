package org.duofold.live;

/** Animation follows current valid hinge input, never a speed/start-time veto.
 * Native endpoint release and session lifetime belong to the controller. */
final class B8AnimationStartPolicy {
 private final boolean enabled;
 private boolean active;
 B8AnimationStartPolicy(boolean enabled){this.enabled=enabled;}
 boolean forFrame(float angle){return enabled&&Float.isFinite(angle);}
 boolean committed(float currentAngle,boolean animatedFrame){
  boolean wanted=forFrame(currentAngle);
  if(wanted!=animatedFrame)return false;
  if(wanted)active=true;
  return true;
 }
 String status(){return !enabled?"disabled":active?"animated":"waiting for first valid frame; no speed cutoff";}
}
