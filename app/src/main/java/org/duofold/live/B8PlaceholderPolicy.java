package org.duofold.live;
/** Fill only cross-panel placeholders. Native screenshots keep their existing fit policy. */
final class B8PlaceholderPolicy {
 static boolean appPreview(boolean nativeManaged, String source, String image, String destination) {
  return !nativeManaged && PanelIdentityPolicy.known(source) && source.equals(image)
    && PanelIdentityPolicy.known(destination) && !source.equals(destination);
 }
 static float[] layout(int sw,int sh,int dw,int dh,boolean placeholder) {
  return placeholder ? LiveMirrorLayout.fill(sw,sh,dw,dh) : LiveMirrorLayout.contain(sw,sh,dw,dh);
 }
}
