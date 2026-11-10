package org.duofold.live;

import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Registered wallpaper window identity, used only for pre/post stability guards.
 * Its owner UID does not describe every photo layer in the visible composition. */
final class HomeWallpaperIdentity {
 final long uid;final String windows;
 private HomeWallpaperIdentity(long uid,String windows){this.uid=uid;this.windows=windows;}
 static HomeWallpaperIdentity read(String dump,int display,long launcherUid){
  return read(EndpointWindowState.blocks(dump),display,launcherUid);
 }
 static HomeWallpaperIdentity read(String[] windows,int display,long launcherUid){
  long owner=-1;TreeSet<String> selected=new TreeSet<>();
  for(String block:windows){
   if(!block.trim().startsWith("Window #")||number(block,"mDisplayId")!=display||
      !Pattern.compile("\\bmIsWallpaper=true\\b|\\bty=(?:WALLPAPER|2013)\\b").matcher(block).find()||
      !block.contains("mHasSurface=true")||!block.contains("isOnScreen=true"))continue;
   if(Pattern.compile("\\bshown=false\\b|\\bmLastHidden=true\\b|\\bmPolicyVisibility=false\\b").matcher(block).find())continue;
   if(!block.contains("isReadyForDisplay()=true")||!Pattern.compile("\\bmDrawState=HAS_DRAWN\\b").matcher(block).find())
    throw new IllegalStateException("HOME_WALLPAPER_DRAW_PENDING");
   long uid=number(block,"mOwnerUid");
   if(uid<0||uid==launcherUid)throw new IllegalStateException("HOME_WALLPAPER_OWNER_UNSAFE");
   if(owner>=0&&owner!=uid)throw new IllegalStateException("HOME_WALLPAPER_OWNER_AMBIGUOUS");
   Matcher token=Pattern.compile("Window\\{([^\\s}]+)").matcher(block);
   if(!token.find())throw new IllegalStateException("HOME_WALLPAPER_ID_UNAVAILABLE");
   owner=uid;selected.add(token.group(1));
  }
  if(owner<0||selected.isEmpty())throw new IllegalStateException("HOME_WALLPAPER_UNAVAILABLE");
  return new HomeWallpaperIdentity(owner,String.join(",",selected));
 }
 private static long number(String block,String field){
  Matcher m=Pattern.compile("\\b"+field+"=(-?\\d+)\\b").matcher(block);
  return m.find()?Long.parseLong(m.group(1)):-1;
 }
 void requireSame(HomeWallpaperIdentity after){
  if(after==null||uid!=after.uid||!windows.equals(after.windows))throw new IllegalStateException("HOME_WALLPAPER_CHANGED_DURING_CAPTURE");
 }
}
