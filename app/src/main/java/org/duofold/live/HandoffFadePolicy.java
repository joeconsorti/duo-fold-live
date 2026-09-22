package org.duofold.live;
/** Visual masking only. Never changes display state, thresholds or the underlying animation. */
final class HandoffFadePolicy {
 static final long REVEAL_MS=180,READY_TIMEOUT_MS=900;
 private Boolean primary;
 private long switched=-1,reveal=-1,blackSince=-1;
 private float arrival;
 private boolean armed=true,expired=false;
 void reset(){primary=null;switched=reveal=blackSince=-1;armed=true;expired=false;}
 static float approach(boolean inner,float angle){
  float x=Math.max(0,Math.min(1,inner?(104-angle)/10f:(angle-88)/10f));
  return x*x*(3-2*x);
 }
 float opacity(long now,boolean inner,float angle,boolean valid,boolean on,long drawn,boolean drawnInner){
  if(!valid||!Float.isFinite(angle)){reset();return 0;}
  if(primary==null)primary=inner;
  if(primary!=inner){primary=inner;switched=now;reveal=-1;arrival=angle;armed=false;expired=false;blackSince=-1;}
  if(switched>=0){
   if(reveal<0&&((on&&drawnInner==inner&&drawn>=switched&&now-switched>=50)||now-switched>=READY_TIMEOUT_MS))reveal=now;
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
