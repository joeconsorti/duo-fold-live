package org.duofold.live;
final class DualPanelPolicy {
 static boolean canStart(float angle){return Float.isFinite(angle)&&angle>=2&&angle<=172;}
 static boolean endpoint(float angle){return angle<=1||angle>=175;}
 static boolean targetInner(float angle,boolean previous){return Float.isFinite(angle) && (angle>=2 || (angle>1 && previous));}
}
