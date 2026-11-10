package org.duofold.live;

/** Per-panel return advanced by completed frames, never by an idle wall clock. */
public final class B8PauseAnimation {
 public static final class Frame {
  public final long request,elapsedMs;
  public final float pose;
  private Frame(long request,long elapsed,float pose){this.request=request;elapsedMs=elapsed;this.pose=pose;}
 }
 private long token,lastAt,nextMs;
 private float from;
 public Frame frame(long request,long now,float visual,boolean inner,float open){
  if(request<=0){token=nextMs=lastAt=0;return new Frame(0,0,visual);}
  if(token!=request){token=request;lastAt=now;nextMs=0;from=visual;}
  float progress=HingePausePolicy.fraction(1,1+nextMs,HingePausePolicy.EASE_MS);
  return new Frame(request,nextMs,HingePausePolicy.pose(from,inner,open,progress));
 }
 /** Called only after the frame's current geometry/content checks pass.
  * Late or duplicate in-flight frames cannot advance a newer pose twice. */
 public boolean presented(Frame frame,long now){
  if(frame.request<=0||frame.request!=token||now<lastAt||frame.elapsedMs!=nextMs)return false;
  if(frame.elapsedMs>=HingePausePolicy.EASE_MS)return true;
  nextMs=Math.min(HingePausePolicy.EASE_MS,nextMs+Math.max(1,Math.min(32,now-lastAt)));
  lastAt=now;return false;
 }
}
