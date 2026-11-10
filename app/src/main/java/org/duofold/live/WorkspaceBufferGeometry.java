package org.duofold.live;
/** Window captures have local coordinates. Never stretch a full-display buffer
 * into a small pane or infer placement from an unstable compositor transform. */
public final class WorkspaceBufferGeometry {
 private WorkspaceBufferGeometry(){}
 public static boolean exact(int bw,int bh,int l,int t,int r,int b,int w,int h){
  return w>0&&h>0&&l>=0&&t>=0&&r<=w&&b<=h&&r>l&&b>t&&bw==r-l&&bh==b-t;
 }
}
