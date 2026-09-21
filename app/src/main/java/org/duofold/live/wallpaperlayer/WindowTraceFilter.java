package org.duofold.live.wallpaperlayer;
import java.io.*;
/** Retain only our photo and the Samsung hinge-wallpaper window blocks. */
public final class WindowTraceFilter {
 public static String read(Reader input)throws IOException {
  BufferedReader reader=new BufferedReader(input);StringBuilder out=new StringBuilder();String line;boolean relevant=false;int matches=0,totalLines=0;StringBuilder preview=new StringBuilder();
  while((line=reader.readLine())!=null){
   totalLines++;if(preview.length()<4000)preview.append(line).append('\n');
   if(line.trim().startsWith("Window #") || line.trim().startsWith("Window{")){
    relevant=line.contains("Duo persistent wallpaper photo")||line.contains("FoldInteractive");
    if(relevant){matches++;if(out.length()<16000)out.append(line).append('\n');}continue;
   }
   if(relevant && out.length()<16000 && (line.contains("mAttrs=")||line.contains("mHasSurface")||line.contains("mViewVisibility")||line.contains("isVisible")||line.contains("isOnScreen")||line.contains("mPolicyVisibility")||line.contains("mDrawState")||line.contains("mShown")||line.contains("mHidden")||line.contains("mFrame=")||line.contains("mSurface")||line.contains("mToken=")||line.contains("mWinAnimator")||line.contains("mLastAlpha")||line.contains("mWallpaper")))out.append(line).append('\n');
  }
  return "Command: dumpsys -t 2 window windows\nResponse lines: "+totalLines+"\nMatching window blocks: "+matches+"\n"+out+(matches==0?"Unmatched command response (first 4000 characters):\n"+(preview.length()==0?"<empty stdout>":preview.substring(0,Math.min(4000,preview.length()))):"");
 }
}
