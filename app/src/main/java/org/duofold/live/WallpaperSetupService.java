package org.duofold.live;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
/** Short-lived Shizuku helper. Writes only the two mandatory HOME wallpaper slots. */
public final class WallpaperSetupService extends Binder {
 public static final String TOKEN="org.duofold.live.WallpaperSetup";
 private static final ComponentName LIVE=new ComponentName("com.samsung.android.wallpaper.live","com.samsung.android.wallpaper.live.fold.FoldInteractive");
 private int owner=-1;
 public WallpaperSetupService(){attachInterface(null,TOKEN);}
 @Override protected synchronized boolean onTransact(int code,Parcel in,Parcel out,int flags)throws RemoteException {
  if(code==INTERFACE_TRANSACTION){out.writeString(TOKEN);return true;}
  if(code==16777115){System.exit(0);return true;}
  in.enforceInterface(TOKEN);
  int caller=Binder.getCallingUid();if(owner<0)owner=caller;
  if(owner!=caller)throw new SecurityException("Wrong caller");
  if(code!=1)return super.onTransact(code,in,out,flags);
  long identity=Binder.clearCallingIdentity();
  try {
   boolean repair=in.dataAvail()>=4 && in.readInt()!=0;
   String result=apply(repair);out.writeNoException();out.writeInt(1);out.writeString(result);
  } catch(Throwable error){
   while(error instanceof InvocationTargetException && error.getCause()!=null)error=error.getCause();
   out.writeNoException();out.writeInt(0);out.writeString("Wallpaper setup incomplete: "+error.getClass().getSimpleName()+": "+error.getMessage()+". Copy this error with the connection report. Existing completed slots are retained.");
  } finally {Binder.restoreCallingIdentity(identity);}
  return true;
 }
 private static String apply(boolean repair)throws Exception {
  if(android.os.Process.myUid()!=2000)throw new IllegalStateException("Start Shizuku using wireless or USB debugging (ADB mode)");
  // Regional variants share eligibility; verify Samsung components and methods before writing.
  DeviceCompatibility.requireEligible(Build.MODEL, Build.VERSION.SDK_INT);
  ShellFrameworkBootstrap.initialize();
  FutureTask<Context> contextTask=new FutureTask<>(()->{
   Class<?> at=Class.forName("android.app.ActivityThread");
   Object thread=at.getMethod("currentActivityThread").invoke(null);
   if(thread==null)thread=at.getMethod("systemMain").invoke(null);
   Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
   return system.createPackageContext("com.android.shell",0);
  });
  if(Looper.myLooper()==Looper.getMainLooper())contextTask.run();else new Handler(Looper.getMainLooper()).post(contextTask);
  Context ctx=contextTask.get(10,TimeUnit.SECONDS);
  ctx.getPackageManager().getServiceInfo(LIVE,0); // fail before any write if Samsung component missing
  WallpaperManager wm=WallpaperManager.getInstance(ctx);
  Method getInfo=WallpaperManager.class.getMethod("getWallpaperInfo",int.class,int.class);
  Method getExtras=WallpaperManager.class.getMethod("getWallpaperExtras",int.class,int.class);
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"wallpaper");
  Class<?> api=Class.forName("android.app.IWallpaperManager");
  Object remote=Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
  Class<?> builder=Class.forName("android.app.wallpaper.WallpaperDescription$Builder");
  Object b=builder.getConstructor().newInstance();builder.getMethod("setComponent",ComponentName.class).invoke(b,LIVE);
  Object description=builder.getMethod("build").invoke(b);
  Method setter=null;
  for(Method m:api.getMethods())if(m.getName().equals("setWallpaperComponentChecked")&&m.getParameterCount()==5){
   if(setter!=null)throw new IllegalStateException("Ambiguous wallpaper API; no change");setter=m;
  }
  if(setter==null)throw new NoSuchMethodException("setWallpaperComponentChecked");
  // Prefer the installed, verified profile. Otherwise use the extracted Samsung Fold8 profile.
  Bundle template=null;
  for(int slot:new int[]{5,17})if(matches(wm,getInfo,getExtras,slot)){template=new Bundle((Bundle)getExtras.invoke(wm,slot,0));break;}
  if(template==null){
   template=new Bundle();Bundle service=new Bundle();service.putString("filename","video_001.mp4");service.putInt("thumbnail_frame_no",545);
   template.putBundle("serviceSettings",service);template.putBoolean("isPreloaded",true);template.putBoolean("isFixedOrientation",false);
   template.putString("saContentCategory","Featured");template.putString("saContentId","Featured_Signature_Folding");
   template.putString("wallpaperId",new java.text.SimpleDateFormat("yyyyMMddHHmmss",java.util.Locale.US).format(new java.util.Date()));
  }
  StringBuilder result=new StringBuilder();
  for(int slot:new int[]{5,17}){
   String label=slot==5?"Inner home":"Cover home";
   if(repair || !matches(wm,getInfo,getExtras,slot)){
    setter.invoke(remote,description,"com.android.shell",slot,0,new Bundle(template));
    boolean ready=false;for(int i=0;i<20;i++){SystemClock.sleep(150);if(matches(wm,getInfo,getExtras,slot)){ready=true;break;}}
    if(!ready)throw new IllegalStateException(label+" configuration was not verified");
   }
   result.append(label).append(": Samsung fold wallpaper verified.\n");
  }
  return result+"Both HOME bindings verified. Move the hinge after enabling animation to check live angles. Lock-screen slots were not written.";
 }
 private static boolean matches(WallpaperManager wm,Method info,Method extras,int slot)throws Exception {
  WallpaperInfo i=(WallpaperInfo)info.invoke(wm,slot,0);Bundle b=(Bundle)extras.invoke(wm,slot,0);
  return i!=null&&LIVE.equals(i.getComponent())&&b!=null&&b.getBundle("serviceSettings")!=null&&"video_001.mp4".equals(b.getBundle("serviceSettings").getString("filename"));
 }
}
