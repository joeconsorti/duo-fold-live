package org.duofold.live.wallpaperlayer;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;
import org.duofold.live.BuildConfig;
import org.duofold.live.FoldBackgroundService;
/** Saved user intent survives package updates. Disable never deletes the selected photo. */
public final class WallpaperRestore {
 private static final String KEY="custom_wallpaper_enabled";
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
 private static IBinder remote;private static boolean connecting,pending;private static long retryAt;
 public static volatile String status="Wallpaper recovery idle";
 private WallpaperRestore(){}
 public static synchronized boolean enabled(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("standalone",0);
  if(p.contains(KEY))return p.getBoolean(KEY,false);
  // Migrate a legacy active host only; an explicit saved OFF always wins.
  boolean active=false;File dir=c.getFilesDir();
  if(new File(dir,"photo.jpg").exists()&&!new File(dir,"stop-layer").exists())try{
   String report=new String(Files.readAllBytes(new File(dir,"layer-report.txt").toPath()),StandardCharsets.UTF_8);
   active=report.contains("Custom wallpaper running — no time limit");
  }catch(IOException ignored){}
  p.edit().putBoolean(KEY,active).commit();return active;
 }
 public static synchronized void setEnabled(Context c,boolean value)throws IOException{
  File stop=new File(c.getFilesDir(),"stop-layer");
  if(value){if(!new File(c.getFilesDir(),"photo.jpg").exists())throw new IOException("Choose a photo first");if(stop.exists()&&!stop.delete())throw new IOException("Could not clear wallpaper stop request");}
  else if(!stop.exists()&&!stop.createNewFile())throw new IOException("Could not save wallpaper stop request");
  if(!c.getSharedPreferences("standalone",0).edit().putBoolean(KEY,value).commit())throw new IOException("Could not save wallpaper preference");
  retryAt=0;status=value?"Saved enabled wallpaper; reconnecting":"Custom wallpaper disabled; photo retained";
 }
 public static Shizuku.UserServiceArgs args(Context c){return new Shizuku.UserServiceArgs(new ComponentName(c,HostLauncher.class)).daemon(true).processNameSuffix("wallpaper_layer").version(BuildConfig.VERSION_CODE);}
 public static void resume(Context context){
  Context c=context.getApplicationContext();
  org.duofold.live.FoldAwakeDefault.persist(c);
  try{c.startForegroundService(new Intent(c,FoldBackgroundService.class));}catch(Exception e){status="Wallpaper choice saved; open Duo to resume: "+e.getClass().getSimpleName();}
 }
 public static void tick(Context context){
  Context c=context.getApplicationContext();
  if(!enabled(c)){status="Custom wallpaper disabled; photo retained";return;}
  if(PhotoInstrumentation.running){status="Saved custom wallpaper running";return;}
  if(connecting||pending||SystemClock.elapsedRealtime()<retryAt)return;
  try{
   if(!new File(c.getFilesDir(),"photo.jpg").exists()){status="Saved wallpaper photo missing; choose a replacement";return;}
   if(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=0){status="Wallpaper saved; waiting for authorized Shizuku";return;}
   if(remote==null||!remote.isBinderAlive()){
    connecting=true;retryAt=SystemClock.elapsedRealtime()+15000;
    Shizuku.bindUserService(args(c),connection);
    main.postDelayed(()->{if(connecting){connecting=false;status="Wallpaper connection timed out; retrying";}},12000);
    return;
   }
   pending=true;retryAt=SystemClock.elapsedRealtime()+15000;IBinder binder=remote;
   worker.execute(()->{
    String result;
    try{
     if(!enabled(c)){result="Custom wallpaper disabled";}
     else{
      File stop=new File(c.getFilesDir(),"stop-layer");if(stop.exists()&&!stop.delete())throw new IOException("Wallpaper stop request pending");
      Parcel p=Parcel.obtain(),r=Parcel.obtain();int active;
      try{p.writeInterfaceToken(HostLauncher.TOKEN);if(!binder.transact(4,p,r,0))throw new IOException("Wallpaper observer unavailable");r.readException();active=r.readInt();}finally{p.recycle();r.recycle();}
      if(active==0&&enabled(c)){
       p=Parcel.obtain();r=Parcel.obtain();
       try{p.writeInterfaceToken(HostLauncher.TOKEN);p.writeTypedObject((ParcelFileDescriptor)null,0);binder.transact(1,p,r,0);r.readException();result=r.readString();}finally{p.recycle();r.recycle();}
      }else result="Wallpaper host active / starting";
     }
    }catch(Exception e){result="Wallpaper recovery retry: "+e;}
    String next=result;main.post(()->{pending=false;status=next;});
   });
  }catch(Exception e){connecting=false;pending=false;retryAt=SystemClock.elapsedRealtime()+15000;status="Wallpaper recovery waiting: "+e;}
 }
 private static final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName n,IBinder binder){main.post(()->{remote=binder;connecting=false;retryAt=0;});}
  public void onServiceDisconnected(ComponentName n){main.post(()->{remote=null;connecting=false;pending=false;retryAt=0;});}
 };
}
