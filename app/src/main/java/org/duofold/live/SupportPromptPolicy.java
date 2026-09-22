package org.duofold.live;
final class SupportPromptPolicy {
 static final long DAY=86400000L;
 static boolean due(long now,long firstUse,long last,boolean enabled,boolean working){
  return enabled&&working&&firstUse>0&&now>=firstUse&&now-firstUse>=DAY&&(last==0||(now>=last&&now-last>=DAY));
 }
}
