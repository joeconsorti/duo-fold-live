package org.duofold.live;
/** Works from either initial posture. Hysteresis prevents chatter around the handoff. */
final class HandoffPolicy {
 boolean cover=false;
 private float handoff=HandoffSettings.DEFAULT;
 void handoff(float value){handoff=HandoffSettings.angle(value);}
 int update(float angle,boolean fresh,boolean interactive){
  if(!fresh||!interactive||!Float.isFinite(angle)){if(cover){cover=false;return -1;}return 0;}
  if(cover&&(angle>=handoff||angle<=0)){cover=false;return -1;}
  if(!cover&&angle>0&&angle<=HandoffSettings.closing(handoff)){cover=true;return 1;}
  return 0;
 }
 void reset(){cover=false;}
}
