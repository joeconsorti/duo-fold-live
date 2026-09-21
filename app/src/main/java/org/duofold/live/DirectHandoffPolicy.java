package org.duofold.live;
/** Direct-state experiment only: preserve the existing 98/94 degree hysteresis. */
final class DirectHandoffPolicy {
 static final int HOLD=0,INNER=1,COVER=2,RELEASE=3;
 static int next(boolean innerHeld,float angle,boolean fresh,boolean interactive,boolean enabled,float open){
  if(!fresh||!interactive||!Float.isFinite(angle)||angle<=0||angle>=FoldThreshold.sanitize(open))return RELEASE;
  if(!enabled)return innerHeld?RELEASE:HOLD;
  if(innerHeld)return angle<=94?COVER:HOLD;
  return angle>=98?INNER:HOLD;
 }
}
