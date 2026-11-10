package org.duofold.live;

/** Optional discovery has a wall-clock deadline, never a handoff deadline. */
final class SystemBarWorkPolicy {
 static final long DISCOVERY_MS=1500;
 private long began=-1;
 private boolean complete;
 boolean available(long now){if(began<0)began=now;return !complete&&accepts(now);}
 boolean accepts(long now){return began>=0&&now>=began&&now-began<DISCOVERY_MS;}
 void complete(){complete=true;}
 static boolean geometry(int w,int h,int r,int actualW,int actualH,int actualR){
  return w>0&&h>0&&r>=0&&r<=3&&w==actualW&&h==actualH&&r==actualR;
 }
 static int rasterSide(int w,int h,int limit){return Math.max(1,Math.min(Math.max(w,h),Math.min(1440,limit)));}
 static boolean complete(boolean inner,boolean status,boolean navigation,boolean taskbar){return status&&(inner?taskbar:navigation);}
}
