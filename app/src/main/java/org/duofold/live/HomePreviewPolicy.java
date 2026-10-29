package org.duofold.live;

/** Visual-only Home cross-panel veil. It never supplies readiness or extends a lease. */
final class HomePreviewPolicy {
 static final long DISSOLVE_MS=120;
 private boolean placeholderSeen;
 private long readyAt=-1;
 private boolean animating;
 static boolean eligible(boolean home,int sw,int sh,int dw,int dh){
  if(!home||sw<=0||sh<=0||dw<=0||dh<=0)return false;
  float source=Math.min(sw,sh)/(float)Math.max(sw,sh);
  float target=Math.min(dw,dh)/(float)Math.max(dw,dh);
  return source<.7f&&target>.7f||source>.7f&&target<.7f;
 }
 float opacity(boolean nativeReady,boolean endpoint,long now){
  if(!nativeReady){placeholderSeen=true;readyAt=-1;animating=false;return 1f;}
  if(!placeholderSeen){animating=false;return 0f;}
  if(readyAt<0)readyAt=now;
  // The normal endpoint release must never wait for a cosmetic dissolve.
  if(endpoint){placeholderSeen=false;animating=false;return 0f;}
  float t=Math.max(0f,Math.min(1f,(now-readyAt)/(float)DISSOLVE_MS));
  animating=t<1f;
  if(!animating)placeholderSeen=false;
  return 1f-t*t*(3f-2f*t);
 }
 boolean animating(){return animating;}
}
