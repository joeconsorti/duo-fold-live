package org.duofold.live;

/** Stationary-hinge gesture. Wallpaper receipt stamps are not hardware sensor timestamps.
 * Repeated replies and gaps cannot manufacture a continuous hold. */
public final class HingePausePolicy {
 public static final long HOLD_MS=1500, EASE_MS=280, FADE_MS=160;
 private static final float STILL_DEGREES=5f, RESUME_DEGREES=5f;
 private long since=-1,lastSample=-1,heldAt;
 private float anchor=Float.NaN,low=Float.NaN,high=Float.NaN;
 public static final String RESUME_MOVEMENT_KEY="hinge_pause_resume_movement";
 private int source,route=-1;
 private boolean resumeMovement;
 private String decision="waiting";
 public void reset(){since=lastSample=-1;heldAt=0;anchor=low=high=Float.NaN;source=0;route=-1;}
 public void update(boolean enabled,long now,long sample,float angle,float open,int primary,int mode){
  update(enabled,now,sample,angle,open,primary,mode,false,true);
 }
 public void update(boolean enabled,long now,long sample,float angle,float open,int primary,int mode,boolean movement,boolean eligible){
  if(!enabled){reset();decision="disabled";return;}
  if(route!=mode||resumeMovement!=movement){reset();route=mode;resumeMovement=movement;decision="mode changed";}
  if(!Float.isFinite(angle)||sample<=0||sample>now||now-sample>=750){suspend();return;}
  if(sample<=lastSample)return;
  boolean gap=lastSample>0&&sample-lastSample>250;
  lastSample=sample;
  if(angle<=0||angle>=open){since=-1;heldAt=0;anchor=angle;source=mode==2?(angle<=0?2:1):primary;decision="endpoint reached; next leg eligible";return;}
  // A power/keyguard/delivery gap must not re-arm a completed default gesture.
  if(!eligible){suspend();return;}
  if(heldAt>0){
   // A completed Duo gesture consumes this leg. Crossing the midpoint changes
   // the native panel only; it must not create another animated session.
   if(mode==2&&!movement){
    if(angle<90f)source=2;else if(angle>90f)source=1;
    decision="native continuation; animation resumes on next leg";return;
   }
   boolean resume=movement?Math.abs(angle-anchor)>=RESUME_DEGREES:
    (source==1&&angle<90f)||(source==2&&angle>90f);
   if(!resume)return;
   heldAt=0;if(mode!=2)source=primary;since=mode==2?-1:sample;anchor=angle;
   decision=movement?"resumed after 5 degrees":"resumed across 90 degrees";return;
  }
  // Select the return screen when a stationary hold is confirmed. The last
  // endpoint is not the origin of every subsequent reversal within a session.
  // Logical D0 may already have remapped, so only exact 90 uses that identity.
  if(source==0)source=primary;
  if(since<0||gap||!Float.isFinite(anchor)||Math.abs(angle-anchor)>STILL_DEGREES){
   since=sample;anchor=low=high=angle;if(source==0)source=primary;return;
  }
  // Bounded back-and-forth jitter is allowed. Extending that envelope by a
  // sensor degree is progress, even below 5 degrees per 1.5 seconds. Restart
  // the stillness clock without forgetting the envelope on every reply.
  if(mode==2&&(angle<=low-1f||angle>=high+1f)){
   low=Math.min(low,angle);high=Math.max(high,angle);since=sample;
   decision="hinge progressing; stillness timer restarted";return;
  }
  if(sample-since>=HOLD_MS){heldAt=now;anchor=angle;source=angle<90f?2:angle>90f?1:primary;decision="pause completed; screen selected from held angle";}
 }
 private void suspend(){
  since=-1;lastSample=-1;
  if(resumeMovement){heldAt=0;anchor=low=high=Float.NaN;source=0;}
  decision=held()?"completed pause retained during input/power gap":"waiting for eligible fresh input";
 }
 public boolean held(){return heldAt>0;}
 public long startedAt(){return heldAt;}
 public int sourcePrimary(){return source;}
 public boolean settled(long now){return held()&&now-heldAt>=EASE_MS;}
 public static float fraction(long start,long now,long duration){
  if(start<=0||now<=start)return 0f;
  float t=Math.min(1f,(now-start)/(float)duration);
  return t*t*(3f-2f*t);
 }
 public static float pose(float visual,boolean inner,float open,float amount){
  return visual+((inner?open:0f)-visual)*Math.max(0f,Math.min(1f,amount));
 }
 public String report(){return "Hinge pause: "+(held()?"settled/returning; selected="+(source==1?"inner":"cover"):"waiting for 1.5 seconds still")+"; below 90° cover, above 90° inner, at 90° active panel; hinge progress restarts timer; 5° jitter tolerance; "+(resumeMovement?"resume after 5° movement":(route==2?"stay native through midpoint until endpoint/next leg":"stay native until endpoint/next leg or crossing 90° toward other panel"))+"; decision="+decision+"; 280 ms frame-paced unwind + 160 ms screenshot dissolve";}
}
