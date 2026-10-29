package org.duofold.live;
import java.util.regex.*;
/** Exact-task WindowManager evidence, never optical/first-buffer proof. */
final class EndpointWindowState {
 /** Reject competing on-screen task windows, even while the selected task is HAS_DRAWN.
  * This is a conservative window-state guard, not task-isolated pixel capture. */
 static String captureGuard(String dump,int task,int displayId){
  String ready=summarize(dump,task,displayId);
  if(!ready.equals("READY; HAS_DRAWN"))return ready;
  for(String block:dump.split("(?m)(?=^\\s*Window #)")){
   if(!block.trim().startsWith("Window #"))continue;
   Matcher display=Pattern.compile("\\bmDisplayId=(\\d+)\\b").matcher(block);
   Matcher id=Pattern.compile("\\btaskId=(-?\\d+)\\b").matcher(block);
   if(!display.find()||Integer.parseInt(display.group(1))!=displayId||!id.find())continue;
   int other=Integer.parseInt(id.group(1));
   if(other>=0&&other!=task&&block.contains("mHasSurface=true")&&block.contains("isOnScreen=true"))
    return "COMPETING_TASK selected="+task+" other="+other+" display="+displayId;
  }
  return "READY; HAS_DRAWN";
 }
 /** All selected panes must be ready; an unselected visible task still rejects capture. */
 static String workspaceGuard(String dump,int[] tasks,int displayId){
  if(tasks==null||tasks.length==0||tasks.length>8)return "INVALID_WORKSPACE";
  java.util.Set<Integer> selected=new java.util.HashSet<>();
  for(int task:tasks){
   if(task<0||!selected.add(task))return "INVALID_WORKSPACE";
   String ready=summarize(dump,task,displayId);
   if(!ready.equals("READY; HAS_DRAWN"))return "TASK "+task+": "+ready;
  }
  for(String block:dump.split("(?m)(?=^\\s*Window #)")){
   if(!block.trim().startsWith("Window #"))continue;
   Matcher display=Pattern.compile("\\bmDisplayId=(\\d+)\\b").matcher(block),id=Pattern.compile("\\btaskId=(-?\\d+)\\b").matcher(block);
   if(!display.find()||Integer.parseInt(display.group(1))!=displayId||!id.find())continue;
   int other=Integer.parseInt(id.group(1));
   if(other>=0&&!selected.contains(other)&&block.contains("mHasSurface=true")&&block.contains("isOnScreen=true"))return "COMPETING_TASK other="+other;
  }
  return "READY; HAS_DRAWN";
 }
 static String summarize(String dump,int task){
  return summarize(dump,task,0);
 }
 static String summarize(String dump,int task,int displayId){
  boolean found=false,ready=false,drawn=false;
  for(String block:dump.split("(?m)(?=^\\s*Window #)")){
   if(!block.trim().startsWith("Window #"))continue;
   Matcher display=Pattern.compile("\\bmDisplayId=(\\d+)\\b").matcher(block),id=Pattern.compile("\\btaskId=(\\d+)\\b").matcher(block);
   if(!display.find()||!display.group(1).equals(String.valueOf(displayId))||!id.find()||!id.group(1).equals(String.valueOf(task)))continue;
   found=true;
   boolean visible=block.contains("mHasSurface=true")&&block.contains("isOnScreen=true");
   // A starting surface can report HAS_DRAWN while the underlying app is not ready.
   // Examine all matching windows so ordering cannot hide a visible splash.
   boolean starting=block.contains("Splash Screen")||block.contains("Starting ")||
       Pattern.compile("\\b(?:ty|type)=(?:APPLICATION_STARTING|3)\\b").matcher(block).find();
   if(visible&&starting)return "STARTING_WINDOW";
   // Explicitly hidden surfaces can still have HAS_DRAWN and logical visibility.
   boolean hidden=Pattern.compile("\\bshown=false\\b|\\bmLastHidden=true\\b|\\bmPolicyVisibility=false\\b").matcher(block).find();
   if(visible&&!hidden&&block.contains("isReadyForDisplay()=true")){ready=true;drawn|=block.contains("HAS_DRAWN");}
  }
  if(ready)return drawn?"READY; HAS_DRAWN":"READY; draw-state unreported";
  return found?"NOT_READY":"NO_MATCHING_D"+displayId+"_WINDOW";
 }
}
