package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppCaptureIdentityTest {
 private String window(String token,int display,int task,long uid){return "  Window #2 Window{"+token+" u0 app}:\n mDisplayId="+display+" taskId="+task+"\n mOwnerUid="+uid+" showForAllUsers=false\n mHasSurface=true isReadyForDisplay()=true isOnScreen=true\n mDrawState=HAS_DRAWN\n";}
 private void rejects(String dump,String expected){try{AppCaptureIdentity.read(dump,42,1);fail();}catch(IllegalStateException e){assertTrue(e.toString(),e.getMessage().contains(expected));}}
 @Test public void usesSelectedTaskOwnerNotWallpaperOrOverlay(){
  String dump=window("wallpaper",1,-1,1000)+window("app",1,42,10345)+window("overlay",1,-1,10456);
  AppCaptureIdentity id=AppCaptureIdentity.read(dump,42,1);assertEquals(10345,id.uid);assertEquals("app",id.windows);
 }
 @Test public void otherDisplayIsNotAnOwnerCandidate(){assertEquals(10345,AppCaptureIdentity.read(window("old",0,42,10111)+window("new",1,42,10345),42,1).uid);}
 @Test public void missingOwnerNeverDefaultsToAllLayers(){rejects(window("app",1,42,10345).replace("mOwnerUid=10345",""),"APP_OWNER_UNAVAILABLE");rejects(window("app",1,42,-1),"APP_OWNER_UNAVAILABLE");}
 @Test public void ownerCannotComeFromAnUnselectedWindow(){rejects(window("app",1,42,10345).replace("mOwnerUid=10345","")+window("wallpaper",1,-1,1000),"APP_OWNER_UNAVAILABLE");}
 @Test public void conflictingOwnersAreRejected(){rejects(window("a",1,42,10345)+window("b",1,42,10346),"APP_OWNER_AMBIGUOUS");}
 @Test public void sameTaskDialogAndWindowOrderAreStable(){
  String a=window("a",1,42,10345),b=window("b",1,42,10345);
  AppCaptureIdentity.read(a+b,42,1).requireSame(AppCaptureIdentity.read(b+a,42,1));
 }
 @Test public void hiddenWindowDoesNotSupplyOwner(){rejects(window("a",1,42,10345).replace("isOnScreen=true","isOnScreen=false"),"NOT_READY");}
 @Test public void recreatedAppWindowInvalidatesCapture(){
  AppCaptureIdentity before=AppCaptureIdentity.read(window("old",1,42,10345),42,1);
  try{before.requireSame(AppCaptureIdentity.read(window("new",1,42,10345),42,1));fail();}catch(IllegalStateException expected){}
 }
 @Test public void ownerChangeInvalidatesCapture(){
  AppCaptureIdentity before=AppCaptureIdentity.read(window("a",1,42,10345),42,1);
  try{before.requireSame(AppCaptureIdentity.read(window("a",1,42,10346),42,1));fail();}catch(IllegalStateException expected){}
 }
 @Test public void drawPendingIsNotAnImage(){rejects(window("a",1,42,10345).replace("mDrawState=HAS_DRAWN","mDrawState=DRAW_PENDING mLastDrawState=HAS_DRAWN"),"APP_WINDOW_DRAW_PENDING");}
 @Test public void invisibleAlphaIsNotAnImage(){for(String alpha:new String[]{"0.0","0.0E0","-1.0","NaN"})rejects(window("a",1,42,10345)+"mShownAlpha="+alpha+"\n","APP_WINDOW_INVISIBLE");}
 @Test public void visibleAlphaAndBlackContentAreNotRejected(){assertEquals(10345,AppCaptureIdentity.read(window("a",1,42,10345)+"mShownAlpha=0.5\n",42,1).uid);}
}
