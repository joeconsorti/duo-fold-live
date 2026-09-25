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
 private SurfaceControl layer,backdrop;private Bitmap hardware,cleanHardware;private HardwareBuffer buffer,cleanBuffer;
 private String innerId;private int bw,bh;
 private volatile boolean enabled=false;
 private long stamp,start=0,ready=-1;private volatile long lastLease=0;
 private boolean polling=false,completed=false,pendingReady=false;
 private volatile boolean closed=false;
 private java.util.concurrent.CountDownLatch coverCommit;
 volatile String trace="No handoff yet";
 private int lastStack=-1,lastPanelState=-1;
 private long lastSample=0,maxSampleGap=0,offSince=-1,missingSince=-1,diagnosticStart=0;
 private String lastMapping="";
 private synchronized void diagnosticEvent(String text){event(text);}
 private String panel(Object info)throws Exception{
  if(info==null)return "absent";
  return (inner(info)?"inner":"cover")+" state="+number(info,"state")+" stack="+number(info,"layerStack")+" "+number(info,"logicalWidth")+"x"+number(info,"logicalHeight");
 }
 private void sample(Object primary,Object secondary,Object target,long now)throws Exception{
  if(diagnosticStart==0)return;
  if(lastSample>0)maxSampleGap=Math.max(maxSampleGap,now-lastSample);
  lastSample=now;
  String mapping="d0 "+panel(primary)+"; d1 "+panel(secondary);
  if(!mapping.equals(lastMapping)){lastMapping=mapping;diagnosticEvent(mapping);}
  if(target==null){if(missingSince<0){missingSince=now;diagnosticEvent("Inner mapping absent");}return;}
  if(missingSince>=0){diagnosticEvent("Inner mapping restored after "+(now-missingSince)+" ms (sampled)");missingSince=-1;}
  boolean off=number(target,"state")==1;
  if(off && offSince<0){offSince=now;diagnosticEvent("Inner reports OFF");}
  if(!off && offSince>=0){diagnosticEvent("Inner OFF interval "+(now-offSince)+" ms (sampled)");offSince=-1;}
 }
 void releaseReturned(){final long when=SystemClock.elapsedRealtime();handler.post(()->{if(diagnosticStart>0)diagnosticEvent("Cover-primary handoff call returned at +"+(when-diagnosticStart)+" ms");});}
 private synchronized void event(String text){trace=(trace+" | "+SystemClock.elapsedRealtime()+": "+text);if(trace.length()>6000)trace=trace.substring(trace.length()-6000);}
 volatile String status="Expansion idle";
 PreviewExpansion(int owner){this.owner=owner;thread.start();handler=new Handler(thread.getLooper());attachInterface(null,TOKEN);}
 void enabled(boolean value){boolean changed=enabled!=value;enabled=value;lastLease=SystemClock.elapsedRealtime();if(!value && changed)handler.post(()->clear("Expansion disabled"));}
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(closed)throw new IllegalStateException("Expansion helper closed");
  data.enforceInterface(TOKEN);if(Binder.getCallingUid()!=owner)throw new SecurityException("Wrong caller");
  if(code==1){
   Bitmap bitmap=data.readTypedObject(Bitmap.CREATOR);Bitmap clean=data.readTypedObject(Bitmap.CREATOR);long captured=data.readLong();
   if(bitmap==null || clean==null)throw new IllegalArgumentException("No prepared frame");
   handler.post(()->{try{prepare(bitmap,clean,captured);}catch(Exception e){clear("Expansion prepare failed: "+root(e));}finally{bitmap.recycle();clean.recycle();}});
  }else if(code==2){boolean endpoint=data.dataAvail()>=4&&data.readInt()!=0;handler.post(()->{if(layer!=null){pendingReady=true;event(endpoint?"Fully-open clear frame committed":"Fresh inner glass frame committed");if(start>0 && ready<0){ready=SystemClock.elapsedRealtime()-start;status=endpoint?"Fully open; expansion fading":"Inner glass committed; expansion fading";}}});}
  else if(code==3){handler.post(()->{completed=false;clear("Expansion reset");});}
  else throw new IllegalArgumentException("Unknown bridge operation");
  reply.writeNoException();reply.writeString(status);return true;
 }
 void holdBeforeRelease(){
  if(!enabled || closed)return;
  java.util.concurrent.CountDownLatch committed=new java.util.concurrent.CountDownLatch(1);
  handler.post(()->{
   if(layer==null || start>0 || !PreviewExpansionPolicy.fresh(stamp,SystemClock.elapsedRealtime())){committed.countDown();return;}
   trace="";diagnosticStart=SystemClock.elapsedRealtime();lastSample=0;maxSampleGap=0;offSince=-1;missingSince=-1;lastMapping="";
   event("MEASURED SOFTWARE EVENTS ONLY: panel state is sampled; commit/draw is not photon visibility");
   event("Pre-release hold requested; prepared frame age="+(diagnosticStart-stamp)+" ms");
   start=SystemClock.elapsedRealtime();ready=-1;coverCommit=committed;
   handler.removeCallbacks(tick);tick.run();
  });
  try{boolean acknowledged=committed.await(24,java.util.concurrent.TimeUnit.MILLISECONDS);
   final long when=SystemClock.elapsedRealtime();handler.post(()->{if(diagnosticStart>0)diagnosticEvent("Pre-release wait ended +"+(when-diagnosticStart)+" ms; commit observed="+acknowledged);});}catch(InterruptedException e){Thread.currentThread().interrupt();}
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
 private void prepare(Bitmap bitmap,Bitmap clean,long captured)throws Exception{
  long now=SystemClock.elapsedRealtime();
  if(!enabled || !PreviewExpansionPolicy.fresh(captured,now) || start>0)return;
  init();Object primary=displayInfo.invoke(dm,0),secondary=displayInfo.invoke(dm,1);
  if(primary==null || secondary==null || inner(primary) || !inner(secondary) || (boolean)keyguard.invoke(wm))return;
  if(completed)return; // Wait for the app to reset after leaving this cover session.
  innerId=id(secondary);stamp=captured;
  Bitmap next=bitmap.copy(Bitmap.Config.HARDWARE,false);HardwareBuffer nextBuffer=next.getHardwareBuffer();
  Bitmap nextClean=Bitmap.createScaledBitmap(clean,bitmap.getWidth(),bitmap.getHeight(),true).copy(Bitmap.Config.HARDWARE,false);HardwareBuffer nextCleanBuffer=nextClean.getHardwareBuffer();
  if(layer==null)layer=new SurfaceControl.Builder().setName("Duo clean right hold").setBufferSize(bitmap.getWidth(),bitmap.getHeight()).setOpaque(true).setHidden(true).build();
  if(backdrop==null)backdrop=new SurfaceControl.Builder().setName("Duo prepared frosted left copy").setBufferSize(bitmap.getWidth(),bitmap.getHeight()).setOpaque(true).setHidden(true).build();
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,backdrop,true);
   t.setBuffer(backdrop,nextBuffer).setLayer(backdrop,Integer.MAX_VALUE-21).setAlpha(backdrop,1f);
   // Own surface only. Excluded from mirrored content and subsequent effect captures.
   SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,layer,true);
   t.setBuffer(layer,nextCleanBuffer).setLayer(layer,Integer.MAX_VALUE-20).setAlpha(layer,1f).setVisibility(layer,false).apply();
  }
  if(buffer!=null)buffer.close();if(hardware!=null)hardware.recycle();
  if(cleanBuffer!=null)cleanBuffer.close();if(cleanHardware!=null)cleanHardware.recycle();
  hardware=next;buffer=nextBuffer;cleanHardware=nextClean;cleanBuffer=nextCleanBuffer;bw=bitmap.getWidth();bh=bitmap.getHeight();
  status="Frosted left copy prepared; clean right hold ready";
  if(!polling){polling=true;handler.post(tick);}
 }
 private final Runnable tick=new Runnable(){public void run(){
  try{
   long now=SystemClock.elapsedRealtime();
   if(!enabled || now-lastLease>1000 || layer==null || (boolean)keyguard.invoke(wm)){clear("Expansion paused");return;}
   if(start==0 && !PreviewExpansionPolicy.fresh(stamp,now)){clear("Expansion waiting for fresh cover frame");return;}
   Object primary=displayInfo.invoke(dm,0),secondary=displayInfo.invoke(dm,1);
   Object target=primary!=null && innerId.equals(id(primary))?primary:secondary!=null && innerId.equals(id(secondary))?secondary:null;
   sample(primary,secondary,target,now);
   if(target==null){if(start>0 && now-start>1500){clear("Expansion panel disappeared");return;}handler.postDelayed(this,8);return;}
   boolean switched=primary!=null && innerId.equals(id(primary));
   boolean coverOff=primary!=null && !inner(primary) && number(primary,"state")!=2;
   if(start==0 && (switched || coverOff)){
    if(!PreviewExpansionPolicy.fresh(stamp,now)){clear("Expansion skipped: stale prepared frame");return;}
    start=now;ready=pendingReady?0:-1;status="Holding the existing two-column layout during handoff";
   }
   long elapsed=start==0?0:now-start;
   float alpha=start==0?1f:PreviewExpansionPolicy.opacity(elapsed,ready);
   if(alpha<=0){completed=true;clear("Prepared layout handed to inner content");return;}
   int stack=number(target,"layerStack"),panelState=number(target,"state");
   if(stack!=lastStack || panelState!=lastPanelState){lastStack=stack;lastPanelState=panelState;event("Inner stack="+stack+" state="+panelState);}
   int w=number(target,"logicalWidth"),h=number(target,"logicalHeight");
   float fit=Math.min(w/(float)bw,h/(float)bh);
   float leftWidth=Math.max(0f,w-bw*fit);
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,layer,number(target,"layerStack"));
    SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,layer,fit,0f,0f,fit);
    SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,backdrop,number(target,"layerStack"));
    SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,backdrop,leftWidth/bw,0f,0f,h/(float)bh);
    // The left copy is visible BEFORE handoff. Never hide either layer merely because
    // Android reports a transient OFF state during the physical panel remap.
    t.setPosition(backdrop,0,0).setAlpha(backdrop,alpha).setVisibility(backdrop,leftWidth>0);
    t.setPosition(layer,leftWidth,(h-bh*fit)/2f).setAlpha(layer,alpha).setVisibility(layer,start>0);
    if(coverCommit!=null){final java.util.concurrent.CountDownLatch fence=coverCommit;coverCommit=null;
     final long submitted=SystemClock.elapsedRealtime();
     t.addTransactionCommittedListener(Runnable::run,()->{long committedAt=SystemClock.elapsedRealtime();fence.countDown();diagnosticEvent("Replacement transaction committed in "+(committedAt-submitted)+" ms");});
     event("Replacement submitted before cover release");
    }
    t.apply();
   }
   handler.postDelayed(this,8);
  }catch(Exception e){clear("Expansion failed: "+root(e));}
 }};
 private void clear(String message){
  if(start>0){event(message+"; hold duration="+(SystemClock.elapsedRealtime()-start)+" ms; maximum state-sampling gap="+maxSampleGap+" ms");
   if(offSince>=0)event("OFF interval still open at cleanup");if(missingSince>=0)event("Missing mapping interval still open at cleanup");}
  diagnosticStart=0;if(coverCommit!=null){coverCommit.countDown();coverCommit=null;}
  handler.removeCallbacks(tick);polling=false;start=0;ready=-1;pendingReady=false;
  if(layer!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(layer,false).reparent(layer,null).apply();}catch(Exception ignored){}layer.release();layer=null;}
  if(backdrop!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(backdrop,false).reparent(backdrop,null).apply();}catch(Exception ignored){}backdrop.release();backdrop=null;}
  if(cleanBuffer!=null){cleanBuffer.close();cleanBuffer=null;}if(cleanHardware!=null){cleanHardware.recycle();cleanHardware=null;}
  if(buffer!=null){buffer.close();buffer=null;}if(hardware!=null){hardware.recycle();hardware=null;}status=message;
  if(!enabled)completed=false;
 }
 void close(){closed=true;enabled=false;handler.post(()->{clear("Expansion stopped");thread.quitSafely();});}
}
