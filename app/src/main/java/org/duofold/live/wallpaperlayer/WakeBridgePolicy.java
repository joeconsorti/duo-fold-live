package org.duofold.live.wallpaperlayer;
/** Never cover a locked screen; bound the temporary photo after the first unlock observation. */
final class WakeBridgePolicy {
 static final long FAILURE_TIMEOUT_MS=2000;
 enum State { WAITING, SHOW, EXPIRED }
 private long deadline=-1;
 State observe(boolean locked,long now){
  if(deadline>=0&&now>=deadline)return State.EXPIRED;
  if(locked)return State.WAITING;
  if(deadline<0)deadline=now+FAILURE_TIMEOUT_MS;
  return State.SHOW;
 }
 void reset(){deadline=-1;}
}
