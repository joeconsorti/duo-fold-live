package org.duofold.live;
import android.os.*;
import android.graphics.Rect;
import android.view.SurfaceControl;
/** Isolated, fixed-primary diagnostic mirror. Never changes ordinary animation mirrors. */
final class CameraContentMirror {
 private SurfaceControl mirror;private int owner;
 String status="Camera content mirror idle";
 private Object service(String name,String stub)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 Bundle attach(int id,SurfaceControl parent,int width,int height,boolean allowed){
  long identity=Binder.clearCallingIdentity();Bundle result=new Bundle();
  try{
   close();
   if(!allowed||parent==null||!parent.isValid())throw new IllegalStateException("Camera content mirror not currently eligible");
   Object dm=service("display","android.hardware.display.IDisplayManager$Stub");
   Class<?> api=Class.forName("android.hardware.display.IDisplayManager");
   Object source=api.getMethod("getDisplayInfo",int.class).invoke(dm,0),dest=api.getMethod("getDisplayInfo",int.class).invoke(dm,1);
   if(source==null||dest==null)throw new IllegalStateException("Both panels must exist");
   int sw=source.getClass().getField("logicalWidth").getInt(source),sh=source.getClass().getField("logicalHeight").getInt(source);
   int dw=dest.getClass().getField("logicalWidth").getInt(dest),dh=dest.getClass().getField("logicalHeight").getInt(dest);
   if(java.util.Objects.equals(source.getClass().getField("uniqueId").get(source),dest.getClass().getField("uniqueId").get(dest)))throw new IllegalStateException("Refusing same-panel feedback mirror");
   Object wm=service("window","android.view.IWindowManager$Stub");
   mirror=SurfaceControl.class.getConstructor().newInstance();
   boolean accepted=(boolean)Class.forName("android.view.IWindowManager").getMethod("mirrorDisplay",int.class,SurfaceControl.class).invoke(wm,0,mirror);
   if(!accepted||!mirror.isValid())throw new IllegalStateException("WindowManager refused live mirror");

   float scale=Math.min(width/(float)sw,height/(float)sh);
   float[] fit={scale,(width-sw*scale)/2f,(height-sh*scale)/2f};
   java.util.concurrent.CountDownLatch committed=new java.util.concurrent.CountDownLatch(1);
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,mirror,fit[0],0f,0f,fit[0]);
    t.addTransactionCommittedListener(Runnable::run,committed::countDown);
    t.reparent(mirror,parent).setLayer(mirror,1).setCrop(mirror,new Rect(0,0,sw,sh)).setPosition(mirror,fit[1],fit[2]).setVisibility(mirror,true).apply();
   }
   if(!committed.await(250,java.util.concurrent.TimeUnit.MILLISECONDS))throw new IllegalStateException("Content mirror commit not yet confirmed");
   owner=id;status="Live primary content mirrored; center-fit, read-only";result.putBoolean("ok",true);
  }catch(Exception e){close();Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();status="Camera content mirror unavailable: "+cause.getClass().getSimpleName()+": "+cause.getMessage();}
  finally{if(parent!=null)parent.release();Binder.restoreCallingIdentity(identity);}
  result.putString("status",status);return result;
 }
 void detach(int id){if(owner==id)close();}
 void close(){
  if(mirror!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(mirror,false).reparent(mirror,null).apply();}catch(Exception ignored){}mirror.release();mirror=null;status="Camera content mirror released";}
  owner=0;
 }
}
