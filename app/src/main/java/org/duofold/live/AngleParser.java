package org.duofold.live;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only visible Samsung wallpaper hinge updates for this helper's action token. */
public final class AngleParser {
 private static final Pattern ANGLE = Pattern.compile("mCurrentAngle(?:=|\\[)([0-9]+(?:\\.[0-9]+)?)");

 public static Float parse(String line,String action){
  if(line==null||action==null||action.isEmpty())return null;
  if(!line.contains("SprWallpaper|FoldInteractive")||!line.contains("onCommand:")||
     !(line.contains("action="+action+",")||line.contains("action["+action+"]"))||
     !(line.contains("isVisible=true")||line.contains("isVisible[true]")))return null;
  Matcher m=ANGLE.matcher(line);
  if(!m.find())return null;
  try{
   float value=Float.parseFloat(m.group(1));
   return Float.isFinite(value)&&value>=0f&&value<=180f?value:null;
  }catch(NumberFormatException ignored){return null;}
 }

 private AngleParser(){}
}
