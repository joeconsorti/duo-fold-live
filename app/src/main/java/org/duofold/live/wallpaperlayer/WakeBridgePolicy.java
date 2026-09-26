package org.duofold.live.wallpaperlayer;
/** Never cover a locked screen; bound the temporary photo after the first unlock observation. */
final class WakeBridgePolicy {
 enum State { WAITING, SHOW, EXPIRED }
 private long deadline=-1;
 State observe(boolean locked,long now){
  if(deadline>=0&&now>=deadline)return State.EXPIRED;
  if(locked)return State.WAITING;
  if(deadline<0)deadline=now+750;
  return State.SHOW;
 }
 void reset(){deadline=-1;}
}
