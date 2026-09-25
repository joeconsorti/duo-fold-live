package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class WallpaperProfileTest {
 @Test public void regionalFold7Uses002(){for(String suffix:new String[]{"U","U1","B","B/DS","W","N","0","Z"})assertEquals("video_002.mp4",WallpaperProfile.video(WallpaperProfile.resolve("SM-F966"+suffix,"fold8")));}
 @Test public void fold8AndUltraKeep001(){for(String model:new String[]{"SM-F971U","SM-F971B","SM-F976U1"})assertEquals("video_001.mp4",WallpaperProfile.video(WallpaperProfile.resolve(model,"fold7")));}
 @Test public void unknownModelAcceptsBothChoices(){for(String model:new String[]{null,"","unknown"})for(String choice:new String[]{"fold7","fold8"})assertEquals(choice,WallpaperProfile.resolve(model,choice));}
 @Test(expected=IllegalArgumentException.class) public void unknownRequiresChoice(){WallpaperProfile.resolve("unknown","");}
 @Test(expected=IllegalArgumentException.class) public void invalidChoiceRejected(){WallpaperProfile.resolve(null,"other");}
}
