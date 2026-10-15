package org.duofold.live;
import java.util.regex.*;
public final class AngleParser {
 // ~640 wallpaper lines/s reach this while polling: compile once, build the action tokens once per action.
 private static final Pattern ANGLE=Pattern.compile("mCurrentAngle(?:=|\\[)([0-9]+(?:\\.[0-9]+)?)");
 // One immutable snapshot, published atomically: a reader restart can briefly overlap two parsing threads, and
 // three separately written fields could pair one action's name with another action's tokens.
 private static final class Tokens{final String action,eq,bracket;Tokens(String a){action=a;eq="action="+a+",";bracket="action["+a+"]";}}
 private static volatile Tokens cache;
 public static Float parse(String line,String action){
  if(!line.contains("SprWallpaper|FoldInteractive")||!line.contains("onCommand:"))return null;
  Tokens t=cache;
  if(t==null||!action.equals(t.action))cache=t=new Tokens(action);
  if(!(line.contains(t.eq)||line.contains(t.bracket))||!(line.contains("isVisible=true")||line.contains("isVisible[true]")))return null;
  Matcher m=ANGLE.matcher(line);
  if(!m.find())return null;
  float value=Float.parseFloat(m.group(1));return value>=0&&value<=180?value:null;
 }
}
