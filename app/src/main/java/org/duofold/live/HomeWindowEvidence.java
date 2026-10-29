package org.duofold.live;
import java.util.regex.*;

/** Additional Home-only rejection of explicitly transitional window evidence.
 * Unity window alpha does not establish the opacity of individual launcher views. */
final class HomeWindowEvidence {
 static String guard(String dump,int task,int display){
  String base=EndpointWindowState.captureGuard(dump,task,display);
  if(!"READY; HAS_DRAWN".equals(base))return base;
  for(String b:dump.split("(?m)(?=^\\s*Window #)")){
   if(!b.trim().startsWith("Window #")||!field(b,"taskId",task)||!field(b,"mDisplayId",display)||!b.contains("isOnScreen=true")||!b.contains("mHasSurface=true")||b.contains("shown=false")||b.contains("mLastHidden=true"))continue;
   if(Pattern.compile("\\b(?:mAnimatingExit|mDestroying|mEnteringAnimation)=true\\b").matcher(b).find())return "HOME_TRANSITION_ACTIVE";
   Matcher alpha=Pattern.compile("\\b(?:mShownAlpha|mAlpha|mLastAlpha)=([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|NaN|Infinity)").matcher(b);
   while(alpha.find())try{if(!Float.isFinite(Float.parseFloat(alpha.group(1)))||Float.parseFloat(alpha.group(1))<.99f)return "HOME_WINDOW_FADED "+alpha.group();}catch(NumberFormatException ignored){}
  }
  return base;
 }
 static boolean field(String b,String key,int value){return Pattern.compile("\\b"+key+"="+value+"\\b").matcher(b).find();}
 static String detail(String dump,int task,int display){
  StringBuilder out=new StringBuilder(guard(dump,task,display));
  for(String b:dump.split("(?m)(?=^\\s*Window #)")){
   if(!b.trim().startsWith("Window #")||!field(b,"taskId",task)||!field(b,"mDisplayId",display))continue;
   for(String line:b.split("\n"))if(line.matches(".*(?:Window #|mAttrs=|mViewVisibility|mHasSurface|mDrawState|mShownAlpha|mAlpha=|mAnimatingExit|mEnteringAnimation|isOnScreen=|isVisible=).*")){
    if(out.length()+line.length()<6000)out.append('\n').append(line.trim());
   }
  }
  return out+"\nWindow evidence only; launcher child-view alpha unmeasured.";
 }
}
