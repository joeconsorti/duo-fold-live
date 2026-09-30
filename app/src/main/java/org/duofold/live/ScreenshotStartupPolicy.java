package org.duofold.live;
/** Pure gate for the staged screenshot startup and physical-panel routing. */
final class ScreenshotStartupPolicy {
 static boolean presented(long frameStamp,long committedStamp){return frameStamp>0 && frameStamp==committedStamp;}
 static boolean canBegin(boolean primaryInner,int source,boolean windowAttached){
  return windowAttached && FreezePolicy.canSwitch(primaryInner,source);
 }
 static boolean mayStart(boolean primaryInner,float angle,float threshold){
  return FoldThreshold.canStart(angle,threshold) && (!primaryInner || angle<=FoldThreshold.sanitize(threshold)-12f);
 }
 static boolean ownsFrame(String captured,String destination){
  return captured!=null && !captured.isEmpty() && !captured.equals("unknown") && captured.equals(destination);
 }
 static boolean incomingInner(int source){return FreezePolicy.targetInner(source);}
 static boolean keepInnerPrimary(int source){
  if(source!=0&&source!=1)throw new IllegalArgumentException("No outgoing frame");
  return source==1;
 }
}
