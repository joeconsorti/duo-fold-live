package org.duofold.live;
import java.util.regex.*;
/** Exact-task WindowManager evidence, never optical/first-buffer proof. */
final class EndpointWindowState {
 static String summarize(String dump,int task){
  boolean found=false;
  for(String block:dump.split("(?m)(?=^\\s*Window #)")){
   if(!block.trim().startsWith("Window #"))continue;
   Matcher display=Pattern.compile("\\bmDisplayId=(\\d+)\\b").matcher(block),id=Pattern.compile("\\btaskId=(\\d+)\\b").matcher(block);
   if(!display.find()||!display.group(1).equals("0")||!id.find()||!id.group(1).equals(String.valueOf(task)))continue;
   found=true;
   if(block.contains("mHasSurface=true")&&block.contains("isReadyForDisplay()=true")&&block.contains("isOnScreen=true"))return "READY"+(block.contains("HAS_DRAWN")?"; HAS_DRAWN":"; draw-state unreported");
  }
  return found?"NOT_READY":"NO_MATCHING_D0_WINDOW";
 }
}
