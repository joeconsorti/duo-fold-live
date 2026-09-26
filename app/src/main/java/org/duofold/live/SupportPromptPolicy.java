package org.duofold.live;
final class SupportPromptPolicy {
 static final long DAY=86400000L,WEEK=7*DAY;
 static long interval(int delivered){return (delivered<=0?3:delivered==1?7:delivered==2?14:30)*DAY;}
 static long migratedDue(long first,long last,int count){return last>0?last+interval(Math.max(1,count)):first>0?first+interval(0):0;}
 static boolean due(long now,long first,long next,long last,long snooze,boolean retired,boolean working){return !retired&&working&&first>0&&next>0&&now>=first&&now>=last&&now>=next&&now>=snooze;}
}
