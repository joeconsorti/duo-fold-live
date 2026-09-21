package org.duofold.live;
final class PanelPowerPolicy {
 static final long WINDOW_MS=650,LEASE_MS=250;
 static final int MAX_REASSERTIONS=8;
 static boolean allowed(long now,long start,long lease,boolean enabled,boolean unlocked){
  return enabled&&unlocked&&start>0&&now>=start&&now-start<WINDOW_MS&&now>=lease&&now-lease<LEASE_MS;
 }
 static int restoreMode(int displayState){
  switch(displayState){case 1:return 0;case 2:return 2;case 3:return 1;case 4:return 3;default:return -1;}
 }
}
