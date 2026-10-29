package org.duofold.live;
/** Accept inset windows only with exact, stable placement. Full native-sized
 * buffers are handled separately; animated screen bounds do not size a surface. */
final class HomeBufferGeometry {
 static boolean exactWindow(int bw,int bh,int l,int t,int r,int b,int w,int h){
  return w>0&&h>0&&l>=0&&t>=0&&r<=w&&b<=h&&r-l>=w*.95&&b-t>=h*.85&&bw==r-l&&bh==b-t;
 }
}
