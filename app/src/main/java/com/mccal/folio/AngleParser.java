package com.mccal.folio;
import java.util.regex.*;
public final class AngleParser {
 public static Float parse(String line,String action){
  if(!line.contains("SprWallpaper|FoldInteractive")||!line.contains("onCommand:")||!(line.contains("action="+action+",")||line.contains("action["+action+"]"))||!(line.contains("isVisible=true")||line.contains("isVisible[true]")))return null;
  Matcher m=Pattern.compile("mCurrentAngle(?:=|\\[)([0-9]+(?:\\.[0-9]+)?)").matcher(line);
  if(!m.find())return null;
  float value=Float.parseFloat(m.group(1));return value>=0&&value<=180?value:null;
 }
}
