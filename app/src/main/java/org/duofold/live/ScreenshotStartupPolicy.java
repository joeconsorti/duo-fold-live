package org.duofold.live;
/** Pure gate for the optional fixed-primary screenshot startup. */
final class ScreenshotStartupPolicy {
 static boolean presented(long frameStamp,long committedStamp){return frameStamp>0 && frameStamp==committedStamp;}
 static boolean canBegin(boolean primaryInner,int source,boolean windowAttached){
  return windowAttached && FreezePolicy.canSwitch(primaryInner,source);
 }
 static boolean keepInnerPrimary(int source){
  if(source!=0&&source!=1)throw new IllegalArgumentException("No outgoing frame");
  return source==1;
 }
}
