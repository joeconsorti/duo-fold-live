package org.duofold.live;
/** Visual masking only. Never changes display state, thresholds or the underlying animation. */
final class HandoffFadePolicy {
 static final long REVEAL_MS=180,READY_TIMEOUT_MS=900,ON_SETTLE_MS=32;
 static final int UI_DRAW=0,GLASS_COMMITTED=1,ENDPOINT_COMMITTED=2;
 static final long COMMIT_SETTLE_MS=2;
 /** Re-check cadence when nothing is fading; fresh angles wake the tick immediately. */
 static final long IDLE_TICK_MS=50;
 private boolean requireInnerGlass,requireCoverGlass;
 /** Fold 7 unified: the closing reveal also waits for a real cover glass frame (else it lands on the dark capture fallback). */
 void coverGlass(boolean require){requireCoverGlass=require;}
 private float openThreshold=172;
 private float handoff=HandoffSettings.DEFAULT;
 void handoff(float value){handoff=HandoffSettings.angle(value);}
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
 static float approach(boolean inner,float angle,float gradualness){return approach(inner,angle,gradualness,HandoffSettings.DEFAULT);}
 /** At the configured handoff angle: fully black at the hold angle closing and at the handoff angle opening. */
 static float approach(boolean inner,float angle,float gradualness,float handoff){
  handoff=HandoffSettings.angle(handoff);
  return approach(inner,angle,gradualness,HandoffSettings.closing(handoff),handoff);
 }
 /** innerBlack: angle where a closing inner panel is fully black; coverBlack: same for an opening cover panel. */
 static float approach(boolean inner,float angle,float gradualness,float innerBlack,float coverBlack){
  float width=FadeSettings.span(gradualness);
  float x=Math.max(0,Math.min(1,inner?(innerBlack+width-angle)/width:(angle-(coverBlack-width))/width));
  return x*x*(3-2*x);
 }
 /**
  * Degrees past the handoff angle at which the panels actually switch while opening; closing switches at the hold
  * angle. Fold7 (SM-F966U1, One UI 8.5) measured 92-94 closing and 101-102 opening at a 98 deg handoff: +3.
  */
 static float coverLagFor(String model){return DeviceCompatibility.isFold7(model)?3:0;}
 /** {closing black, opening black} for this model at the given handoff angle (94 / 101 on a Fold7 at 98). */
 static float[] blackAnglesFor(String model,float handoff){handoff=HandoffSettings.angle(handoff);return new float[]{HandoffSettings.closing(handoff),handoff+coverLagFor(model)};}
 /**
  * Upper bound on solid black after the destination panel turns ON. Duo draws only when a new angle
  * arrives, and on the Fold7 the wallpaper sends none for 0.3-0.5 s after a panel switch, so waiting
  * for a confirmed destination draw held black for 190-460 ms. Reveal at ON+40 ms there instead (no-glass path).
  */
 static long readyTimeoutFor(String model){return DeviceCompatibility.isFold7(model)?40:READY_TIMEOUT_MS;}
 /** Reveal (fade-in) length at 0% gradualness. Fold7 uses 100 ms so the switch reads as a short blink. */
 static long revealBaseFor(String model){return DeviceCompatibility.isFold7(model)?100:180;}
 private long revealBase=180;
 void revealBase(long ms){revealBase=ms>=60&&ms<=500?ms:180;}
 long revealMs(float gradualness){return Math.round(revealBase+320*FadeSettings.gradualness(gradualness));}
 /** Re-check cadence while VSYNC may be stopped. Every frame during a Fold7 switch, otherwise the original 80 ms. */
 static long tickDelayMs(boolean transitioning,boolean fastSwitchTicks){return transitioning&&fastSwitchTicks?16:80;}
 private long readyTimeout=READY_TIMEOUT_MS;
 void readyTimeout(long ms){readyTimeout=ms>=ON_SETTLE_MS&&ms<=READY_TIMEOUT_MS?ms:READY_TIMEOUT_MS;}
 /**
  * Opening (destination = inner) may wait longer than closing: on the Fold 7 the first inner glass frame
  * commits ~300-450 ms after the switch, and revealing earlier exposes the mirrored hold, which then cuts
  * over to the full display outside the black. Waiting keeps that cut-over inside the black. -1 = use readyTimeout.
  */
 static long openingReadyTimeoutFor(String model){return DeviceCompatibility.isFold7(model)?600:READY_TIMEOUT_MS;}
 private long openingReadyTimeout=-1;
 static final long FRESH_WINDOW_MS=350;
 /** Fold 7: the first post-switch capture alone takes up to ~400 ms, so upstream's 350 ms rejects real frames. */
 static long freshWindowFor(String model){return DeviceCompatibility.isFold7(model)?700:FRESH_WINDOW_MS;}
 private long freshWindow=FRESH_WINDOW_MS;
 void freshWindow(long ms){freshWindow=ms>=100&&ms<=2000?ms:FRESH_WINDOW_MS;}
 /**
  * Closing (destination = cover) cap when waiting for cover glass; the cover draws faster than the inner. Fold 7
  * (plan F2, 2026-10-04 capture): 7/10 closes confirmed glass at ON+190-281 ms; the 3 that hit a 300 ms cap had their
  * first capture at ON+230-265 ms and commit lags up to 114 ms, so their frames landed just past it. 380 ms lets those
  * reveal on a confirmed frame (worst case +80 ms of black) instead of the fallback.
  */
 static long closingReadyTimeoutFor(String model){return DeviceCompatibility.isFold7(model)?380:READY_TIMEOUT_MS;}
 private long closingReadyTimeout=-1;
 void closingReadyTimeout(long ms){closingReadyTimeout=ms>=ON_SETTLE_MS&&ms<=READY_TIMEOUT_MS?ms:-1;}
 void openingReadyTimeout(long ms){openingReadyTimeout=ms>=ON_SETTLE_MS&&ms<=READY_TIMEOUT_MS?ms:-1;}
 private float coverLag;
 void coverLag(float degrees){coverLag=Float.isFinite(degrees)&&degrees>=0&&degrees<=10?degrees:0;}
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
    if((inner&&requireInnerGlass)||(!inner&&requireCoverGlass)){
     boolean committed=drawnInner==inner&&drawn>=onSince&&drawn<=now;
     // captured is the capture START; captured>=onSince guarantees post-ON destination content. Window per device.
     boolean freshContent=committed&&captured>=onSince&&captured<=drawn&&now-captured<=freshWindow;
     boolean glass=freshContent&&kind==GLASS_COMMITTED;
     // Inner: fully open clear on fresh content. Cover: closed clear (native screen is the destination).
     boolean endpoint=kind==ENDPOINT_COMMITTED&&(inner?freshContent&&angle>=openThreshold:committed);
     if(readyAt<0){
      if(glass||endpoint){readyAt=drawn;readiness=glass?"fresh content capture + glass frame committed":(inner?"fresh content capture + fully-open clear committed":"closed cover clear committed");}
      else readiness="waiting for fresh content capture + glass commit";
     }
     if(readyAt>=0&&now-readyAt>=COMMIT_SETTLE_MS)reveal=now;
    }else if(now-onSince>=ON_SETTLE_MS&&drawnInner==inner&&drawn>=onSince+ON_SETTLE_MS){reveal=now;readiness="destination draw";}
    // The long opening cap waits for inner GLASS; the no-glass path keeps the short destination-draw cap.
    // Emergency escape is not evidence of readiness. Keep a bounded recovery
    // instead of leaving the user's display black after a renderer failure.
    long limit=inner?(requireInnerGlass&&openingReadyTimeout>0?openingReadyTimeout:readyTimeout):(requireCoverGlass?(closingReadyTimeout>0?closingReadyTimeout:openingReadyTimeout>0?openingReadyTimeout:readyTimeout):readyTimeout);
    if(reveal<0&&now-onSince>=limit){reveal=now;readiness=limit<READY_TIMEOUT_MS?"reveal cap at ON+"+limit+" ms; destination readiness NOT confirmed":"TIMEOUT recovery; readiness NOT confirmed";}
   }
   if(reveal<0)return 1;
   float x=Math.min(1,(now-reveal)/(float)revealMs(gradualness));
   if(x<1)return 1-x*x*(3-2*x);
   switched=reveal=-1;
  }
  float innerBlack=HandoffSettings.closing(handoff),coverBlack=handoff+coverLag,mid=(innerBlack+coverBlack)/2;
  if(!armed&&(inner?(angle>=mid+FadeSettings.span(gradualness)||angle<=arrival-2):(angle<=mid-FadeSettings.span(gradualness)||angle>=arrival+2)))armed=true;
  float alpha=armed?approach(inner,angle,gradualness,innerBlack,coverBlack):0;
  if(alpha<=0){blackSince=-1;expired=false;}
  if(alpha>=.999f){if(blackSince<0)blackSince=now;if(now-blackSince>1200)expired=true;}else blackSince=-1;
  return expired?0:filtered(alpha,now);
 }
}
