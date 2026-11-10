package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndpointWindowStateTest {
 private String window(int id,int task,boolean ready){return "  Window #1 Window{abc u0 App}:\n mDisplayId="+id+" taskId="+task+"\n mHasSurface=true isReadyForDisplay()="+ready+"\n isOnScreen=true\n mDrawState=HAS_DRAWN\n";}
 @Test public void requiresExactTaskOnPrimary(){assertEquals("NO_MATCHING_D0_WINDOW",EndpointWindowState.summarize(window(1,42,true)+window(0,420,true),42));assertTrue(EndpointWindowState.summarize(window(0,42,true),42).startsWith("READY"));}
 @Test public void anotherReadyWindowCannotMaskUnreadyApp(){assertEquals("NOT_READY",EndpointWindowState.summarize(window(0,42,false)+window(0,99,true),42));}
 @Test public void accessibilityFooterDoesNotChangeDisplayIdentity(){assertEquals("NO_MATCHING_D0_WINDOW",EndpointWindowState.summarize(window(1,42,true)+"WindowsForAccessibilityObserver{mDisplayId=0}",42));}
 @Test public void noSurfaceOrOffscreenIsNotReady(){assertEquals("NOT_READY",EndpointWindowState.summarize(window(0,42,true).replace("mHasSurface=true","mHasSurface=false"),42));assertEquals("NOT_READY",EndpointWindowState.summarize(window(0,42,true).replace("isOnScreen=true","isOnScreen=false"),42));}
 @Test public void incomingCaptureRequiresSelectedDisplay(){assertTrue(EndpointWindowState.summarize(window(1,42,true),42,1).startsWith("READY"));assertEquals("NO_MATCHING_D1_WINDOW",EndpointWindowState.summarize(window(0,42,true),42,1));}
 @Test public void rejectsAnotherVisibleTaskEvenWhenSelectedHasDrawn(){assertTrue(EndpointWindowState.captureGuard(window(0,42,true)+window(0,99,true),42,0).startsWith("COMPETING_TASK"));}
 @Test public void competingUndrawnSurfaceStillBlocks(){assertTrue(EndpointWindowState.captureGuard(window(0,42,true)+window(0,99,false).replace("HAS_DRAWN","DRAW_PENDING"),42,0).startsWith("COMPETING_TASK"));}
 @Test public void ignoresOtherDisplayAndHiddenWindows(){assertEquals("READY; HAS_DRAWN",EndpointWindowState.captureGuard(window(0,42,true)+window(1,99,true)+window(0,98,true).replace("isOnScreen=true","isOnScreen=false"),42,0));}
 @Test public void sameTaskDialogIsAllowed(){assertEquals("READY; HAS_DRAWN",EndpointWindowState.captureGuard(window(0,42,true)+window(0,42,true),42,0));}
 @Test public void missingSelectedTaskNeverPassesGuard(){assertEquals("NO_MATCHING_D0_WINDOW",EndpointWindowState.captureGuard(window(0,99,true),42,0));}
 @Test public void visibleSplashBlocksEvenBehindReadyTaskInDump(){
  String app=window(0,42,true),splash=app.replace("App}","Splash Screen com.openai.chatgpt}");
  assertEquals("STARTING_WINDOW",EndpointWindowState.captureGuard(app+splash,42,0));
  assertEquals("STARTING_WINDOW",EndpointWindowState.captureGuard(splash+app,42,0));
 }
 @Test public void startingWindowTypeIsRejectedWithoutEnglishTitle(){
  assertEquals("STARTING_WINDOW",EndpointWindowState.captureGuard(window(1,42,true)+" mAttrs={ty=APPLICATION_STARTING}",42,1));
  assertEquals("STARTING_WINDOW",EndpointWindowState.captureGuard(window(0,42,true)+" mAttrs={ty=3}",42,0));
 }
 @Test public void hiddenSplashAndOtherDisplayDoNotBlock(){
  String splash=window(0,42,true).replace("App}","Splash Screen app}").replace("isOnScreen=true","isOnScreen=false");
  assertEquals("READY; HAS_DRAWN",EndpointWindowState.captureGuard(window(0,42,true)+splash+window(1,42,true).replace("App}","Splash Screen app}"),42,0));
 }
 @Test public void drawnButExplicitlyHiddenDoesNotPass(){
  for(String hidden:new String[]{"Surface: shown=false", "mLastHidden=true", "mPolicyVisibility=false"})
   assertEquals("NOT_READY",EndpointWindowState.captureGuard(window(0,42,true)+hidden,42,0));
 }
 @Test public void shownDrawnSurfacePasses(){
  assertEquals("READY; HAS_DRAWN",EndpointWindowState.captureGuard(window(0,42,true)+"Surface: shown=true mLastHidden=false mPolicyVisibility=true",42,0));
 }
 @Test public void workspaceAcceptsTwoAndThreeReadyPanes(){
  String two=window(0,42,true)+window(0,43,true);
  assertEquals("READY; HAS_DRAWN",EndpointWindowState.workspaceGuard(two,new int[]{42,43},0));
  assertEquals("READY; HAS_DRAWN",EndpointWindowState.workspaceGuard(two+window(0,44,true),new int[]{42,43,44},0));
  assertTrue(EndpointWindowState.captureGuard(two,42,0).startsWith("COMPETING_TASK"));
 }
 @Test public void workspaceRequiresEveryPaneAndRejectsUnselectedApps(){
  assertTrue(EndpointWindowState.workspaceGuard(window(0,42,true),new int[]{42,43},0).contains("NO_MATCHING"));
  assertTrue(EndpointWindowState.workspaceGuard(window(0,42,true)+window(0,43,false),new int[]{42,43},0).contains("NOT_READY"));
  assertTrue(EndpointWindowState.workspaceGuard(window(0,42,true)+window(0,43,true)+window(0,99,true),new int[]{42,43},0).contains("COMPETING_TASK"));
 }
 @Test public void workspaceRejectsSplashHiddenPaneAndInvalidMembership(){
  String ready=window(0,42,true),other=window(0,43,true);
  assertTrue(EndpointWindowState.workspaceGuard(ready+other+" mAttrs={ty=3}",new int[]{42,43},0).contains("STARTING_WINDOW"));
  assertTrue(EndpointWindowState.workspaceGuard(ready+other+" mLastHidden=true",new int[]{42,43},0).contains("NOT_READY"));
  assertEquals("INVALID_WORKSPACE",EndpointWindowState.workspaceGuard(ready,new int[]{42,42},0));
  assertEquals("INVALID_WORKSPACE",EndpointWindowState.workspaceGuard(ready,new int[0],0));
 }
 @Test public void workspaceIgnoresOtherDisplayAndAcceptsNativeSinglePane(){
  assertEquals("READY; HAS_DRAWN",EndpointWindowState.workspaceGuard(window(0,42,true)+window(1,99,true),new int[]{42},0));
 }
}
