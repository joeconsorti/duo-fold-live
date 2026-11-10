package org.duofold.live;

import java.util.*;
import java.util.regex.*;

/** Bounded, drawn system-bar windows. Fullscreen containers need explicit OS bounds. */
final class SystemBarRegions {
 static final class Region {
  final String kind,window;final int left,top,right,bottom;
  Region(String kind,String window,int l,int t,int r,int b){this.kind=kind;this.window=window;left=l;top=t;right=r;bottom=b;}
  String signature(){return kind+":"+window+":"+left+","+top+","+right+","+bottom;}
 }
 private static final Pattern DISPLAY=Pattern.compile("\\bmDisplayId=(\\d+)\\b");
 private static final Pattern FRAME=Pattern.compile("(?m)^\\s*(?:mFrame|mWindowFrames:\\s*frame|Frames:[^\\n]*?\\bframe)=\\[(-?\\d+),\\s*(-?\\d+)\\]\\[(-?\\d+),\\s*(-?\\d+)\\]");
 private static final Pattern TOUCH=Pattern.compile("(?m)^\\s*touchable region=(SkRegion\\([^\\r\\n]*\\))\\s*$");
 private static final Pattern RECT=Pattern.compile("\\((-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)");
 private static String header(String block){return block.stripLeading().split("\\n",2)[0];}
 private static boolean onDisplay(String block,int display){Matcher id=DISPLAY.matcher(block);return id.find()&&id.group(1).equals(String.valueOf(display));}
 private static String kind(String block){
  String name=header(block);
  if(Pattern.compile("\\bty=(?:STATUS_BAR|2000)\\b").matcher(block).find()&&name.contains("StatusBar"))return "status";
  if(Pattern.compile("(?i)\\bTaskbar(?:Window)?\\d*\\b").matcher(name).find()&&Pattern.compile("\\bty=(?:NAVIGATION_BAR|NAVIGATION_BAR_PANEL|2019|2024)\\b").matcher(block).find())return "taskbar";
  if(Pattern.compile("\\bty=(?:NAVIGATION_BAR|2019)\\b").matcher(block).find()&&name.contains("NavigationBar"))return "navigation";
  return null;
 }
 private static String readiness(String block){
  if(!block.contains("mHasSurface=true"))return "no-surface";
  if(!block.contains("isOnScreen=true")||!Pattern.compile("\\bshown=true\\b").matcher(block).find()||Pattern.compile("\\bshown=false\\b|\\bmLastHidden=true\\b|\\bmPolicyVisibility=false\\b").matcher(block).find())return "hidden";
  if(!block.contains("isReadyForDisplay()=true")||!Pattern.compile("\\bmDrawState=HAS_DRAWN\\b").matcher(block).find())return "not-drawn";
  if(Pattern.compile("\\b(?:mAnimatingExit|mDestroying|mEnteringAnimation)=true\\b").matcher(block).find()||faded(block))return "animating-or-faded";
  return "ready";
 }
 private static int[] rect(Matcher m){try{return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4))};}catch(NumberFormatException e){return null;}}
 private static boolean inside(int[] b,int w,int h){return b!=null&&b[0]>=0&&b[1]>=0&&b[2]<=w&&b[3]<=h&&b[2]>b[0]&&b[3]>b[1];}
 private static boolean bounded(int[] b,String kind,int w,int h){
  if(!inside(b,w,h))return false;
  if(kind.equals("status"))return b[1]==0&&b[3]<=h/8;
  boolean horizontal=(b[1]==0||b[3]==h)&&b[3]-b[1]<=h/4;
  boolean vertical=(b[0]==0||b[2]==w)&&b[2]-b[0]<=w/8;
  boolean floatingTaskbar=kind.equals("taskbar")&&b[1]>=h-h/4;
  return horizontal||vertical||floatingTaskbar;
 }
 private static List<Region> regions(String block,int w,int h){
  List<Region> out=new ArrayList<>();String kind=kind(block),name=header(block);
  if(kind==null||!name.contains("Window{")||!readiness(block).equals("ready"))return out;
  Matcher frame=FRAME.matcher(block);if(!frame.find())return out;int[] b=rect(frame);
  if(!inside(b,w,h))return out;
  String window=name.substring(name.indexOf("Window{"));
  if(bounded(b,kind,w,h)){out.add(new Region(kind,window,b[0],b[1],b[2],b[3]));return out;}
  // Taskbar/nav containers may fill the screen for drag/folder handling. Accept
  // only explicit global touchable bounds in the bottom quarter, never the
  // fullscreen frame or an estimated dp strip. Touchability is not icon proof.
  if(kind.equals("status")||!Pattern.compile("\\bmTouchableInsets=3\\b").matcher(block).find()||!block.contains("mGivenInsetsPending=false"))return out;
  Matcher touch=TOUCH.matcher(block);if(!touch.find())return out;
  String body=touch.group(1).substring(9,touch.group(1).length()-1);
  Matcher pieces=RECT.matcher(body);int end=0;
  while(pieces.find()){
   if(!body.substring(end,pieces.start()).trim().isEmpty()||out.size()>=32)return Collections.emptyList();
   int[] p=rect(pieces);
   if(!inside(p,w,h)||p[0]<b[0]||p[1]<b[1]||p[2]>b[2]||p[3]>b[3]||p[1]<h-h/4)return Collections.emptyList();
   out.add(new Region(kind,window+":touchable",p[0],p[1],p[2],p[3]));end=pieces.end();
  }
  return body.substring(end).trim().isEmpty()?out:Collections.emptyList();
 }
 static List<Region> parse(String dump,int display,int width,int height){
  List<Region> result=new ArrayList<>();if(display<0||display>1||width<=0||height<=0)return result;
  for(String block:dump.split("(?m)(?=^\\s*Window #)"))if(block.trim().startsWith("Window #")&&onDisplay(block,display))result.addAll(regions(block,width,height));
  // Union overlapping nav/taskbar pixels so transparent backgrounds are not
  // painted twice. Re-run after every union to handle chains of rectangles.
  result.sort(Comparator.comparingInt((Region r)->r.top).thenComparingInt(r->r.left).thenComparing(r->r.window));
  List<Region> merged=new ArrayList<>();
  for(Region next:result){
   Region r=next;
   for(int i=0;i<merged.size();){Region a=merged.get(i);
    if(edge(a,width,height).equals(edge(r,width,height))&&a.left<r.right&&r.left<a.right&&a.top<r.bottom&&r.top<a.bottom){
     r=new Region(a.kind+"+"+r.kind,a.window+"|"+r.window,Math.min(a.left,r.left),Math.min(a.top,r.top),Math.max(a.right,r.right),Math.max(a.bottom,r.bottom));merged.remove(i);i=0;
    }else i++;
   }
   merged.add(r);
  }
  return merged;
 }
 private static boolean faded(String block){
  Matcher alpha=Pattern.compile("\\b(?:mShownAlpha|mAlpha|mLastAlpha)=([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|NaN|Infinity)").matcher(block);
  while(alpha.find())try{float value=Float.parseFloat(alpha.group(1));if(!Float.isFinite(value)||value<.99f)return true;}catch(NumberFormatException e){return true;}
  return false;
 }
 static String evidence(String dump,int display,int width,int height){
  StringBuilder out=new StringBuilder();boolean taskbar=false;
  for(String block:dump.split("(?m)(?=^\\s*Window #)")){
   if(!block.trim().startsWith("Window #")||!onDisplay(block,display))continue;
   String name=header(block);if(!Pattern.compile("(?i)StatusBar|NavigationBar|Taskbar").matcher(name).find())continue;
   taskbar|=Pattern.compile("(?i)Taskbar").matcher(name).find();if(out.length()>900)continue;
   Matcher frame=FRAME.matcher(block);String kind=kind(block);List<Region> accepted=regions(block,width,height);
   out.append(name.substring(Math.max(0,name.indexOf("Window{")))).append(" ").append(kind==null?"unsupported-type-or-name":readiness(block))
      .append(" frame=").append(frame.find()?frame.group(0).trim():"unreported")
      .append(" accepted=").append(accepted.size())
      .append(accepted.isEmpty()?(block.contains("mGivenInsetsPending=true")?" pending-insets":" no-bounded-drawn-region"):accepted.get(0).window.endsWith(":touchable")?" basis=OS-touchable-bounds":" basis=window-frame").append("; ");
  }
  if(!taskbar)out.append("no named taskbar on D").append(display).append("; navigation may share its container");
  return out.toString();
 }
 static String signature(List<Region> regions){List<String> values=new ArrayList<>();for(Region r:regions)values.add(r.signature());Collections.sort(values);return String.join(";",values);}
 private static String edge(Region r,int width,int height){
  if(r.top==0&&r.bottom-r.top<=height/4)return "top";
  if(r.top>=height-height/4)return "bottom";
  return r.left==0?"left":"right";
 }
 static List<Region> stable(List<Region> before,List<Region> after){
  Set<String> accepted=new HashSet<>();for(Region r:after)accepted.add(r.signature());
  List<Region> result=new ArrayList<>();for(Region r:before)if(accepted.contains(r.signature()))result.add(r);return result;
 }
}
