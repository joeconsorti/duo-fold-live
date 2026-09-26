package org.duofold.live;
/** Only a recent, explicitly fold-triggered sleep may be recovered. */
final class FoldWakePolicy {
 static boolean recent(long started,long now){return started>0&&now>=started&&now-started<=5000;}
 static boolean recover(long started,long now,int reason){return recent(started,now)&&reason==13;}
}
