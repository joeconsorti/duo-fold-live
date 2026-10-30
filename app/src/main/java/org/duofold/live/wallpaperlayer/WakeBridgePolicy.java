package org.duofold.live.wallpaperlayer;
/** A full one-second hold; early Home readiness cannot shorten it. */
final class WakeBridgePolicy {
 static final long HOLD_MS=1000;
 /**
  * Home is sampled every ~500 ms plus window-enumeration time, and SCREEN_OFF is
  * delivered after the panel is already off (often several hundred ms late on
  * One UI). A 1500 ms window regularly missed a user who really was on Home,
  * so no bridge was prepared and the live wallpaper flashed on unlock. homeSeen
  * is cleared by every interactive sample that does not show Home active, so a
  * wider window does not admit stale Home state; a wrong guess is still removed
  * by the "Another app active" check after unlock.
  */
 static final long HOME_ELIGIBLE_MS=4000;
 static boolean homeEligible(long seen,long screenOffAt){return seen>=0&&screenOffAt>=seen&&screenOffAt-seen<HOME_ELIGIBLE_MS;}
 enum State { WAITING, PRELOCK_SHOW, SHOW, EXPIRED }
 private long deadline=-1;private boolean shown,unlockNotified;
 State observe(boolean locked,long now){
  return observe(locked,now,false);
 }
 State observe(boolean locked,long now,boolean prepareBeforeUnlock){
  if(locked)return prepareBeforeUnlock?State.PRELOCK_SHOW:State.WAITING;
  if(deadline<0)deadline=now+HOLD_MS;
  return now>=deadline?State.EXPIRED:State.SHOW;
 }
 void shown(long now){if(!shown){shown=true;deadline=Math.max(deadline,now+HOLD_MS);}}
 void unlocked(long now){if(!unlockNotified){unlockNotified=true;deadline=Math.max(deadline,now+HOLD_MS);}}
 long remaining(long now){return deadline<0?HOLD_MS:Math.max(0,deadline-now);}
 void reset(){deadline=-1;shown=false;unlockNotified=false;}
}
