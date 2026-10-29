package org.duofold.live;

/** Native-managed Home/workspaces need a temporary cross-panel projection.
 * Backdrop and glass must sample the same placement. No native-layout claim. */
final class HomeProjectionPolicy {
 static boolean project(boolean home,int sw,int sh,int dw,int dh){
  return home&&sw>0&&sh>0&&dw>0&&dh>0&&Math.abs(sw/(double)sh-dw/(double)dh)>.01;
 }
 static float[] layout(int sw,int sh,int dw,int dh){
  boolean coverToInner=Math.min(sw,sh)/(float)Math.max(sw,sh)<.7f&&Math.min(dw,dh)/(float)Math.max(dw,dh)>.7f;
  // Keep the complete portrait launcher at its panel-height scale. Filling the
  // inner panel enlarged/cropped the icon grid before the native Home arrived.
  return coverToInner?LiveMirrorLayout.fit(sw,sh,dw,dh):LiveMirrorLayout.fill(sw,sh,dw,dh);
 }
}
