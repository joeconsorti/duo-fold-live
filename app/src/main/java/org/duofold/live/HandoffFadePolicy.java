package org.duofold.live;
/** Visual masking only. Never changes display state, thresholds or the underlying animation. */
final class HandoffFadePolicy {
 static final long REVEAL_MS=180,READY_TIMEOUT_MS=900,ON_SETTLE_MS=32;
 static final int UI_DRAW=0,GLASS_COMMITTED=1,ENDPOINT_COMMITTED=2;
 static final long COMMIT_SETTLE_MS=2;
 private boolean requireInnerGlass;
 private float openThreshold=172;
 private long readyAt=-1;
 String readiness="idle";
 void renderer(boolean requireGlass,float open){requireInnerGlass=requireGlass;openThreshold=FoldThreshold.sanitize(open);}
 private float smoothing,gradualness,filtered;
 private long filterTime=-1;
 void settings(float smoothing,float gradualness){this.smoothing=FadeSettings.smoothing(smoothing);this.gradualness=FadeSettings.gradualness(gradualness);}
 private float filtered(float target,long now){
  long dt=filterTime<0?0:Math.max(0,now-filterTime);filterTime=now;
  if(smoothing==0||target>=1||dt==0){filtered=target;return target;}
  filtered+=(target-filtered)*(float)(1-Math.exp(-dt/smoothing));
  if(Math.abs(filtered-target)<.001f)filtered=target;
  return filtered;
 }
 private Boolean primary;
 private long switched=-1,reveal=-1,blackSince=-1,onSince=-1;
 private String physical;
 private boolean mappingChanged;
 private float arrival;
 private boolean armed=true,expired=false;
 void reset(){filterTime=-1;filtered=0;primary=null;physical=null;mappingChanged=false;switched=reveal=blackSince=onSince=readyAt=-1;armed=true;expired=false;readiness="idle";}
 void mapping(String id){if(physical!=null&&!physical.equals(id))mappingChanged=true;physical=id;}
 boolean transitioning(){return switched>=0;}
 static float approach(boolean inner,float angle){return approach(inner,angle,0);}
 static float approach(boolean inner,float angle,float gradualness){
  float width=FadeSettings.span(gradualness);
  float x=Math.max(0,Math.min(1,inner?(94+width-angle)/width:(angle-(98-width))/width));
  return x*x*(3-2*x);
 }
 float opacity(long now,boolean inner,float angle,boolean valid,boolean on,long drawn,boolean drawnInner){
  return opacity(now,inner,angle,valid,on,drawn,drawnInner,UI_DRAW,-1);
 }
 float opacity(long now,boolean inner,float angle,boolean valid,boolean on,long drawn,boolean drawnInner,int kind,long captured){
  if(!valid||!Float.isFinite(angle)){reset();return 0;}
  if(primary==null)primary=inner;
  if(primary!=inner||mappingChanged){mappingChanged=false;primary=inner;switched=now;reveal=onSince=readyAt=-1;arrival=angle;armed=false;expired=false;blackSince=-1;readiness="waiting for panel ON";}
  if(switched>=0){
   filterTime=now;filtered=0;
   // A submitted frame can precede the physical ON interval. Do not spend the
   // reveal timer while the panel is OFF, or reuse a pre-ON draw callback.
   if(!on){onSince=reveal=readyAt=-1;readiness="waiting for panel ON";return 1;}
   if(onSince<0)onSince=now;
   if(reveal<0){
    if(inner&&requireInnerGlass){
     boolean committed=drawnInner&&drawn>=onSince&&drawn<=now;
     boolean glass=committed&&kind==GLASS_COMMITTED&&captured>=onSince&&captured<=drawn&&now-captured<=350;
     boolean endpoint=committed&&kind==ENDPOINT_COMMITTED&&angle>=openThreshold;
     if(readyAt<0){
      if(glass||endpoint){readyAt=drawn;readiness=glass?"fresh content capture + glass frame committed":"fully open; clear frame committed";}
      else readiness="waiting for fresh content capture + glass commit";
     }
     if(readyAt>=0&&now-readyAt>=COMMIT_SETTLE_MS)reveal=now;
    }else if(now-onSince>=ON_SETTLE_MS&&drawnInner==inner&&drawn>=onSince+ON_SETTLE_MS){reveal=now;readiness="destination draw";}
    // Emergency escape is not evidence of readiness. Keep a bounded recovery
    // instead of leaving the user's display black after a renderer failure.
    if(reveal<0&&now-onSince>=READY_TIMEOUT_MS){reveal=now;readiness="TIMEOUT recovery; readiness NOT confirmed";}
   }
   if(reveal<0)return 1;
   float x=Math.min(1,(now-reveal)/(float)FadeSettings.reveal(gradualness));
   if(x<1)return 1-x*x*(3-2*x);
   switched=reveal=-1;
  }
  if(!armed&&(inner?(angle>=96+FadeSettings.span(gradualness)||angle<=arrival-2):(angle<=96-FadeSettings.span(gradualness)||angle>=arrival+2)))armed=true;
  float alpha=armed?approach(inner,angle,gradualness):0;
  if(alpha<=0){blackSince=-1;expired=false;}
  if(alpha>=.999f){if(blackSince<0)blackSince=now;if(now-blackSince>1200)expired=true;}else blackSince=-1;
  return expired?0:filtered(alpha,now);
 }
}
