package org.duofold.live;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PersistentFoldJournalTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 private PersistentFoldJournal journal(File root,String id,int size){return new PersistentFoldJournal(root,id,()->"build=test pid="+id+" bootId=same-boot",size,false);}
 @Test public void previousFoldSurvivesProcessReplacementAndNewStartup()throws Exception{
  File root=temp.newFolder();
  try(PersistentFoldJournal old=journal(root,"first",4096)){old.append("1\tSCENE session=17 task=22047 package=com.google.android.youtube");old.append("2\tPRIMARY_REQUEST_BEGIN target=1");old.drain();}
  // Simulate an interrupted final append, without corrupting preceding complete rows.
  Files.write(new File(root,"process-first/timeline.tsv").toPath(),"3 partial".getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
  try(PersistentFoldJournal current=journal(root,"second",4096)){
   current.append("4\tPROCESS restarted");current.drain();String before=current.report();
   assertTrue(before.contains("PRIMARY_REQUEST_BEGIN"));assertTrue(before.contains("bootId=same-boot"));assertFalse(before.contains("3 partial"));
   for(int i=0;i<100;i++)current.append("startup "+i);current.drain();
   assertTrue(current.report().contains("package=com.google.android.youtube"));
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){current.archive(zip);}
   Map<String,String> entries=unzip(bytes.toByteArray());
   assertTrue(entries.get("restart-history/process-first/timeline.tsv").contains("PRIMARY_REQUEST_BEGIN"));
   assertTrue(entries.get("restart-history/process-second/timeline.tsv").contains("startup 99"));
  }
 }
 @Test public void queueAndUnicodeSegmentsRemainBounded()throws Exception{
  File root=temp.newFolder();
  try(PersistentFoldJournal j=journal(root,"bounded",512)){
   for(int i=0;i<2000;i++)j.append("row="+i+" "+"\uD83D\uDE00".repeat(100));
   assertEquals(512,j.queued());assertEquals(1488,j.dropped());j.drain();assertEquals(0,j.queued());
   File d=new File(root,"process-bounded");assertTrue(new File(d,"timeline.tsv").length()<=512);assertTrue(new File(d,"timeline.previous.tsv").length()<=512);
   assertTrue(new String(Files.readAllBytes(new File(d,"timeline.tsv").toPath()),StandardCharsets.UTF_8).contains("row=1999"));
   assertTrue(j.report().contains("queueDropped=1488"));
  }
 }
 @Test public void retainsSixProcessesAndTheirSeparateRecoveryEvidence()throws Exception{
  File root=temp.newFolder();
  for(int i=0;i<9;i++){
   try(PersistentFoldJournal j=journal(root,"p"+i,1024)){j.append("fold "+i);j.recovery("Recovery evidence: crash "+i+"\n");j.drain();}
   new File(root,"process-p"+i).setLastModified(10000+i);
  }
  assertEquals(6,root.listFiles().length);assertFalse(new File(root,"process-p2").exists());
  assertEquals("Recovery evidence: crash 3\n",new String(Files.readAllBytes(new File(root,"process-p3/recovery.txt").toPath()),StandardCharsets.UTF_8));
  try(PersistentFoldJournal next=journal(root,"next",1024)){next.drain();assertTrue(next.report().contains("crash 8"));}
 }
 @Test public void diskFailureIsVisibleAndDoesNotThrowOrBlockAppend()throws Exception{
  File root=temp.newFile();
  try(PersistentFoldJournal j=journal(root,"fail",512)){j.append("event");j.drain();assertTrue(j.report().contains("write failed"));j.append("later");assertTrue(j.queued()>0);}
 }
 @Test public void exportDrainsPendingEventsAndIncludesRecoveryWithoutReaderConnection()throws Exception{
  File root=temp.newFolder();
  try(PersistentFoldJournal j=journal(root,"export",4096)){
   j.append("thermal=2 appCpuDeltaMs=964 intervalMs=1024");j.recovery("Recovery evidence: FATAL EXCEPTION IN SYSTEM PROCESS\n");
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){j.archive(zip);}
   Map<String,String> entries=unzip(bytes.toByteArray());
   assertTrue(entries.get("restart-history/process-export/timeline.tsv").contains("thermal=2"));
   assertTrue(entries.get("restart-history/process-export/recovery.txt").contains("SYSTEM PROCESS"));
  }
 }
 private static Map<String,String> unzip(byte[] bytes)throws IOException{
  Map<String,String> out=new HashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=zip.getNextEntry())!=null)out.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}return out;
 }
}
