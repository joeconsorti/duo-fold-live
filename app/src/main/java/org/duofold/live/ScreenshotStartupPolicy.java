package org.duofold.live;
/** Pure gate for the optional fixed-primary screenshot startup. */
final class ScreenshotStartupPolicy {
 static boolean canBegin(boolean primaryInner,int source,boolean windowAttached){
  return windowAttached && FreezePolicy.canSwitch(primaryInner,source);
 }
 static boolean keepInnerPrimary(int source){
  if(source!=0&&source!=1)throw new IllegalArgumentException("No outgoing frame");
  return source==1;
 }
}
