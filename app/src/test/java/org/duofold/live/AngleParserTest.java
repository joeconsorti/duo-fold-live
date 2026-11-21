package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class AngleParserTest {
 private static final String ACTION="org.duofold.live.wallpaperprobe.READ_12345";
 private static String line(String action,String visible,String angle){
  return "11-22 03:00:00.000  100  200 I SprWallpaper|FoldInteractive: onCommand: action="+action+", isVisible="+visible+", mCurrentAngle="+angle;
 }
 @Test public void parsesOnlyTheRequestedVisibleWallpaperAction(){
  assertEquals(Float.valueOf(42.5f),AngleParser.parse(line(ACTION,"true","42.5"),ACTION));
  assertNull(AngleParser.parse(line(ACTION+"9","true","42.5"),ACTION));
  assertNull(AngleParser.parse(line(ACTION,"false","42.5"),ACTION));
  assertNull(AngleParser.parse(line("OTHER","true","42.5"),ACTION));
 }
 @Test public void acceptsValidEndpointsAndRejectsOutOfRangeValues(){
  assertEquals(Float.valueOf(0f),AngleParser.parse(line(ACTION,"true","0"),ACTION));
  assertEquals(Float.valueOf(180f),AngleParser.parse(line(ACTION,"true","180"),ACTION));
  assertNull(AngleParser.parse(line(ACTION,"true","181"),ACTION));
  assertNull(AngleParser.parse(line(ACTION,"true","-1"),ACTION));
  assertNull(AngleParser.parse(line(ACTION,"true","NaN"),ACTION));
 }
 @Test public void malformedAndNullInputsAreIgnored(){
  assertNull(AngleParser.parse(null,ACTION));
  assertNull(AngleParser.parse(line(ACTION,"true","not-a-number"),ACTION));
  assertNull(AngleParser.parse(line(ACTION,"true","20"),null));
  assertNull(AngleParser.parse(line(ACTION,"true","20"),""));
  assertNull(AngleParser.parse("unrelated log line",ACTION));
 }
 @Test public void supportsSamsungBracketStyleFields(){
  String value="SprWallpaper|FoldInteractive: onCommand: action["+ACTION+"], isVisible[true], mCurrentAngle[91.25]";
  assertEquals(Float.valueOf(91.25f),AngleParser.parse(value,ACTION));
 }
}
