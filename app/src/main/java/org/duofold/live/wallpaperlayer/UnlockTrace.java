package org.duofold.live.wallpaperlayer;

import android.app.UiAutomation;
import android.content.*;
import android.os.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.zip.*;

/** Opt-in, bounded compositor diagnostics. Never changes a system layer or tracing property. */
public final class UnlockTrace {
 private static volatile UnlockTrace host;
 private static volatile String status="Unlock trace idle";
 private final Context context;
 private final UiAutomation automation;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile Session session;
 private volatile boolean closed;
 private static final String CONFIG="duration_ms: 30000\n"
  +"buffers { size_kb: 8192 fill_policy: RING_BUFFER }\n"
  +"data_sources { config { name: \"android.surfaceflinger.layers\" surfaceflinger_layers_config { mode: MODE_ACTIVE trace_flags: TRACE_FLAG_COMPOSITION trace_flags: TRACE_FLAG_BUFFERS } } }\n"
  +"data_sources { config { name: \"android.surfaceflinger.transactions\" surfaceflinger_transactions_config { mode: MODE_ACTIVE } } }\n"
  +"data_sources { config { name: \"com.android.wm.shell.transition\" } }\n";
 public UnlockTrace(Context c,UiAutomation a){context=c.getApplicationContext();automation=a;host=this;}
 public static String status(){return status;}
 public static boolean capturing(){UnlockTrace h=host;return h!=null&&h.session!=null;}
 public static String start(){UnlockTrace h=host;return h==null?"Enable custom wallpaper and authorize Shizuku first.":h.begin();}
 private synchronized String begin(){
  if(closed)return "Wallpaper host stopped; enable it again.";
  if(session!=null)return status;
  File dir=new File(context.getCacheDir(),"unlock-trace-work");
  try{if(dir.exists()){File[] files=dir.listFiles();if(files!=null)for(File f:files)if(!f.delete())throw new IOException("Cannot replace previous trace");}else if(!dir.mkdirs())throw new IOException("Cannot create trace folder");
   Session s=new Session(dir);session=s;status="Preparing unlock trace…";worker.execute(()->capture(s));
  }catch(Exception e){session=null;status="Trace could not start: "+e;}
  return status;
 }
 public void event(String event){Session s=session;if(s==null||s.done)return;s.mark(event);
  if(event.contains("Screen event")){
   snapshot(s);
   if(event.contains("SCREEN_ON")||event.contains("USER_PRESENT"))try{timer.schedule(()->snapshot(s),200,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}
  }
 }
 private void snapshot(Session s){
  synchronized(s){if(s.done||closed||s.snapshotBusy||s.snapshots>=6)return;s.snapshotBusy=true;s.snapshots++;
  final int index=s.snapshots;
  s.snapWorker.execute(()->{try{
   s.mark("SF snapshot "+index+" begin");
   command("dumpsys -t 2 SurfaceFlinger --proto",null,new File(s.dir,"sf-"+index+".winscope"),1024*1024,4000);
   command("dumpsys -t 2 SurfaceFlinger",null,new File(s.dir,"sf-"+index+".txt"),1024*1024,4000);
   s.mark("SF snapshot "+index+" end (sequential dumps, not an atomic frame)");
  }catch(Exception e){s.mark("Snapshot failed: "+e);}finally{s.snapshotBusy=false;}});
  }
 }
 private void capture(Session s){
  try{
   Files.write(new File(s.dir,"config.txt").toPath(),CONFIG.getBytes(StandardCharsets.UTF_8));
   s.mark("Session preparing; elapsedRealtimeNanos="+SystemClock.elapsedRealtimeNanos());
   try{command("timeout 3 perfetto --query",null,new File(s.dir,"perfetto-sources.txt"),256*1024,4000);}catch(Exception e){s.mark("Source query failed: "+e);}
   if(closed)throw new IOException("Wallpaper host stopped");
   status="Capturing for 30 seconds — go Home, lock, then unlock";
   s.mark("Perfetto recording requested; source availability must be checked in trace");
   command("timeout 35 perfetto --txt -c - -o -",CONFIG,new File(s.dir,"unlock.perfetto-trace"),12*1024*1024,38000);
   s.mark("Perfetto command ended; nonempty output alone does not prove layer data was permitted");
  }catch(Exception e){s.mark("Capture failed: "+e);}
  finally{
   // Even a denied/empty Perfetto session must leave time to reproduce for snapshots.
   while(!closed&&SystemClock.elapsedRealtime()<s.started+34000){try{Thread.sleep(250);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}
   synchronized(s){s.done=true;}
   s.snapWorker.shutdown();
   try{if(!s.snapWorker.awaitTermination(10,TimeUnit.SECONDS))throw new IOException("Snapshot still running; export not ready");
    File report=new File(context.getFilesDir(),"layer-report.txt");if(report.exists())Files.copy(report.toPath(),new File(s.dir,"background-report.txt").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    s.mark("Session end");Files.write(new File(s.dir,"events.txt").toPath(),s.events().getBytes(StandardCharsets.UTF_8));
    Files.write(new File(s.dir,"README.txt").toPath(),("Duo "+org.duofold.live.BuildConfig.VERSION_NAME+" / "+Build.MODEL+" / "+Build.DISPLAY+"\nElapsed markers use Android boottime, including sleep.\nPerfetto contains layer metadata, not screen images. It may contain other app/window names.\nCheck sources, command stderr and actual SF packets: retail firmware may refuse tracing.\nSF dumps are timed samples, not continuous coverage; read their errors and truncation markers.\nCapture overhead may affect timing. No rendering or rotation settings changed.\n").getBytes(StandardCharsets.UTF_8));
    File exports=new File(context.getCacheDir(),"exports");if(!exports.exists()&&!exports.mkdirs())throw new IOException("Cannot create export folder");
    File zip=new File(exports,"Duo-unlock-trace-"+s.started+".zip");
    try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){File[] files=s.dir.listFiles();if(files!=null)for(File f:files){out.putNextEntry(new ZipEntry(f.getName()));Files.copy(f.toPath(),out);out.closeEntry();}}
    context.getSharedPreferences("unlock_trace",0).edit().putString("zip",zip.getName()).apply();
    File[] older=exports.listFiles();if(older!=null)for(File f:older)if(!f.equals(zip)&&f.getName().matches("Duo-unlock-trace-[0-9]+\\.zip"))f.delete();
    status="Capture saved — tap Share unlock trace (layer availability requires inspection)";
   }catch(Exception e){status="Trace export failed: "+e;}
   synchronized(this){if(session==s)session=null;}
   if(closed)timer.shutdown();
  }
 }
 private void command(String command,String input,File file,int limit,long timeout)throws Exception{
  ParcelFileDescriptor[] fds=automation.executeShellCommandRwe(command);
  ScheduledFuture<?> deadline=timer.schedule(()->{for(ParcelFileDescriptor fd:fds)try{fd.close();}catch(IOException ignored){}},timeout,TimeUnit.MILLISECONDS);
  final Throwable[] stderrError=new Throwable[1];
  Thread err=new Thread(()->{try{copy(fds[2],new File(file.getPath()+".stderr.txt"),65536);}catch(Throwable e){stderrError[0]=e;}},"unlock-trace-stderr");err.setDaemon(true);err.start();
  try{
   try(OutputStream in=new ParcelFileDescriptor.AutoCloseOutputStream(fds[1])){if(input!=null)in.write(input.getBytes(StandardCharsets.UTF_8));}
   copy(fds[0],file,limit);err.join(1000);
   if(stderrError[0]!=null)throw new IOException("stderr capture failed",stderrError[0]);
  }finally{deadline.cancel(false);for(ParcelFileDescriptor fd:fds)try{fd.close();}catch(IOException ignored){}err.join(1000);}
 }
 static void copy(ParcelFileDescriptor fd,File file,int limit)throws IOException{
  boolean truncated=false;int saved=0;
  try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd);OutputStream out=new FileOutputStream(file)){
   byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){int keep=Math.min(n,Math.max(0,limit-saved));out.write(bytes,0,keep);saved+=keep;if(keep<n)truncated=true;}
  }
  if(truncated)Files.write(new File(file.getPath()+".TRUNCATED.txt").toPath(),"Capture exceeded file limit; data incomplete".getBytes(StandardCharsets.UTF_8));
 }
 public static Intent share(Context c)throws IOException{
  String name=c.getSharedPreferences("unlock_trace",0).getString("zip","");
  if(!name.matches("Duo-unlock-trace-[0-9]+\\.zip"))throw new IOException("Capture a trace first");
  File f=new File(new File(c.getCacheDir(),"exports"),name);if(!f.isFile())throw new IOException("Trace no longer available; capture again");
  android.net.Uri uri=FileProvider.getUriForFile(c,c.getPackageName()+".files",f);
  Intent send=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  send.setClipData(ClipData.newRawUri("Unlock trace",uri));return Intent.createChooser(send,"Share unlock trace");
 }
 public void close(){closed=true;if(host==this)host=null;worker.shutdown();if(session==null)timer.shutdown();}
 private static final class Session{
  final long started=SystemClock.elapsedRealtime();final File dir;
  final ExecutorService snapWorker=Executors.newSingleThreadExecutor();
  final java.util.ArrayDeque<String> markers=new java.util.ArrayDeque<>();
  volatile boolean done,snapshotBusy;int snapshots;
  Session(File d){dir=d;}
  synchronized void mark(String m){if(markers.size()>=256)markers.removeFirst();markers.addLast(SystemClock.elapsedRealtime()+" "+m);}
  synchronized String events(){return String.join("\n",markers);}
 }
}
