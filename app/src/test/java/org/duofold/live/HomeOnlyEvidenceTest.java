package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeOnlyEvidenceTest {
 private String window(int task,int display){return "Window #1 Window{home}:\n mDisplayId="+display+" taskId="+task+"\n mHasSurface=true isReadyForDisplay()=true\n isOnScreen=true mDrawState=HAS_DRAWN\n";}
 @Test public void homeRejectsFadedDrawnWindowButAppGuardIsUnchanged(){
  for(String alpha:new String[]{"mAlpha=0.5","mShownAlpha=0.0","mLastAlpha=0.75","mShownAlpha=1.0E-4","mAlpha=NaN"}){
   String dump=window(42,0)+alpha;
   assertEquals("READY; HAS_DRAWN",EndpointWindowState.captureGuard(dump,42,0));
   assertTrue(HomeWindowEvidence.guard(dump,42,0).startsWith("HOME_WINDOW_FADED"));
  }
 }
 @Test public void nativeUnityAlphaAndUnreportedAlphaRetainExistingReadiness(){
  assertEquals("READY; HAS_DRAWN",HomeWindowEvidence.guard(window(42,0),42,0));
  assertEquals("READY; HAS_DRAWN",HomeWindowEvidence.guard(window(42,0)+"mShownAlpha=1.0 mAlpha=1.0 mLastAlpha=1.0",42,0));
  assertTrue(HomeWindowEvidence.detail(window(42,0),42,0).contains("child-view alpha unmeasured"));
 }
 @Test public void transitionalHomeDoesNotAcceptDrawnAsComplete(){
  for(String field:new String[]{"mAnimatingExit","mDestroying","mEnteringAnimation"})
   assertEquals("HOME_TRANSITION_ACTIVE",HomeWindowEvidence.guard(window(42,0)+field+"=true",42,0));
 }
 @Test public void onlySelectedVisibleHomeWindowContributesAlpha(){
  assertEquals("READY; HAS_DRAWN",HomeWindowEvidence.guard(window(42,0)+window(42,1)+"mAlpha=0.0",42,0));
  assertEquals("READY; HAS_DRAWN",HomeWindowEvidence.guard(window(42,0)+window(42,0).replace("isOnScreen=true","isOnScreen=false")+"mAlpha=0.0",42,0));
  assertEquals("READY; HAS_DRAWN",HomeWindowEvidence.guard(window(42,0)+window(42,0)+"shown=false mAlpha=0.0",42,0));
  assertTrue(HomeWindowEvidence.guard(window(42,0)+window(43,0),42,0).startsWith("COMPETING_TASK"));
  assertEquals("NO_MATCHING_D0_WINDOW",HomeWindowEvidence.guard(window(43,0),42,0));
 }
 @Test public void crossPanelCropIsHomeOnlyAndPreservesNativeLayouts(){
  for(int[] size:new int[][]{{1248,1972,2448,1848},{2448,1848,1248,1972},{1972,1248,1848,2448},{1848,2448,1972,1248}}){
   assertFalse(HomeProjectionPolicy.project(false,size[0],size[1],size[2],size[3]));
   assertTrue(HomeProjectionPolicy.project(true,size[0],size[1],size[2],size[3]));
   float[] crop=LiveMirrorLayout.fill(size[0],size[1],size[2],size[3]);
   assertTrue(crop[1]<=.01f&&crop[2]<=.01f);
   assertTrue(crop[1]+size[0]*crop[0]>=size[2]-.01f);
   assertTrue(crop[2]+size[1]*crop[0]>=size[3]-.01f);
  }
  assertFalse(HomeProjectionPolicy.project(true,1248,1972,1248,1972));
  assertFalse(HomeProjectionPolicy.project(true,2448,1848,2448,1848));
 }
 @Test public void endpointBudgetSurvivesLongOrLargeMovingSequence(){
  HomeInnerBudget frames=new HomeInnerBudget();for(int i=0;i<240;i++){assertTrue(frames.available(false));frames.attempt(false);}
  assertFalse(frames.available(false));assertTrue(frames.available(true));
  for(int i=0;i<8;i++){assertTrue(frames.available(true));frames.attempt(true);}assertFalse(frames.available(true));
  HomeInnerBudget bytes=new HomeInnerBudget();bytes.saved(false,HomeInnerBudget.MOVING_BYTES);assertFalse(bytes.available(false));assertTrue(bytes.available(true));
  bytes.saved(true,HomeInnerBudget.TAIL_BYTES);assertFalse(bytes.available(true));
 }
 @Test public void diagnosticChoosesPhysicalInnerInEitherRotation(){
  assertTrue(HomeInnerBudget.inner(2448,1848));assertTrue(HomeInnerBudget.inner(1848,2448));
  assertFalse(HomeInnerBudget.inner(1248,1972));assertFalse(HomeInnerBudget.inner(1972,1248));assertFalse(HomeInnerBudget.inner(0,0));
 }
 public static class WindowConfiguration {public int type=2,mode=1;public int getActivityType(){return type;}public int getWindowingMode(){return mode;}}
 public static class Configuration {public WindowConfiguration windowConfiguration=new WindowConfiguration();}
 public static class Root {public Configuration configuration=new Configuration();public int displayId=0,userId=0;public Object topActivity="launcher/Home";}
 @Test public void identityUsesWindowConfigurationAndRejectsAppsEvenWithLauncherName()throws Exception{
  Root root=new Root();String identity=HomeCaptureIdentity.identity(root,false);assertTrue(identity.contains("launcher/Home"));
  root.configuration.windowConfiguration.type=1;
  for(boolean recents:new boolean[]{false,true})try{HomeCaptureIdentity.identity(root,recents);fail();}catch(IllegalStateException expected){}
 }
 @Test public void recentsAllowedOnlyForDiagnosticNotHomeSnapshot()throws Exception{
  Root root=new Root();root.configuration.windowConfiguration.type=3;
  assertNotNull(HomeCaptureIdentity.identity(root,true));
  try{HomeCaptureIdentity.identity(root,false);fail();}catch(IllegalStateException expected){}
 }
 @Test public void rejectSecondaryAndFloatingAndMissingIdentity()throws Exception{
  Root root=new Root();root.displayId=1;
  try{HomeCaptureIdentity.identity(root,true);fail();}catch(IllegalStateException expected){}
  root.displayId=0;root.configuration.windowConfiguration.mode=5;
  try{HomeCaptureIdentity.identity(root,true);fail();}catch(IllegalStateException expected){}
  root.configuration.windowConfiguration.mode=1;root.topActivity=null;
  try{HomeCaptureIdentity.identity(root,true);fail();}catch(IllegalStateException expected){}
 }
}
