package org.duofold.live;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
public class OrphanReadersTest {
 static final String READER="logcat -v epoch -T 1 -s SprWallpaper|FoldInteractive:V *:S";
 @Test public void reapsOnlyReadersReparentedToInit(){
  List<OrphanReaders.Proc> procs=Arrays.asList(
   new OrphanReaders.Proc(25507,1,READER),                      // orphan from a killed service process
   new OrphanReaders.Proc(2761,2684,READER),                    // live reader of the current service
   new OrphanReaders.Proc(802,700,"logcat -v threadtime *:E"),  // someone else's logcat
   new OrphanReaders.Proc(900,1,"logcat -v epoch"),             // orphan, but not ours
   new OrphanReaders.Proc(901,1,"sh -c logcat SprWallpaper|FoldInteractive")); // not a logcat command
  assertEquals(Arrays.asList(25507),OrphanReaders.orphans(procs,1234));
 }
 @Test public void neverReapsItself(){
  assertTrue(OrphanReaders.orphans(Arrays.asList(new OrphanReaders.Proc(42,1,READER)),42).isEmpty());
 }
 @Test public void parsesProcStat(){
  // comm can contain spaces and parentheses; ppid is the 2nd field after the last ')'.
  assertEquals(1,OrphanReaders.ppid("25507 (logcat) S 1 25507 0 0"));
  assertEquals(2684,OrphanReaders.ppid("2761 (log cat) (x)) S 2684 2761 0"));
  assertEquals(-1,OrphanReaders.ppid("garbage"));
 }
}
