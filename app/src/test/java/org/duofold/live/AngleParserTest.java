package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class AngleParserTest {
 // The hot-path parser: precompiled pattern, per-action token cache.
 private static final String A="org.duofold.live.wallpaperprobe.READ_1";
 private static String bracket(String action,String angle,boolean visible){
  return "I SprWallpaper|FoldInteractive: onCommand: action["+action+"], mCurrentAngle["+angle+"], isVisible["+visible+"]";
 }
 @Test public void parsesTheBracketForm(){assertEquals(93f,AngleParser.parse(bracket(A,"93.000000",true),A),0);}
 @Test public void parsesTheEqualsForm(){
  assertEquals(12.5f,AngleParser.parse("SprWallpaper|FoldInteractive onCommand: action="+A+", mCurrentAngle=12.5 isVisible=true",A),0);
 }
 @Test public void rejectsInvisibleSamples(){assertNull(AngleParser.parse(bracket(A,"93.000000",false),A));}
 @Test public void rejectsOtherActionsAndResetsTheCache(){
  String b="org.duofold.live.wallpaperprobe.READ_2";
  assertNull(AngleParser.parse(bracket(A,"40",true),b));
  assertEquals(40f,AngleParser.parse(bracket(b,"40",true),b),0);
  assertEquals(41f,AngleParser.parse(bracket(A,"41",true),A),0);   // switching back re-derives the tokens
 }
 @Test public void rejectsOutOfRangeAndForeignLines(){
  assertNull(AngleParser.parse(bracket(A,"181",true),A));
  assertNull(AngleParser.parse("I Other: onCommand: action["+A+"], mCurrentAngle[10], isVisible[true]",A));
 }
}
