package org.duofold.live.experimental.b8;
import android.content.*;
import android.os.*;
import rikka.shizuku.Shizuku;
final class Bridge {
 static volatile IBinder remote;
 static volatile String state="Authorize Shizuku, then connect.";
 static Shizuku.UserServiceArgs args;
 static final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){remote=b;state="Connected to shell observer";}public void onServiceDisconnected(ComponentName n){remote=null;state="Shell observer disconnected";}};
 static void connect(Context c){if(remote!=null&&remote.isBinderAlive())return;args=new Shizuku.UserServiceArgs(new ComponentName(c,Probe.class)).daemon(false).processNameSuffix("b8_observer").debuggable(false).version(org.duofold.live.BuildConfig.VERSION_CODE);Shizuku.bindUserService(args,connection);state="Connecting…";}
 static Parcel request(int code,Long offset,Integer mode)throws Exception{
  IBinder b=remote;if(b==null||!b.isBinderAlive())throw new IllegalStateException("Connect Shizuku first");
  Parcel d=Parcel.obtain(),r=Parcel.obtain();try{d.writeInterfaceToken(Probe.TOKEN);if(offset!=null)d.writeLong(offset);if(mode!=null)d.writeInt(mode);if(!b.transact(code,d,r,0))throw new IllegalStateException("Unsupported diagnostic command");r.readException();return r;}catch(Exception e){r.recycle();throw e;}finally{d.recycle();}
 }
 static void start(int mode)throws Exception{Parcel p=request(1,null,mode);p.recycle();}
 static void startRecipe(String recipe,boolean detailed)throws Exception{
  ExperimentConfig.parse(recipe);IBinder b=remote;if(b==null||!b.isBinderAlive())throw new IllegalStateException("Connect Shizuku first");
  Parcel d=Parcel.obtain(),r=Parcel.obtain();try{d.writeInterfaceToken(Probe.TOKEN);d.writeString(recipe);if(!b.transact(detailed?8:7,d,r,0))throw new IllegalStateException("Reconnect Shizuku after updating");r.readException();}finally{d.recycle();r.recycle();}
 }
 static void startTaskRecipe(String recipe,String pkg,int options)throws Exception{startTaskRecipe(recipe,pkg,options,false);}
 static void startTaskRecipe(String recipe,String pkg,int options,boolean screenshotOnly)throws Exception{startTaskRecipe(recipe,pkg,options,screenshotOnly,false);}
 static void startTaskRecipe(String recipe,String pkg,int options,boolean screenshotOnly,boolean brightnessHold)throws Exception{
  RoutePlan.validate(options);TaskGuard.validate(pkg,ExperimentConfig.parse(recipe));IBinder b=remote;if(b==null||!b.isBinderAlive())throw new IllegalStateException("Connect Shizuku first");
  Parcel d=Parcel.obtain(),r=Parcel.obtain();try{d.writeInterfaceToken(Probe.TOKEN);d.writeString(recipe);d.writeString(pkg);d.writeInt(options);if(!b.transact(brightnessHold?12:screenshotOnly?11:10,d,r,0))throw new IllegalStateException("Reconnect Shizuku after updating");r.readException();}finally{d.recycle();r.recycle();}
 }
 static void stop(){try{Parcel p=request(4,null,null);p.recycle();}catch(Exception ignored){}}
 static void cleanup(){try{Parcel p=request(6,null,null);p.recycle();}catch(Exception ignored){}}
}
