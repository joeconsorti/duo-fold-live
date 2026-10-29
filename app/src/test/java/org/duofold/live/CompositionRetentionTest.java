package org.duofold.live;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import static org.junit.Assert.*;
public class CompositionRetentionTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 @Test public void newCaptureSurvivesOlderFoldersWithLargerLexicalNames() throws Exception {
  File[] files=new File[13];
  for(int i=0;i<12;i++){files[i]=temp.newFolder("8735003-D0-"+i);assertTrue(files[i].setLastModified(100000+i));}
  files[12]=temp.newFolder("15335635-D1-15336098");assertTrue(files[12].setLastModified(200000));
  File[] expired=CompositionRetention.expired(files,12);
  assertEquals(1,expired.length);assertEquals(files[0],expired[0]);
 }
 @Test public void filesDoNotConsumeSampleSlots() throws Exception {
  File folder=temp.newFolder("sample"),zip=temp.newFile("export.zip");
  assertEquals(0,CompositionRetention.expired(new File[]{folder,zip},1).length);
 }
}
