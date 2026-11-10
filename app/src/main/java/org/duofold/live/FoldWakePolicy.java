package org.duofold.live;
/** Only a recent, explicitly fold-triggered sleep may be recovered. */
final class FoldWakePolicy {
 // Endpoint sensor wobble must not create a service, brightness wake lock and
 // five-second lease. Four degrees still prepares well before physical closure.
 static boolean closingMotion(float peak,float angle){return Float.isFinite(peak)&&Float.isFinite(angle)&&peak>20f&&peak-angle>=4f;}
 static long holdUntil(long sessionStarted,long now){return Math.min(sessionStarted+30000,now+5000);}
 static boolean recent(long started,long now){return started>0&&now>=started&&now-started<=5000;}
 static boolean validLease(long started,long now,long deadline){return recent(started,now)&&deadline>started&&deadline<=started+5000&&now<deadline;}
 static boolean recover(long started,long now,int reason){return recent(started,now)&&reason==13;}
}
