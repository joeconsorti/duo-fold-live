package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SystemBarRegionsTest {
 private String window(String name,String type,int display,String frame){return "  Window #12 Window{abc u0 "+name+"}:\n mDisplayId="+display+"\n mAttrs={ty="+type+"}\n mHasSurface=true isReadyForDisplay()=true\n Surface: shown=true mDrawState=HAS_DRAWN mLastHidden=false\n isOnScreen=true\n mWindowFrames: frame="+frame+" display=[0,0][1200,2000]\n";}
 private String status(){return window("StatusBar","STATUS_BAR",0,"[0,0][1200,72]");}
 private String nav(){return window("NavigationBar0","NAVIGATION_BAR",0,"[0,1920][1200,2000]");}
 @Test public void capturesExactDisplayTopAndBottom(){
  var regions=SystemBarRegions.parse(status()+nav(),0,1200,2000);assertEquals(2,regions.size());assertEquals("status",regions.get(0).kind);assertEquals(1920,regions.get(1).top);
  assertTrue(SystemBarRegions.parse(status()+nav(),1,1200,2000).isEmpty());
 }
 @Test public void refusesShadeAndAppImpersonation(){
  assertTrue(SystemBarRegions.parse(window("NotificationShade","NOTIFICATION_SHADE",0,"[0,0][1200,2000]"),0,1200,2000).isEmpty());
  assertTrue(SystemBarRegions.parse(window("StatusBar","BASE_APPLICATION",0,"[0,0][1200,72]"),0,1200,2000).isEmpty());
 }
 @Test public void hiddenImmersiveBarsAreNotInvented(){
  for(String hidden:new String[]{status().replace("shown=true","shown=false"),status().replace("isOnScreen=true","isOnScreen=false"),status()+" mPolicyVisibility=false",status().replace("HAS_DRAWN","DRAW_PENDING"),status().replace("isReadyForDisplay()=true","isReadyForDisplay()=false")})assertTrue(SystemBarRegions.parse(hidden,0,1200,2000).isEmpty());
 }
 @Test public void fadingOrExitingNativeBarsCannotReplaceSourceFallback(){
  for(String flag:new String[]{"mAlpha=0.0","mShownAlpha=0.5","mLastAlpha=NaN","mAnimatingExit=true","mDestroying=true","mEnteringAnimation=true"})assertTrue(SystemBarRegions.parse(status()+flag,0,1200,2000).isEmpty());
  assertEquals(1,SystemBarRegions.parse(status()+"mAlpha=1.0",0,1200,2000).size());
 }
 @Test public void rejectsFullScreenTaskbarAndInvalidBounds(){
  for(String frame:new String[]{"[0,0][1200,2000]","[-1,0][1200,72]","[0,0][1201,72]","[0,1900][0,2000]"})assertTrue(SystemBarRegions.parse(window("Taskbar","NAVIGATION_BAR_PANEL",0,frame),0,1200,2000).isEmpty());
 }
 @Test public void overlappingTaskbarAndNavigationAreCapturedOnce(){
  var regions=SystemBarRegions.parse(nav()+window("Taskbar","NAVIGATION_BAR_PANEL",0,"[0,1800][1200,2000]"),0,1200,2000);
  assertEquals(1,regions.size());assertEquals(1800,regions.get(0).top);assertTrue(regions.get(0).kind.contains("taskbar"));
 }
 @Test public void acceptsSideNavigationAndNumericTypes(){
  var regions=SystemBarRegions.parse(window("NavigationBar0","2019",0,"[1120,0][1200,2000]")+status().replace("STATUS_BAR","2000"),0,1200,2000);
  assertEquals(2,regions.size());
 }
 @Test public void stableWindowIgnoresDumpOrderingButRejectsRecreationOrMovement(){
  var before=SystemBarRegions.parse(status(),0,1200,2000);
  assertEquals(1,SystemBarRegions.stable(before,SystemBarRegions.parse(status().replace("#12","#27"),0,1200,2000)).size());
  assertTrue(SystemBarRegions.stable(before,SystemBarRegions.parse(status().replace("abc","def"),0,1200,2000)).isEmpty());
  assertTrue(SystemBarRegions.stable(before,SystemBarRegions.parse(status().replace("1200,72","1200,80"),0,1200,2000)).isEmpty());
 }
 @Test public void parsesAndroidFramesLineWithoutUsingParentDisplayOrInsetFrames(){
  String dump=status().replace("mWindowFrames: frame=[0,0][1200,72] display=[0,0][1200,2000]", "Frames: parent=[0,0][1200,2000] display=[0,0][1200,2000] frame=[0,0][1200,72] last=[0,0][1200,72] insetsChanged=false");
  dump=dump.replace("mHasSurface=true", "mAttrs providedInsets=[frame=[0,0][1200,2000]]\n mHasSurface=true");
  var regions=SystemBarRegions.parse(dump,0,1200,2000);assertEquals(1,regions.size());assertEquals(72,regions.get(0).bottom);
 }
 @Test public void supportsOldFrameSpellingAndRejectsMissingEvidence(){
  assertEquals(1,SystemBarRegions.parse(status().replace("mWindowFrames: frame=","mFrame="),0,1200,2000).size());
  assertTrue(SystemBarRegions.parse(status().replace("mDisplayId=0",""),0,1200,2000).isEmpty());
  assertTrue(SystemBarRegions.parse(status().replace("frame=","unknown="),0,1200,2000).isEmpty());
 }
 private String fullscreenTaskbar(String region){return window("TaskbarWindow","NAVIGATION_BAR_PANEL",0,"[0,0][1200,2000]")+" mTouchableInsets=3 mGivenInsetsPending=false\n touchable region=SkRegion("+region+")\n";}
 @Test public void fullscreenContainerUsesOnlyExplicitBottomTouchableRegion(){
  var regions=SystemBarRegions.parse(fullscreenTaskbar("(180,1800,1020,1950)"),0,1200,2000);
  assertEquals(1,regions.size());assertEquals("taskbar",regions.get(0).kind);assertEquals(180,regions.get(0).left);assertEquals(1800,regions.get(0).top);assertEquals(1950,regions.get(0).bottom);
  assertTrue(regions.get(0).window.endsWith(":touchable"));
  String evidence=SystemBarRegions.evidence(fullscreenTaskbar("(180,1800,1020,1950)"),0,1200,2000);
  assertTrue(evidence.contains("accepted=1"));assertTrue(evidence.contains("basis=OS-touchable-bounds"));
 }
 @Test public void fullscreenNavigationContainerMayHoldTaskbarWithoutSeparateWindow(){
  String dump=fullscreenTaskbar("(100,1800,1100,1950)(0,1950,1200,2000)").replace("TaskbarWindow","NavigationBar0").replace("NAVIGATION_BAR_PANEL","NAVIGATION_BAR");
  var regions=SystemBarRegions.parse(dump,0,1200,2000);assertEquals(2,regions.size());assertEquals("navigation",regions.get(0).kind);
 }
 @Test public void touchableRegionsCannotCopyFullscreenAppContent(){
  for(String region:new String[]{"(0,0,1200,2000)","(100,1400,1100,1900)","(-1,1800,1000,2000)","(100,1800,1201,2000)","(100,1800,100,1900)","(9999999999,1800,1100,2000)","(100,1800,1100,2000)garbage",""})assertTrue(region,SystemBarRegions.parse(fullscreenTaskbar(region),0,1200,2000).isEmpty());
 }
 @Test public void touchRegionRequiresReadyInsetsAndDrawnWindowOnExactDisplay(){
  String dump=fullscreenTaskbar("(180,1800,1020,1950)");
  for(String rejected:new String[]{dump.replace("mTouchableInsets=3","mTouchableInsets=0"),dump.replace("mGivenInsetsPending=false","mGivenInsetsPending=true"),dump.replace("shown=true","shown=false"),dump.replace("HAS_DRAWN","DRAW_PENDING"),dump.replace("NAVIGATION_BAR_PANEL","BASE_APPLICATION")})assertTrue(SystemBarRegions.parse(rejected,0,1200,2000).isEmpty());
  assertTrue(SystemBarRegions.parse(dump,1,1200,2000).isEmpty());
  assertTrue(SystemBarRegions.evidence(dump.replace("mGivenInsetsPending=false","mGivenInsetsPending=true"),0,1200,2000).contains("pending-insets"));
 }
 @Test public void stableTouchableBoundsRejectResizeAndRecreation(){
  String dump=fullscreenTaskbar("(180,1800,1020,1950)");var before=SystemBarRegions.parse(dump,0,1200,2000);
  assertEquals(1,SystemBarRegions.stable(before,SystemBarRegions.parse(dump,0,1200,2000)).size());
  assertTrue(SystemBarRegions.stable(before,SystemBarRegions.parse(dump.replace("1800","1850"),0,1200,2000)).isEmpty());
  assertTrue(SystemBarRegions.stable(before,SystemBarRegions.parse(dump.replace("abc","def"),0,1200,2000)).isEmpty());
 }
 @Test public void acceptsFloatingNamedTaskbarButNeverFloatingAppWindow(){
  assertEquals(1,SystemBarRegions.parse(window("TaskbarWindow0","2024",0,"[180,1800][1020,1950]"),0,1200,2000).size());
  assertTrue(SystemBarRegions.parse(window("SomeTaskbarApp","2024",0,"[180,1800][1020,1950]"),0,1200,2000).isEmpty());
 }
 @Test public void taskbarAbsenceAndHiddenStateAreVisibleEvenWhenStatusSucceeds(){
  assertTrue(SystemBarRegions.evidence(status(),0,1200,2000).contains("no named taskbar on D0"));
  String evidence=SystemBarRegions.evidence(status()+fullscreenTaskbar("(180,1800,1020,1950)").replace("shown=true","shown=false"),0,1200,2000);
  assertTrue(evidence.contains("TaskbarWindow"));assertTrue(evidence.contains("hidden"));
 }
 @Test public void signatureIgnoresWindowOrderingButIncludesTaskbarAppearance(){
  assertEquals(SystemBarRegions.signature(SystemBarRegions.parse(status()+nav(),0,1200,2000)),SystemBarRegions.signature(SystemBarRegions.parse(nav()+status(),0,1200,2000)));
  assertNotEquals(SystemBarRegions.signature(SystemBarRegions.parse(status(),0,1200,2000)),SystemBarRegions.signature(SystemBarRegions.parse(status()+fullscreenTaskbar("(180,1800,1020,1950)"),0,1200,2000)));
 }
}
