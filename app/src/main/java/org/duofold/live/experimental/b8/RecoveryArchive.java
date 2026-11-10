package org.duofold.live.experimental.b8;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
/** Bounded, non-destructive evidence copy. Never follows symbolic links. */
final class RecoveryArchive {
 private final ZipOutputStream zip; private final File output; private long remaining; private int count;
 private final StringBuilder log=new StringBuilder("Evidence copy; originals untouched. Missing/skipped data is unknown.\n");
 private RecoveryArchive(File out,long budget)throws IOException{output=out.getCanonicalFile();remaining=budget;zip=new ZipOutputStream(new FileOutputStream(out));}
 static void pack(File root,File out,String prefix,String excluded,long budget)throws IOException{
  try(RecoveryArchiveClose a=new RecoveryArchiveClose(out,budget)){File[] fs=a.r.list(root);for(File f:fs)if(f.getName().startsWith(prefix)&&!f.getName().startsWith(excluded))a.r.add(f,f.getName(),0);}
 }
 static void packFlat(File root,File out,long budget)throws IOException{
  try(RecoveryArchiveClose a=new RecoveryArchiveClose(out,budget)){for(File f:a.r.list(root))a.r.add(f,f.getName(),0);}
 }
 private File[] list(File dir){File[] fs=dir.listFiles();if(fs==null){log.append("UNAVAILABLE directory ").append(dir).append('\n');return new File[0];}Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));return fs;}
 private void add(File f,String name,int depth)throws IOException{
  if(count++>=4096||depth>8){if(count<=4097)log.append("LIMIT entries/depth: ").append(name).append('\n');return;}
  if(Files.isSymbolicLink(f.toPath())){log.append("SKIP symlink ").append(name).append('\n');return;}
  if(f.getCanonicalFile().equals(output))return;
  if(f.isDirectory()){for(File c:list(f))add(c,name+"/"+c.getName(),depth+1);return;}
  if(!f.isFile()){log.append("SKIP nonregular ").append(name).append('\n');return;}
  if(remaining<=0){log.append("SKIP budget ").append(name).append('\n');return;}
  long copied=0;boolean opened=false;
  try(InputStream in=new FileInputStream(f)){
   zip.putNextEntry(new ZipEntry(name));opened=true;byte[] buf=new byte[16384];int n;
   while(remaining>0&&(n=in.read(buf,0,(int)Math.min(buf.length,remaining)))!=-1){zip.write(buf,0,n);remaining-=n;copied+=n;}
   log.append(copied<f.length()?"TRUNCATED ":"COPIED ").append(name).append(" bytes=").append(copied).append(" observedSize=").append(f.length()).append(" modified=").append(f.lastModified()).append('\n');
  }catch(IOException e){log.append("ERROR ").append(name).append(' ').append(e).append('\n');}finally{if(opened)zip.closeEntry();}
 }
 private void close()throws IOException{try{zip.putNextEntry(new ZipEntry("RECOVERY-MANIFEST.txt"));zip.write(log.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}finally{zip.close();}}
 private static final class RecoveryArchiveClose implements AutoCloseable{final RecoveryArchive r;RecoveryArchiveClose(File f,long b)throws IOException{r=new RecoveryArchive(f,b);}public void close()throws IOException{r.close();}}
}
