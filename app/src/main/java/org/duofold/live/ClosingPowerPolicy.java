package org.duofold.live;
/** One ON attempt, only after the native closed layout has replaced concurrency. */
final class ClosingPowerPolicy {
 static final long WATCH_MS=1200, PULSE_MS=250, INPUT_LEASE_MS=250;
 static boolean mayPulse(long now,long deadline,long inputUntil,boolean attempted,
                         boolean interactive,boolean samePanel,int current,int base,int displayState){
  // State 0 is the native closed state verified on the supported SM-F971U firmware.
  return now<deadline&&now<inputUntil&&!attempted&&interactive&&samePanel
    &&current==0&&base==0&&displayState==1;
 }
}
