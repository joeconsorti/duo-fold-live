package org.duofold.live;
import android.os.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
/**
 * An owned, non-interactive compositor bridge. Uses an already prepared clean frame, never
 * requests a display state or secure capture. Fails closed on lock, stale lease, or timeout.
 */
final class PreviewExpansion extends Binder {
 static final String TOKEN="org.duofold.live.PreviewExpansion";
 private final int owner;
 private final HandlerThread thread=new HandlerThread("duo-preview-expansion");
 private final Handler handler;
 private Object dm,wm;private java.lang.reflect.Method displayInfo,keyguard;
 private SurfaceControl layer,backdrop;private Bitmap hardware;private HardwareBuffer buffer;
 private String innerId;private int bw,bh;
 private volatile boolean enabled=false;
 private long stamp,start=0,ready=-1;private volatile long lastLease=0;
 private boolean polling=false,completed=false,pendingReady=false;
 private volatile boolean closed=false;
 volatile String status="Expansion idle";
 PreviewExpansion(int owner){this.owner=owner;thread.start();handler=new Handler(thread.getLooper());attachInterface(null,TOKEN);}
 void enabled(boolean value){boolean changed=enabled!=value;enabled=value;lastLease=SystemClock.elapsedRealtime();if(!value && changed)handler.post(()->clear("Expansion disabled"));}
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(closed)throw new IllegalStateException("Expansion helper closed");
  data.enforceInterface(TOKEN);if(Binder.getCallingUid()!=owner)throw new SecurityException("Wrong caller");
  if(code==1){
   Bitmap bitmap=data.readTypedObject(Bitmap.CREATOR);long captured=data.readLong();
   if(bitmap==null)throw new IllegalArgumentException("No prepared frame");
   handler.post(()->{try{prepare(bitmap,captured);}catch(Exception e){clear("Expansion prepare failed: "+root(e));}finally{bitmap.recycle();}});
  }else if(code==2){handler.post(()->{if(layer!=null){pendingReady=true;if(start>0 && ready<0){ready=SystemClock.elapsedRealtime()-start;status="Inner content received; expansion fading";}}});}
  else if(code==3){handler.post(()->{completed=false;clear("Expansion reset");});}
  else throw new IllegalArgumentException("Unknown bridge operation");
  reply.writeNoException();reply.writeString(status);return true;
 }
 private static String root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
 private Object service(String name,String stub)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 private void init()throws Exception{
  if(dm!=null)return;
  dm=service("display","android.hardware.display.IDisplayManager$Stub");
  displayInfo=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class);
  wm=service("window","android.view.IWindowManager$Stub");keyguard=Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked");
 }
 private int number(Object info,String field)throws Exception{return info.getClass().getField(field).getInt(info);}
 private String id(Object info)throws Exception{return (String)info.getClass().getField("uniqueId").get(info);}
 private boolean inner(Object info)throws Exception{
  int w=number(info,"logicalWidth"),h=number(info,"logicalHeight");return Math.min(w,h)/(float)Math.max(w,h)>.7f;
 }
 private void prepare(Bitmap bitmap,long captured)throws Exception{
  long now=SystemClock.elapsedRealtime();
  if(!enabled || !PreviewExpansionPolicy.fresh(captured,now) || start>0)return;
  init();Object primary=displayInfo.invoke(dm,0),secondary=displayInfo.invoke(dm,1);
  if(primary==null || secondary==null || inner(primary) || !inner(secondary) || (boolean)keyguard.invoke(wm))return;
  if(completed)return; // Wait for the app to reset after leaving this cover session.
  innerId=id(secondary);stamp=captured;
  Bitmap next=bitmap.copy(Bitmap.Config.HARDWARE,false);HardwareBuffer nextBuffer=next.getHardwareBuffer();
  if(layer==null)layer=new SurfaceControl.Builder().setName("Duo preview expansion bridge").setBufferSize(bitmap.getWidth(),bitmap.getHeight()).setOpaque(true).setHidden(true).build();
  if(backdrop==null)backdrop=new SurfaceControl.Builder().setName("Duo expansion frosted backdrop").setBufferSize(bitmap.getWidth(),bitmap.getHeight()).setOpaque(true).setHidden(true).build();
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,backdrop,true);
   t.setBuffer(backdrop,nextBuffer).setLayer(backdrop,Integer.MAX_VALUE-21).setAlpha(backdrop,1f).setVisibility(backdrop,false);
   // Own surface only. Excluded from mirrored content and subsequent effect captures.
   SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,layer,true);
   t.setBuffer(layer,nextBuffer).setLayer(layer,Integer.MAX_VALUE-20).setAlpha(layer,1f).setVisibility(layer,false).apply();
  }
  if(buffer!=null)buffer.close();if(hardware!=null)hardware.recycle();
  hardware=next;buffer=nextBuffer;bw=bitmap.getWidth();bh=bitmap.getHeight();
  status="Clean expansion frame prepared";
  if(!polling){polling=true;handler.post(tick);}
 }
 private final Runnable tick=new Runnable(){public void run(){
  try{
   long now=SystemClock.elapsedRealtime();
   if(!enabled || now-lastLease>1000 || layer==null || (boolean)keyguard.invoke(wm)){clear("Expansion paused");return;}
   if(start==0 && !PreviewExpansionPolicy.fresh(stamp,now)){clear("Expansion waiting for fresh cover frame");return;}
   Object primary=displayInfo.invoke(dm,0),secondary=displayInfo.invoke(dm,1);
   Object target=primary!=null && innerId.equals(id(primary))?primary:secondary!=null && innerId.equals(id(secondary))?secondary:null;
   if(target==null){if(start>0 && now-start>1500){clear("Expansion panel disappeared");return;}handler.postDelayed(this,8);return;}
   boolean switched=primary!=null && innerId.equals(id(primary));
   boolean coverOff=primary!=null && !inner(primary) && number(primary,"state")!=2;
   if(start==0 && (switched || coverOff)){
    if(!PreviewExpansionPolicy.fresh(stamp,now)){clear("Expansion skipped: stale prepared frame");return;}
    start=now;ready=pendingReady?0:-1;status="Frosted expansion started";
   }
   if(start>0){
    long elapsed=now-start;float alpha=PreviewExpansionPolicy.opacity(elapsed,ready);
    if(alpha<=0){completed=true;clear("Expansion complete");return;}
    float progress=PreviewExpansionPolicy.progress(elapsed);
    int w=number(target,"logicalWidth"),h=number(target,"logicalHeight");
    float fit=Math.min(w/(float)bw,h/(float)bh);
    float sx=fit+(w/(float)bw-fit)*progress,sy=fit+(h/(float)bh-fit)*progress;
    try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
     SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,layer,number(target,"layerStack"));
     SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,layer,sx,0f,0f,sy);
     SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,backdrop,number(target,"layerStack"));
     SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,backdrop,w/(float)bw,0f,0f,h/(float)bh);
     t.setPosition(backdrop,0,0).setAlpha(backdrop,alpha).setVisibility(backdrop,number(target,"state")==2);
     t.setPosition(layer,w-bw*sx,(h-bh*sy)/2f).setAlpha(layer,alpha).setVisibility(layer,number(target,"state")==2).apply();
    }
   }else if(!PreviewExpansionPolicy.fresh(stamp,now)){clear("Expansion waiting for fresh cover frame");return;}
   handler.postDelayed(this,8);
  }catch(Exception e){clear("Expansion failed: "+root(e));}
 }};
 private void clear(String message){
  handler.removeCallbacks(tick);polling=false;start=0;ready=-1;pendingReady=false;
  if(layer!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(layer,false).reparent(layer,null).apply();}catch(Exception ignored){}layer.release();layer=null;}
  if(backdrop!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(backdrop,false).reparent(backdrop,null).apply();}catch(Exception ignored){}backdrop.release();backdrop=null;}
  if(buffer!=null){buffer.close();buffer=null;}if(hardware!=null){hardware.recycle();hardware=null;}status=message;
  if(!enabled)completed=false;
 }
 void close(){closed=true;enabled=false;handler.post(()->{clear("Expansion stopped");thread.quitSafely();});}
}
