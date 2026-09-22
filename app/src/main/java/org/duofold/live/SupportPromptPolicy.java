package org.duofold.live;
final class SupportPromptPolicy {
 static final long DAY=86400000L,WEEK=7*DAY;
 static boolean due(long now,long firstUse,long last,boolean enabled,boolean working){
  return enabled&&working&&firstUse>0&&now>=firstUse&&now-firstUse>=WEEK&&(last==0||(now>=last&&now-last>=WEEK));
 }
 static boolean cycleAllowed(long now,long first,long cycle,int count,long snooze,boolean retired,boolean working){
  return !retired&&working&&now>=snooze&&first>0&&now-first>=WEEK&&now>=cycle&&(cycle>0&&now-cycle<WEEK||count<3);
 }
}
