package org.duofold.live.wallpaperlayer;
import java.util.regex.*;
public final class DisplayAreaGuard {
 private static final Pattern LEAF=Pattern.compile("Leaf:(\\d+):(\\d+)");
 public static boolean belowApps(String name){Matcher m=LEAF.matcher(name);return m.find()&&Integer.parseInt(m.group(1))<=1&&Integer.parseInt(m.group(2))==1;}
 public static String check(String dump){
  boolean found=false;int count=0;
  for(String line:dump.split("\\n")){Matcher m=LEAF.matcher(line);if(!m.find())continue;count++;if(line.toLowerCase(java.util.Locale.ROOT).contains("organized"))throw new IllegalStateException("An existing organizer owns a token area: "+line.trim());if(belowApps(line))found=true;}
  if(!found)throw new IllegalStateException("No verified below-app wallpaper area in display hierarchy (leaf areas="+count+")");
  return "Verified unorganized token areas: "+count+"; below-app wallpaper area found";
 }
}
