package org.duofold.live;
/** Visual masking only. Never changes display state, thresholds or the underlying animation. */
final class HandoffFadePolicy {
 static final long REVEAL_MS=180,READY_TIMEOUT_MS=900,ON_SETTLE_MS=120;
 private Boolean primary;
 private long switched=-1,reveal=-1,blackSince=-1,onSince=-1;
 private String physical;
 private boolean mappingChanged;
 private float arrival;
 private boolean armed=true,expired=false;
 void reset(){primary=null;physical=null;mappingChanged=false;switched=reveal=blackSince=onSince=-1;armed=true;expired=false;}
 void mapping(String id){if(physical!=null&&!physical.equals(id))mappingChanged=true;physical=id;}
 boolean transitioning(){return switched>=0;}
 static float approach(boolean inner,float angle){
  float x=Math.max(0,Math.min(1,inner?(104-angle)/10f:(angle-88)/10f));
  return x*x*(3-2*x);
 }
 float opacity(long now,boolean inner,float angle,boolean valid,boolean on,long drawn,boolean drawnInner){
  if(!valid||!Float.isFinite(angle)){reset();return 0;}
  if(primary==null)primary=inner;
  if(primary!=inner||mappingChanged){mappingChanged=false;primary=inner;switched=now;reveal=onSince=-1;arrival=angle;armed=false;expired=false;blackSince=-1;}
  if(switched>=0){
   // A submitted frame can precede the physical ON interval. Do not spend the
   // reveal timer while the panel is OFF, or reuse a pre-ON draw callback.
   if(!on){onSince=reveal=-1;return 1;}
   if(onSince<0)onSince=now;
   if(reveal<0&&now-onSince>=ON_SETTLE_MS&&
      ((drawnInner==inner&&drawn>=onSince+ON_SETTLE_MS)||now-onSince>=READY_TIMEOUT_MS))reveal=now;
   if(reveal<0)return 1;
   float x=Math.min(1,(now-reveal)/(float)REVEAL_MS);
   if(x<1)return 1-x*x*(3-2*x);
   switched=reveal=-1;
  }
  if(!armed&&(inner?(angle>=106||angle<=arrival-2):(angle<=86||angle>=arrival+2)))armed=true;
  float alpha=armed?approach(inner,angle):0;
  if(alpha<=0){blackSince=-1;expired=false;}
  if(alpha>=.999f){if(blackSince<0)blackSince=now;if(now-blackSince>1200)expired=true;}else blackSince=-1;
  return expired?0:alpha;
 }
}
