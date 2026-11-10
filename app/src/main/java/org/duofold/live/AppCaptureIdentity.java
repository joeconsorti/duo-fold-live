package org.duofold.live;

import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owner filtering supplements the exact-task guard; it is not task-isolated pixel proof.
 * Reuses the two existing window dumps, with no new polling or settling delay. */
final class AppCaptureIdentity {
 final long uid;
 final String windows;
 private AppCaptureIdentity(long uid,String windows){this.uid=uid;this.windows=windows;}
 static AppCaptureIdentity read(String dump,int task,int display){
  String[] windows=EndpointWindowState.blocks(dump);
  String guard=EndpointWindowState.captureGuard(windows,task,display);
  if(!"READY; HAS_DRAWN".equals(guard))throw new IllegalStateException(guard);
  return readGuarded(windows,task,display);
 }
 // Called only after the same window blocks passed the exact-task visibility guard.
 static AppCaptureIdentity readGuarded(String[] windows,int task,int display){
  long owner=-1;TreeSet<String> selected=new TreeSet<>();
  for(String block:windows){
   if(!block.trim().startsWith("Window #")||number(block,"mDisplayId")!=display||number(block,"taskId")!=task||
      !block.contains("mHasSurface=true")||!block.contains("isOnScreen=true"))continue;
   if(Pattern.compile("\\bshown=false\\b|\\bmLastHidden=true\\b|\\bmPolicyVisibility=false\\b").matcher(block).find())continue;
   if(!block.contains("isReadyForDisplay()=true")||!Pattern.compile("\\bmDrawState=HAS_DRAWN\\b").matcher(block).find())
    throw new IllegalStateException("APP_WINDOW_DRAW_PENDING");
   Matcher alpha=Pattern.compile("\\bmShownAlpha=([^\\s]+)").matcher(block);
   if(alpha.find()){
    float shown=Float.parseFloat(alpha.group(1));
    if(!Float.isFinite(shown)||shown<=0)throw new IllegalStateException("APP_WINDOW_INVISIBLE");
   }
   long uid=number(block,"mOwnerUid");
   if(uid<0)throw new IllegalStateException("APP_OWNER_UNAVAILABLE");
   if(owner>=0&&owner!=uid)throw new IllegalStateException("APP_OWNER_AMBIGUOUS");
   Matcher token=Pattern.compile("Window\\{([^\\s}]+)").matcher(block);
   if(!token.find())throw new IllegalStateException("APP_WINDOW_ID_UNAVAILABLE");
   owner=uid;selected.add(token.group(1));
  }
  if(owner<0||selected.isEmpty())throw new IllegalStateException("APP_OWNER_UNAVAILABLE");
  return new AppCaptureIdentity(owner,String.join(",",selected));
 }
 private static long number(String block,String field){
  Matcher m=Pattern.compile("\\b"+field+"=(-?\\d+)\\b").matcher(block);
  return m.find()?Long.parseLong(m.group(1)):-1;
 }
 void requireSame(AppCaptureIdentity after){
  if(after==null||uid!=after.uid||!windows.equals(after.windows))throw new IllegalStateException("APP_WINDOWS_CHANGED_DURING_CAPTURE");
 }
}
