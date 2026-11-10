package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SystemBarMemoryCacheTest {
 @Test public void expiresAndRejectsFutureFrames(){var c=new SystemBarMemoryCache<String>(2,100,500);c.put("inner","i",1000,20,1000);assertEquals("i",c.get("inner",1500));assertNull(c.get("inner",1501));c.put("inner","future",2000,20,1600);assertNull(c.get("inner",1600));}
 @Test public void limitsBytesAndPanels(){var c=new SystemBarMemoryCache<String>(2,100,500);c.put("inner","i",1000,60,1000);c.put("cover","c",1001,60,1001);assertNull(c.get("inner",1001));assertEquals(60,c.bytes());c.put("third","t",1002,10,1002);c.put("fourth","f",1003,10,1003);assertNull(c.get("cover",1003));assertEquals(20,c.bytes());}
 @Test public void oversizedCannotReplaceValidEntryAndClearDropsAll(){var c=new SystemBarMemoryCache<String>(2,100,500);c.put("inner","i",1000,20,1000);c.put("inner","large",1001,101,1001);assertEquals("i",c.get("inner",1001));c.clear();assertNull(c.get("inner",1001));assertEquals(0,c.bytes());}
}
