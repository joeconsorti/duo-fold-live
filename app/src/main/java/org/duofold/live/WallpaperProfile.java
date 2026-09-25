package org.duofold.live;
/** Wallpaper assets differ by Fold generation; this is not a compatibility guarantee. */
public final class WallpaperProfile {
 public static String resolve(String model,String selected){
  if(model!=null && model.matches("SM-F966[A-Z0-9]+(?:/DS)?"))return "fold7";
  if(model!=null && model.matches("SM-F(?:971|976)[A-Z0-9]+(?:/DS)?"))return "fold8";
  if("fold7".equals(selected)||"fold8".equals(selected))return selected;
  throw new IllegalArgumentException("Choose Fold 7 or Fold 8 in setup before applying wallpaper");
 }
 public static String video(String profile){
  if("fold7".equals(profile))return "video_002.mp4";
  if("fold8".equals(profile))return "video_001.mp4";
  throw new IllegalArgumentException("Unknown wallpaper profile");
 }
}
