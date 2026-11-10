package org.duofold.live;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.*;

/** Metadata only. One bounded queue and writer; callers never wait for disk. */
final class PersistentFoldJournal implements AutoCloseable {
 static final int SEGMENT_BYTES=512*1024, QUEUE_LIMIT=512, PROCESS_LIMIT=6;
 private final File root;
 private final String id;
 private final Supplier<String> identity;
 private final ArrayDeque<String> queue=new ArrayDeque<>();
 private final ScheduledExecutorService writer;
 private final Object files=new Object();
 private File directory;
 private volatile String recovered="Restart history loading",status="starting";
 private String pendingRecovery;
 private long dropped,totalDropped,lastSync;
 private volatile long writeFailures,uncertainRows;
 private boolean dirty;
 private volatile boolean closed;
 private final int segmentBytes;

 PersistentFoldJournal(File root,String id,Supplier<String> identity){this(root,id,identity,SEGMENT_BYTES,true);}
 PersistentFoldJournal(File root,String id,Supplier<String> identity,int segmentBytes,boolean scheduled){
  if(!id.matches("[a-zA-Z0-9_-]+")||segmentBytes<128)throw new IllegalArgumentException();
  this.root=root;this.id=id;this.identity=identity;this.segmentBytes=segmentBytes;
  writer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"duo-fold-journal");t.setDaemon(true);return t;});
  if(scheduled)writer.scheduleWithFixedDelay(this::drain,0,250,TimeUnit.MILLISECONDS);
 }
 synchronized void append(String row){
  if(closed)return;
  if(queue.size()==QUEUE_LIMIT){queue.removeFirst();dropped++;totalDropped++;}
  queue.addLast(clean(row,3000));
 }
 synchronized void recovery(String text){if(!closed)pendingRecovery=clean(text,96*1024,false);}
 synchronized int queued(){return queue.size();}
 synchronized long dropped(){return totalDropped;}
 String report(){return "Restart-persistent metadata: "+status+"; queueDropped="+dropped()+" writeFailures="+writeFailures+" uncertainRows="+uncertainRows+
  "; batches every 250 ms, sync target 1 second; abrupt failure can lose the unsaved tail. Up to 6 processes, 2 x 512 KiB each.\n"+recovered;}

 private void initialize()throws IOException{
  if(directory!=null)return;
  if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create journal folder");
  List<File> old=processes();
  StringBuilder history=new StringBuilder();
  for(int i=Math.max(0,old.size()-2);i<old.size();i++){
   File d=old.get(i);
   history.append("\nPREVIOUS PROCESS ").append(d.getName()).append(" (historical; restart cause not inferred)\n")
    .append(tail(new File(d,"identity.txt"),2048,10))
    .append(tail(new File(d,"timeline.previous.tsv"),48*1024,60))
    .append(tail(new File(d,"timeline.tsv"),96*1024,180))
    .append("Saved recovery evidence for that process (may describe an earlier incident):\n")
    .append(tail(new File(d,"recovery.txt"),24*1024,100));
  }
  recovered=history.length()==0?"No earlier persistent timeline. Recording begins with this build.\n":history.toString();
  directory=new File(root,"process-"+id);
  if(!directory.mkdir()) {directory=null;throw new IOException("Cannot create unique process journal");}
  write(new File(directory,"identity.txt"),identity.get()+"\n",false,true);
  for(int i=0;i<old.size()-(PROCESS_LIMIT-1);i++)delete(old.get(i));
 }
 // Called only by the writer, except deterministic tests and explicit ZIP export.
 void drain(){synchronized(files){
  int writing=0;
  try{
   initialize();
   List<String> batch;String recovery;long lost;
   synchronized(this){batch=new ArrayList<>(queue);queue.clear();recovery=pendingRecovery;pendingRecovery=null;lost=dropped;dropped=0;}
   if(lost>0)batch.add(0,"JOURNAL_GAP droppedQueuedRows="+lost);
   writing=batch.size();
   File active=new File(directory,"timeline.tsv");
   if(!batch.isEmpty()){
    StringBuilder part=new StringBuilder();long size=active.length();int bytes=0;
    for(String row:batch){
     // Keep each segment strictly bounded even for Unicode or a backed-up writer.
     while((row+"\n").getBytes(StandardCharsets.UTF_8).length>segmentBytes)row=row.substring(0,row.length()/2);
     String line=row+"\n";int n=line.getBytes(StandardCharsets.UTF_8).length;
     if(size+bytes+n>segmentBytes){
      if(bytes>0)write(active,part.toString(),true,false);
      File old=new File(directory,"timeline.previous.tsv");if(old.exists()&&!old.delete())throw new IOException("Journal rotation delete failed");if(!active.renameTo(old))throw new IOException("Journal rotation failed");
      part.setLength(0);size=0;bytes=0;
     }
     part.append(line);bytes+=n;
    }
    if(bytes>0)write(active,part.toString(),true,false);dirty=true;
   }
   long now=System.nanoTime();
   if(dirty&&(lastSync==0||now-lastSync>=TimeUnit.SECONDS.toNanos(1)||closed)){
    try(FileOutputStream out=new FileOutputStream(active,true)){out.getFD().sync();}
    lastSync=now;dirty=false;
   }
   if(recovery!=null)write(new File(directory,"recovery.txt"),recovery,false,true);
   status="recording";
  }catch(Exception failure){writeFailures++;uncertainRows+=writing;status="write failed: "+failure.getClass().getSimpleName()+" "+failure.getMessage();}
 }}
 void archive(ZipOutputStream zip)throws IOException{
  drain();
  synchronized(files){
   zip.putNextEntry(new ZipEntry("restart-history/status.txt"));zip.write(report().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
   for(File d:processes())for(String name:new String[]{"identity.txt","timeline.previous.tsv","timeline.tsv","recovery.txt"}){
    File f=new File(d,name);if(!f.isFile())continue;
    zip.putNextEntry(new ZipEntry("restart-history/"+d.getName()+"/"+name));
    try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();
   }
  }
 }
 private List<File> processes(){
  File[] all=root.listFiles(f->f.isDirectory()&&f.getName().startsWith("process-"));
  List<File> result=new ArrayList<>();if(all!=null)Collections.addAll(result,all);
  result.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));return result;
 }
 private static void write(File f,String s,boolean append,boolean sync)throws IOException{
  try(FileOutputStream out=new FileOutputStream(f,append)){out.write(s.getBytes(StandardCharsets.UTF_8));if(sync)out.getFD().sync();}
 }
 static String tail(File file,int bytes,int lines){
  if(!file.isFile())return "";
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){
   long start=Math.max(0,in.length()-bytes);in.seek(start);byte[] b=new byte[(int)Math.min(bytes,in.length())];in.readFully(b);
   String s=new String(b,StandardCharsets.UTF_8);int first=start>0?s.indexOf('\n')+1:0;
   // An interrupted append can leave a partial row. Never present it as complete evidence.
   int end=s.lastIndexOf('\n');if(end<first)return "";s=s.substring(first,end+1);
   String[] rows=s.split("\n");return String.join("\n",Arrays.copyOfRange(rows,Math.max(0,rows.length-lines),rows.length))+"\n";
  }catch(IOException e){return "Unreadable "+file.getName()+": "+e.getClass().getSimpleName()+"\n";}
 }
 private static String clean(String s,int cap){return clean(s,cap,true);}
 private static String clean(String s,int cap,boolean single){s=s==null?"":s;if(single)s=s.replace('\n',' ').replace('\r',' ');return s.length()>cap?s.substring(0,cap)+" [truncated]":s;}
 private static void delete(File dir){File[] children=dir.listFiles();if(children!=null)for(File f:children)if(f.isFile())f.delete();dir.delete();}
 @Override public void close(){closed=true;writer.shutdownNow();drain();}
}
