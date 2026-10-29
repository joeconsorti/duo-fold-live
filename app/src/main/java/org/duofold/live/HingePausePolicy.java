package org.duofold.live;

/** Stationary-hinge gesture. Uses sensor time, never repeated IPC replies as time evidence. */
public final class HingePausePolicy {
 public static final long HOLD_MS=1250, EASE_MS=280, FADE_MS=160;
 private static final float STILL_DEGREES=5f, RESUME_DEGREES=5f;
 private long since=-1,lastSample=-1,heldAt;
 private float anchor=Float.NaN;
 private int source,route=-1;
 public void reset(){since=lastSample=-1;heldAt=0;anchor=Float.NaN;source=0;route=-1;}
 public void update(boolean enabled,long now,long sample,float angle,float open,int primary,int mode){
  if(!enabled||!Float.isFinite(angle)||sample<=0||sample>now||now-sample>=750){reset();return;}
  if(route!=mode){reset();route=mode;}
  if(angle<=0||angle>=open){since=-1;lastSample=sample;heldAt=0;anchor=angle;source=primary;return;}
  if(sample<=lastSample)return;
  // Missing sensor time cannot count as a deliberate 1.25-second hold.
  boolean gap=lastSample>0&&sample-lastSample>250;
  lastSample=sample;
  if(heldAt>0){
   if(Math.abs(angle-anchor)<RESUME_DEGREES)return;
   heldAt=0;source=primary;since=sample;anchor=angle;return;
  }
  if(since<0||gap||!Float.isFinite(anchor)||Math.abs(angle-anchor)>STILL_DEGREES){
   since=sample;anchor=angle;if(source==0)source=primary;return;
  }
  // Choose once at confirmation, independent of opening/closing direction.
  // At exactly 90 degrees keep the active panel; held jitter cannot flip it.
  if(sample-since>=HOLD_MS){heldAt=now;anchor=angle;source=angle<90f?2:angle>90f?1:primary;}
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
 public String report(){return "Hinge pause: "+(held()?"settled/returning; selected="+(source==1?"inner":"cover"):"waiting for 1.25 seconds still")+"; below 90° cover, above 90° inner, at 90° active panel; 5° hold tolerance; resume after 5° movement; 280 ms unwind + 160 ms screenshot dissolve";}
}
