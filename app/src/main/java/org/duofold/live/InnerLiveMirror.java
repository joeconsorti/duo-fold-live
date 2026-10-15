package org.duofold.live;
import android.os.*;
import android.graphics.Rect;
import android.view.SurfaceControl;
/** Live inner-to-cover compositor mirror; no bitmap capture, saved screenshots or protected-content overrides. */
final class InnerLiveMirror {
 private volatile SurfaceControl mirror;private int owner;
 String status="Cover live mirror idle";
 // A lock instead of the monitor so revoke() can tryLock and never wait out a 250 ms attach commit.
 private final java.util.concurrent.locks.ReentrantLock lock=new java.util.concurrent.locks.ReentrantLock();
 private final MirrorAttachGate gate=new MirrorAttachGate();
 /** Take before reading "preview allowed" (AngleReader code 4). */
 int ticket(){return gate.ticket();}
 private Object service(String name,String stub)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 Bundle attach(int id,SurfaceControl parent,int width,int height,boolean allowed){return attach(id,parent,width,height,allowed,gate.ticket());}
 Bundle attach(int id,SurfaceControl parent,int width,int height,boolean allowed,int ticket){
  lock.lock();
  long identity=Binder.clearCallingIdentity();Bundle result=new Bundle();SurfaceControl built=null;
  try{
   close();
   if(!allowed||parent==null||!parent.isValid())throw new IllegalStateException("Cover mirror not currently eligible");
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
   if(Math.min(sw,sh)/(float)Math.max(sw,sh)>.7f || Math.min(dw,dh)/(float)Math.max(dw,dh)<=.7f)throw new IllegalStateException("Cover-to-inner preview only");
   int sr=source.getClass().getField("rotation").getInt(source),dr=dest.getClass().getField("rotation").getInt(dest);
   boolean landscape=sw>sh;
   float[] legacy=landscape?null:LiveMirrorLayout.fit(sw,sh,width,height);
   float[] matrix=landscape?LandscapePreviewLayout.transform(sw,sh,sr,width,height,dr,false):new float[]{legacy[0],0,0,legacy[0],legacy[1],legacy[2]};
   java.util.concurrent.CountDownLatch committed=new java.util.concurrent.CountDownLatch(1);
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,mirror,matrix[0],matrix[1],matrix[2],matrix[3]);
    t.addTransactionCommittedListener(Runnable::run,committed::countDown);
    t.reparent(mirror,parent).setLayer(mirror,1).setCrop(mirror,new Rect(0,0,sw,sh)).setPosition(mirror,matrix[4],matrix[5]).setVisibility(mirror,true).apply();
   }
   if(!committed.await(250,java.util.concurrent.TimeUnit.MILLISECONDS))throw new IllegalStateException("Right preview commit not yet confirmed; retrying");
   // Revoked (preview no longer allowed, or reader stopped) while this attach was in flight: close what it built.
   if(!gate.valid(ticket))throw new IllegalStateException("Preview revoked during attach");
   built=mirror;owner=id;status="Live cover → inner preview; source="+sw+"x"+sh+" r="+sr+" destination="+width+"x"+height+" r="+dr+" matrix="+java.util.Arrays.toString(matrix);result.putBoolean("ok",true);
  }catch(Exception e){close();Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();status="Cover mirror unavailable: "+cause.getClass().getSimpleName()+": "+cause.getMessage();}
  finally{if(parent!=null)parent.release();Binder.restoreCallingIdentity(identity);lock.unlock();}
  // A revoke between the check above and the unlock finds the lock held, so its tryLock skips the close. Re-check
  // after unlocking: either revoke saw the lock free and closed, or the bumped generation is visible here.
  if(built!=null&&!gate.valid(ticket)){
   lock.lock();try{if(mirror==built){close();status="Preview revoked during attach";result.putBoolean("ok",false);}}finally{lock.unlock();}
  }
  result.putString("status",status);return result;
 }
 void detach(int id){lock.lock();try{if(owner==id)close();}finally{lock.unlock();}}
 /** From the angle poll: never blocks. An attach in flight sees the bumped generation and closes itself. */
 void revoke(){
  gate.revoke();
  if(lock.tryLock()){try{if(mirror!=null)close();}finally{lock.unlock();}}
 }
 /** Reader stop: invalidate any attach in flight, then close (may wait for it; teardown only). */
 void shutdown(){gate.revoke();lock.lock();try{close();}finally{lock.unlock();}}
 void close(){
  lock.lock();
  try{
   if(mirror!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(mirror,false).reparent(mirror,null).apply();}catch(Exception ignored){}mirror.release();mirror=null;status="Cover live mirror released";}
   owner=0;
  }finally{lock.unlock();}
 }
}
