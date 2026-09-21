package org.duofold.live;
final class FreezePolicy {
 static boolean accepts(boolean inner,int width,int height,long stamp,long now){
  if(width<=0||height<=0||now<stamp||now-stamp>500)return false;
  boolean capturedInner=Math.min(width,height)/(float)Math.max(width,height)>.7f;
  return capturedInner==inner;
 }
 static boolean canSwitch(boolean primaryInner,int source){return source==(primaryInner?1:0);}
 static boolean targetInner(int source){if(source!=0&&source!=1)throw new IllegalArgumentException("No outgoing frame");return source==0;}
}
