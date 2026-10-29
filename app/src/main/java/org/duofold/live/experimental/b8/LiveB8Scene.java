package org.duofold.live.experimental.b8;

import android.os.*;
import android.view.SurfaceControl;
import java.io.File;
import java.util.concurrent.*;

/** Owned, parentless roots. App-produced buffers keep moving during window remaps.
 * No display capture and no mirror graph. Missing mapping retains both old roots.
 * Full-extent opaque guards prevent Home exposure during a sampled stack exchange.
 */
// The controller selects this implementation only when supported() is true.
@android.annotation.TargetApi(35)
final class LiveB8Scene {
 private final SurfaceControl[] roots=new SurfaceControl[2],guards=new SurfaceControl[2];
 private final String[] ids=new String[2];
 private final int[] stacks=new int[2],widths=new int[2],heights=new int[2],rotations=new int[2];
 private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
 private final long started=SystemClock.elapsedRealtime();
 private volatile long lease=started;
 private volatile boolean closed;
 private boolean removing,mappingReady,mappingPending,observationValid=true;private long mappingSerial;
 static boolean supported(){return Build.VERSION.SDK_INT>=35;}
 private static void stack(SurfaceControl.Transaction t,SurfaceControl s,int stack)throws Exception{
  SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,s,stack);
 }
 LiveB8Scene(File unused,LiveB8Power power)throws Exception{
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   for(int n=0;n<2;n++){
    Object i=power.info(n);ids[n]=LiveB8Power.physical(i);stacks[n]=LiveB8Power.number(i,"layerStack");
    widths[n]=LiveB8Power.number(i,"logicalWidth");heights[n]=LiveB8Power.number(i,"logicalHeight");rotations[n]=LiveB8Power.number(i,"rotation");
    SurfaceControl.Builder b=new SurfaceControl.Builder().setName("DuoB8MovingRoot_"+n);
    SurfaceControl.Builder.class.getMethod("setContainerLayer").invoke(b);roots[n]=b.build();
    stack(t,roots[n],stacks[n]);t.setLayer(roots[n],2147483646).setVisibility(roots[n],false);
   }
   if(!SceneMapping.complete(ids[0],stacks[0],ids[1],stacks[1],ids[0],ids[1]))throw new IllegalStateException("Ambiguous initial scene mapping");
   int extent=Math.max(Math.max(widths[0],heights[0]),Math.max(widths[1],heights[1]));
   for(int n=0;n<2;n++){
    SurfaceControl.Builder b=new SurfaceControl.Builder().setName("DuoB8OpaqueGuard_"+n).setParent(roots[n]);
    SurfaceControl.Builder.class.getMethod("setColorLayer").invoke(b);guards[n]=b.build();
    SurfaceControl.Transaction.class.getMethod("setColor",SurfaceControl.class,float[].class).invoke(t,guards[n],new float[]{0,0,0});
    t.setCrop(guards[n],new android.graphics.Rect(0,0,extent,extent)).setLayer(guards[n],0).setOpaque(guards[n],true).setVisibility(guards[n],true);
   }
   mappingPending=true;
   t.addTransactionCompletedListener(Runnable::run,stats->{synchronized(this){if(!removing&&mappingSerial==0){mappingReady=true;mappingPending=false;}}}).apply();
  }catch(Exception e){close();throw e;}
  watchdog.scheduleWithFixedDelay(()->{long now=SystemClock.elapsedRealtime();if(now-lease>750||now-started>=30000)close();},100,100,TimeUnit.MILLISECONDS);
 }
 synchronized void refresh(LiveB8Power power)throws Exception{
  if(removing||closed)throw new IllegalStateException("Independent scene lease ended");lease=SystemClock.elapsedRealtime();
  Object a=power.info(0),b=power.info(1);if(a==null||b==null){observationValid=false;return;}
  String ai=LiveB8Power.physical(a),bi=LiveB8Power.physical(b);int sa=LiveB8Power.number(a,"layerStack"),sb=LiveB8Power.number(b,"layerStack");
  if(!SceneMapping.complete(ai,sa,bi,sb,ids[0],ids[1])){observationValid=false;return;}
  observationValid=true;int next0=ids[0].equals(ai)?sa:sb,next1=ids[1].equals(ai)?sa:sb;
  if(next0==stacks[0]&&next1==stacks[1]){if(!mappingReady&&!mappingPending)submitMapping(next0,next1);return;}
  submitMapping(next0,next1);
 }
 private void submitMapping(int a,int b)throws Exception{
  mappingReady=false;mappingPending=true;long serial=++mappingSerial;
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   stack(t,roots[0],a);stack(t,roots[1],b);stacks[0]=a;stacks[1]=b;
   t.addTransactionCompletedListener(Runnable::run,stats->{synchronized(this){if(!removing&&serial==mappingSerial){mappingReady=true;mappingPending=false;}}}).apply();
  }
 }
 synchronized boolean mapped(){return !removing&&mappingReady&&observationValid;}
 synchronized void opacity(float value){
  if(removing||closed)return;
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   for(SurfaceControl root:roots)if(root!=null&&root.isValid())t.setAlpha(root,Math.max(0f,Math.min(1f,value)));
   t.apply();
  }
 }
 synchronized Object handles(){
  if(removing||closed)return null;
  Bundle all=new Bundle();
  for(int n=0;n<2;n++){
   Bundle row=new Bundle();row.putString("physical",ids[n]);row.putInt("width",widths[n]);row.putInt("height",heights[n]);row.putInt("rotation",rotations[n]);
   // Independent parcel reference survives controller cleanup while reply is written.
   Parcel p=Parcel.obtain();try{p.writeTypedObject(roots[n],0);p.setDataPosition(0);row.putParcelable("root",p.readTypedObject(SurfaceControl.CREATOR));}finally{p.recycle();}
   all.putBundle("p"+n,row);
  }
  return all;
 }
 synchronized boolean closed(){return closed;}
 synchronized void close(){
  if(removing)return;removing=true;mappingReady=false;mappingSerial++;
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   for(SurfaceControl s:roots)if(s!=null&&s.isValid()){stack(t,s,-1);t.setVisibility(s,false).reparent(s,null);}
   // A color guard must never become a visible parentless layer during teardown.
   for(SurfaceControl s:guards)if(s!=null&&s.isValid()){stack(t,s,-1);t.setVisibility(s,false).reparent(s,null);}
   watchdog.schedule(()->{if(!closed)android.os.Process.killProcess(android.os.Process.myPid());},2,TimeUnit.SECONDS);
   t.addTransactionCompletedListener(Runnable::run,stats->{synchronized(this){
    for(SurfaceControl s:guards)if(s!=null)s.release();for(SurfaceControl s:roots)if(s!=null)s.release();closed=true;watchdog.shutdown();
   }}).apply();
  }catch(Exception e){android.os.Process.killProcess(android.os.Process.myPid());}
 }
}
