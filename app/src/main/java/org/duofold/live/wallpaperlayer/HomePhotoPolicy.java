package org.duofold.live.wallpaperlayer;
/** Exclude launcher popups/small windows from the full-display wallpaper anchor. */
public final class HomePhotoPolicy {
 public static boolean accepts(int width,int height,int displayWidth,int displayHeight){
  return displayWidth>0&&displayHeight>0&&width>0&&height>0&&width<=displayWidth&&height<=displayHeight
   &&(long)width*100>= (long)displayWidth*90 &&(long)height*100 >= (long)displayHeight*80;
 }
}
