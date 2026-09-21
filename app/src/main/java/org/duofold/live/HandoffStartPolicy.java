package org.duofold.live;
final class HandoffStartPolicy {
 static int target(boolean primaryInner,int source,boolean live){
  if(live)return primaryInner?0:1;
  return FreezePolicy.canSwitch(primaryInner,source)?(FreezePolicy.targetInner(source)?1:0):-1;
 }
}
