package org.duofold.live;

/** Coalesces tilt while a screenshot buffer is in flight; idle/disabled panels do no work. */
final class ScreenshotMotionPolicy {
 private float x,y;
 private long revision;
 static boolean usesMotion(boolean standard,boolean backdrop,boolean preview,boolean frozen){
  return !preview && (standard || (!backdrop && !frozen));
 }
 boolean update(float nextX,float nextY){
  if(!Float.isFinite(nextX)||!Float.isFinite(nextY)||nextX==x&&nextY==y)return false;
  x=nextX;y=nextY;revision++;return true;
 }
 long revision(){return revision;}
 boolean changedSince(long rendered){return rendered!=revision;}
}
