package org.duofold.live;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ShellDiagnosticArchiveTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 private File session(String name)throws Exception{return temp.newFolder(name);}
 private void put(File dir,String name,String text)throws Exception{Files.write(new File(dir,name).toPath(),text.getBytes(StandardCharsets.UTF_8));}
 private Map<String,byte[]> export()throws Exception {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();ShellDiagnosticArchive.write(temp.getRoot(),2000,"test-build",bytes);
  Map<String,byte[]> entries=new LinkedHashMap<>();
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
   for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)entries.put(entry.getName(),zip.readAllBytes());
  }
  return entries;
 }
 private String text(Map<String,byte[]> entries,String key){return new String(entries.get(key),StandardCharsets.UTF_8);}
 @Test public void includesUnarmedWriterEvidenceAndLabelsMissingFiles()throws Exception {
  File dir=session("duo-live-b8-2000-519646660");put(dir,"physical-brightness-hold.txt","519649367 HOLD_END\n");
  put(dir,"brightness-source-prepare.txt","physical brightness sample\n");put(dir,"live-journal.txt","PRIMARY_REQUEST\n");
  Map<String,byte[]> entries=export();String prefix=dir.getName()+"/";
  assertEquals("519649367 HOLD_END\n",text(entries,prefix+"physical-brightness-hold.txt"));
  assertEquals("physical brightness sample\n",text(entries,prefix+"brightness-source-prepare.txt"));
  String status=text(entries,"shell-evidence-status.txt");
  assertTrue(status.contains("brightness-source-refreshed.txt unavailable"));
  assertTrue(status.contains("may precede this build"));assertTrue(status.contains("selectedSessions=1"));
 }
 @Test public void limitsToNewestSixNumericSessionsAndDoesNotCopyOtherFiles()throws Exception {
  for(int n=7;n<=14;n++){File dir=session("duo-live-b8-2000-"+n);put(dir,"live-journal.txt","session="+n);put(dir,"unrelated.txt","private");new File(dir,"live-journal.txt").setLastModified(10000+n);}
  put(session("duo-live-b8-0-999"),"live-journal.txt","other UID");
  put(session("duo-live-b8-2000-not-a-session"),"live-journal.txt","invalid name");
  Map<String,byte[]> entries=export();assertEquals(7,entries.size());
  assertFalse(entries.containsKey("duo-live-b8-2000-8/live-journal.txt"));
  assertTrue(entries.containsKey("duo-live-b8-2000-9/live-journal.txt"));
  assertTrue(entries.containsKey("duo-live-b8-2000-14/live-journal.txt"));
  assertFalse(entries.keySet().stream().anyMatch(s->s.contains("unrelated")||s.contains("not-a-session")||s.contains("b8-0-")));
 }
 @Test public void newBootLowUptimeSessionIsNotDiscardedBehindOldBootTokens()throws Exception {
  for(int n=0;n<6;n++){File dir=session("duo-live-b8-2000-"+(900000000+n));put(dir,"live-journal.txt","old boot");new File(dir,"live-journal.txt").setLastModified(10000+n);}
  File newest=session("duo-live-b8-2000-100");put(newest,"live-journal.txt","new boot");new File(newest,"live-journal.txt").setLastModified(20000);
  put(newest,"recovery-after-restart.txt","saved system crash\n");
  Map<String,byte[]> entries=export();assertTrue(entries.containsKey(newest.getName()+"/live-journal.txt"));
  assertEquals("saved system crash\n",text(entries,newest.getName()+"/recovery-after-restart.txt"));
  assertFalse(entries.containsKey("duo-live-b8-2000-900000000/live-journal.txt"));
 }
 @Test public void neverTraversesDirectoryOrFileSymlinks()throws Exception {
  File outside=session("outside");put(outside,"live-journal.txt","do not export");
  Files.createSymbolicLink(new File(temp.getRoot(),"duo-live-b8-2000-999").toPath(),outside.toPath());
  File dir=session("duo-live-b8-2000-100");
  Files.createSymbolicLink(new File(dir,"physical-brightness-hold.txt").toPath(),new File(outside,"live-journal.txt").toPath());
  Map<String,byte[]> entries=export();assertEquals(Collections.singleton("shell-evidence-status.txt"),entries.keySet());
  assertTrue(text(entries,"shell-evidence-status.txt").contains("selectedSessions=1"));
 }
 @Test public void byteBoundsRetainTailAndRecordOffsets()throws Exception {
  File dir=session("duo-live-b8-2000-100");
  byte[] log=new byte[ShellDiagnosticArchive.LOG_LIMIT+100],source=new byte[ShellDiagnosticArchive.SOURCE_LIMIT+200];
  Arrays.fill(log,(byte)'L');Arrays.fill(source,(byte)'S');log[log.length-1]='!';source[source.length-1]='?';
  Files.write(new File(dir,"physical-brightness-hold.txt").toPath(),log);
  Files.write(new File(dir,"brightness-source-refreshed.txt").toPath(),source);
  Map<String,byte[]> entries=export();String prefix=dir.getName()+"/";
  assertArrayEquals(Arrays.copyOfRange(log,100,log.length),entries.get(prefix+"physical-brightness-hold.txt"));
  assertArrayEquals(Arrays.copyOfRange(source,200,source.length),entries.get(prefix+"brightness-source-refreshed.txt"));
  String status=text(entries,"shell-evidence-status.txt");assertTrue(status.contains("offset=100"));assertTrue(status.contains("offset=200"));assertTrue(status.contains("truncated=true"));
 }
 @Test public void emptyHistoryIsExplicitlyUnmeasured()throws Exception {
  Map<String,byte[]> entries=export();assertEquals(1,entries.size());
  String status=text(entries,"shell-evidence-status.txt");assertTrue(status.contains("selectedSessions=0"));assertTrue(status.contains("Missing files are unmeasured"));
 }
}
