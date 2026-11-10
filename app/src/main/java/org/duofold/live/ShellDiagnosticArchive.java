package org.duofold.live;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** On-demand copy of existing Duo session evidence. No commands, captures or policy writes. */
final class ShellDiagnosticArchive {
 static final int SESSION_LIMIT=6, LOG_LIMIT=64*1024, SOURCE_LIMIT=256*1024;
 private static final String[] FILES={"live-journal.txt","live-journal.previous.txt","recovery-after-restart.txt",
  "physical-brightness-hold.txt","brightness-refresh.txt",
  "brightness-refresh-watchdog-D0.txt","brightness-refresh-watchdog-D1.txt",
  "brightness-source-prepare.txt","brightness-source-refreshed.txt","task-route.txt"};

 static void write(File root,int uid,String exportBuild,OutputStream destination)throws IOException {
  Path base=root.toPath().toRealPath();
  String prefix="duo-live-b8-"+uid+"-";
  ArrayList<Path> sessions=new ArrayList<>();
  try(DirectoryStream<Path> dirs=Files.newDirectoryStream(base,prefix+"*")){
   for(Path dir:dirs)if(dir.getFileName().toString().matches(java.util.regex.Pattern.quote(prefix)+"[0-9]{1,19}")
       &&Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)){
    sessions.add(dir);
    sessions.sort(Comparator.comparingLong(ShellDiagnosticArchive::sessionModified)
     .thenComparingLong(ShellDiagnosticArchive::sessionTime).reversed());
    if(sessions.size()>SESSION_LIMIT)sessions.remove(sessions.size()-1);
   }
  }
  StringBuilder status=new StringBuilder("Exporting helper build="+exportBuild+"\n")
   .append("Existing shell session logs; sessions may precede this build or process.\n")
   .append("No fresh commands or captures. Missing files are unmeasured, not successes.\n")
   .append("Byte-bounded tails may start mid-line. Files are sequential observations, not an atomic snapshot.\n")
   .append("sessionLimit=").append(SESSION_LIMIT).append(" logLimit=").append(LOG_LIMIT)
   .append(" sourceLimit=").append(SOURCE_LIMIT).append(" selectedSessions=").append(sessions.size()).append('\n');
  try(ZipOutputStream zip=new ZipOutputStream(destination)){
   byte[] bytes=new byte[8192];
   for(Path dir:sessions)for(String name:FILES){
    String entry=dir.getFileName()+"/"+name;
    Path file=dir.resolve(name);
    try{
     // Do not follow session/file symlinks or accept arbitrary caller-supplied paths.
     if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)||!dir.toRealPath().getParent().equals(base)
         ||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){
      status.append(entry).append(" unavailable or not a regular owned-session file\n");continue;
     }
     try(SeekableByteChannel input=Files.newByteChannel(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
      long size=input.size(),start=Math.max(0,size-(name.startsWith("brightness-source-")?SOURCE_LIMIT:LOG_LIMIT));
      input.position(start);long remaining=size-start,copied=0;
      zip.putNextEntry(new ZipEntry(entry));
      while(remaining>0){int n=input.read(ByteBuffer.wrap(bytes,0,(int)Math.min(bytes.length,remaining)));if(n<=0)break;zip.write(bytes,0,n);copied+=n;remaining-=n;}
      zip.closeEntry();
      status.append(entry).append(" observedBytes=").append(size).append(" offset=").append(start)
       .append(" copiedBytes=").append(copied).append(" truncated=").append(start>0||remaining>0).append('\n');
     }
    }catch(IOException e){
     // A session can expire while exporting; keep the remaining evidence usable.
     zip.closeEntry();status.append(entry).append(" unavailable: ").append(e.getClass().getSimpleName()).append('\n');
    }
   }
   zip.putNextEntry(new ZipEntry("shell-evidence-status.txt"));
   zip.write(status.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
  }
 }
 private static long sessionModified(Path dir){
  // Elapsed-realtime folder names restart at zero after a kernel boot. Order by
  // the journal's wall-clock file time, not by that cross-boot incomparable token.
  Path journal=dir.resolve("live-journal.txt");
  try{return Files.getLastModifiedTime(Files.isRegularFile(journal,LinkOption.NOFOLLOW_LINKS)?journal:dir,LinkOption.NOFOLLOW_LINKS).toMillis();}
  catch(IOException e){return 0;}
 }
 private static long sessionTime(Path dir){
  String name=dir.getFileName().toString();
  try{return Long.parseLong(name.substring(name.lastIndexOf('-')+1));}catch(NumberFormatException e){return -1;}
 }
}
