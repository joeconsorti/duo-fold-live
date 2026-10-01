package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndpointWindowStateTest {
 private String window(int id,int task,boolean ready){return "  Window #1 Window{abc u0 App}:\n mDisplayId="+id+" taskId="+task+"\n mHasSurface=true isReadyForDisplay()="+ready+"\n isOnScreen=true\n mDrawState=HAS_DRAWN\n";}
 @Test public void requiresExactTaskOnPrimary(){assertEquals("NO_MATCHING_D0_WINDOW",EndpointWindowState.summarize(window(1,42,true)+window(0,420,true),42));assertTrue(EndpointWindowState.summarize(window(0,42,true),42).startsWith("READY"));}
 @Test public void anotherReadyWindowCannotMaskUnreadyApp(){assertEquals("NOT_READY",EndpointWindowState.summarize(window(0,42,false)+window(0,99,true),42));}
 @Test public void accessibilityFooterDoesNotChangeDisplayIdentity(){assertEquals("NO_MATCHING_D0_WINDOW",EndpointWindowState.summarize(window(1,42,true)+"WindowsForAccessibilityObserver{mDisplayId=0}",42));}
 @Test public void noSurfaceOrOffscreenIsNotReady(){assertEquals("NOT_READY",EndpointWindowState.summarize(window(0,42,true).replace("mHasSurface=true","mHasSurface=false"),42));assertEquals("NOT_READY",EndpointWindowState.summarize(window(0,42,true).replace("isOnScreen=true","isOnScreen=false"),42));}
}
