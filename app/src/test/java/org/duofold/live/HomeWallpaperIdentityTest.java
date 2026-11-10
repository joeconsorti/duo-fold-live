package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeWallpaperIdentityTest {
 private String window(String id,int display,int uid){return "Window #1 Window{"+id+" u0 wallpaper}:\n mDisplayId="+display+" mOwnerUid="+uid+"\n mAttrs={ty=WALLPAPER}\n mHasSurface=true isOnScreen=true isReadyForDisplay()=true\n mDrawState=HAS_DRAWN\n";}
 private void reject(String dump,String reason){try{HomeWallpaperIdentity.read(dump,0,12000);fail("accepted "+reason);}catch(IllegalStateException e){assertTrue(e.getMessage(),e.getMessage().contains(reason));}}
 @Test public void selectsOnlyVisibleWallpaperOnRequestedPanel(){HomeWallpaperIdentity a=HomeWallpaperIdentity.read(window("other",1,10001)+window("native",0,10002)+window("hidden",0,10003).replace("isOnScreen=true","isOnScreen=false"),0,12000);assertEquals(10002,a.uid);assertEquals("native",a.windows);}
 @Test public void numericWallpaperTypeSupported(){assertEquals(10001,HomeWallpaperIdentity.read(window("a",0,10001).replace("WALLPAPER","2013"),0,12000).uid);}
 @Test public void missingOwnerRejected(){reject(window("a",0,10001).replace("mOwnerUid=10001",""),"OWNER_UNSAFE");}
 @Test public void launcherSharedUidRejected(){reject(window("a",0,12000),"OWNER_UNSAFE");}
 @Test public void differentVisibleOwnersRejected(){reject(window("a",0,10001)+window("b",0,10002),"AMBIGUOUS");}
 @Test public void sameOwnerWithStableMultipleWindowsAccepted(){HomeWallpaperIdentity a=HomeWallpaperIdentity.read(window("a",0,10001)+window("b",0,10001),0,12000);a.requireSame(HomeWallpaperIdentity.read(window("b",0,10001)+window("a",0,10001),0,12000));}
 @Test public void pendingDrawRejected(){reject(window("a",0,10001).replace("HAS_DRAWN","DRAW_PENDING"),"DRAW_PENDING");}
 @Test public void hiddenWallpaperCannotStandInForNativeBackground(){reject(window("a",0,10001)+" shown=false","UNAVAILABLE");}
 @Test public void otherWindowTypesNotBackground(){reject(window("a",0,10001).replace("WALLPAPER","STATUS_BAR"),"UNAVAILABLE");}
 @Test public void noWallpaperRejected(){reject("","UNAVAILABLE");}
 @Test public void windowReplacementRejected(){HomeWallpaperIdentity a=HomeWallpaperIdentity.read(window("a",0,10001),0,12000);try{a.requireSame(HomeWallpaperIdentity.read(window("b",0,10001),0,12000));fail();}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("CHANGED"));}}
 @Test public void uidReplacementRejected(){HomeWallpaperIdentity a=HomeWallpaperIdentity.read(window("a",0,10001),0,12000);try{a.requireSame(HomeWallpaperIdentity.read(window("a",0,10002),0,12000));fail();}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("CHANGED"));}}
}
