package org.duofold.live.experimental.b8;

/** Reject a stale opposite-panel dp layout without writing system density settings.
 * Allows small decor/rounding differences; this is not a pixel-perfect size assertion. */
public final class TaskLayoutPolicy {
 private TaskLayoutPolicy(){}
 public static boolean matches(int width,int height,int widthDp,int heightDp,int density){
  if(width<=0||height<=0||widthDp<=0||heightDp<=0||density<=0)return false;
  return close(width,widthDp*density/160.0)&&close(height,heightDp*density/160.0);
 }
 private static boolean close(int pixels,double configured){return Math.abs(pixels-configured)<=Math.max(96,pixels*.10);}
}
