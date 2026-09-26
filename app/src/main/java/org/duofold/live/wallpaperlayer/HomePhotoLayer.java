package org.duofold.live.wallpaperlayer;

import android.app.UiAutomation;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Own surfaces only: a negative-Z anchor under Home, and an opaque photo relative to it. */
final class HomePhotoLayer {
 final Context context;final UiAutomation automation;final Handler main;final Consumer<String> note;
 final ExecutorService worker=Executors.newSingleThreadExecutor();final AtomicBoolean querying=new AtomicBoolean();
 final Map<Integer,Entry> entries=new HashMap<>();final Map<Integer,Entry> pending=new HashMap<>();
 final Object client;final int connection;final Method attach,relative,layerStack;
 volatile boolean closed;volatile String status="Home photo waiting for launcher";long retryAt;
 Bitmap hardware;HardwareBuffer buffer;
 HomePhotoLayer(Context c,UiAutomation a,Handler h,Consumer<String> n,Bitmap photo)throws Exception{
  context=c;automation=a;main=h;note=n;
  Class<?> api=Class.forName("android.view.accessibility.AccessibilityInteractionClient");
  client=api.getMethod("getInstance").invoke(null);
  connection=(Integer)UiAutomation.class.getMethod("getConnectionId").invoke(a);
  attach=api.getMethod("attachAccessibilityOverlayToWindow",int.class,int.class,SurfaceControl.class,Executor.class,IntConsumer.class);
  relative=SurfaceControl.Transaction.class.getMethod("setRelativeLayer",SurfaceControl.class,SurfaceControl.class,int.class);
  layerStack=SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class);
  setPhoto(photo);
 }
 void requestRefresh(){
  if(closed||!querying.compareAndSet(false,true))return;
  try{worker.execute(()->{try{
   if(!context.getSystemService(PowerManager.class).isInteractive())return;
   ResolveInfo home=context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
   if(home==null||home.activityInfo==null){main.post(()->status="Home photo: default launcher unavailable");return;}
   String pkg=home.activityInfo.packageName;
   ArrayList<Target> targets=new ArrayList<>();
   android.util.SparseArray<List<AccessibilityWindowInfo>> windows=automation.getWindowsOnAllDisplays();
   for(int i=0;i<windows.size();i++)for(AccessibilityWindowInfo w:windows.valueAt(i))try{
    if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||w.getDisplayId()<0||w.getDisplayId()>1)continue;
    AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;
    boolean matches;try{matches=pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName());}finally{root.recycle();}
    if(!matches)continue;
    Rect bounds=new Rect();w.getBoundsInScreen(bounds);
    Display d=context.getSystemService(DisplayManager.class).getDisplay(w.getDisplayId());if(d==null||!d.isValid())continue;
    Point size=new Point();d.getRealSize(size);
    if(!HomePhotoPolicy.accepts(bounds.width(),bounds.height(),size.x,size.y))continue;
    targets.add(new Target(w.getId(),w.getDisplayId(),pkg));
   }finally{w.recycle();}
   main.post(()->{if(closed)return;for(Target t:targets)ensure(t);});
  }catch(Throwable e){main.post(()->{if(!closed)status="Home photo lookup waiting: "+e.getClass().getSimpleName();});}finally{querying.set(false);}});}catch(RejectedExecutionException ignored){querying.set(false);}
 }
 void ensure(Target target){
  if(closed||SystemClock.elapsedRealtime()<retryAt)return;
  Entry old=entries.get(target.display);
  if(old!=null&&old.target.window==target.window){refreshGeometry();return;}
  if(pending.containsKey(target.display))return;
  Entry e=null;
  try{
   SurfaceControl anchor=new SurfaceControl.Builder().setName("Duo Home wallpaper anchor").setBufferSize(1,1).setHidden(true).build();
   e=new Entry(target,anchor);pending.put(target.display,e);
   e.photo=new SurfaceControl.Builder().setName("Duo Home transition photo").setBufferSize(hardware.getWidth(),hardware.getHeight()).setOpaque(true).setHidden(true).build();
   try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){tx.setLayer(anchor,-1).setVisibility(anchor,true).apply();}
   Entry next=e;
   attach.invoke(client,connection,target.window,anchor,(Executor)r->main.post(r),(IntConsumer)result->attached(next,result));
   note.accept("Home photo attachment requested: "+target.pkg+" window="+target.window+" display="+target.display);
  }catch(Throwable error){if(e!=null){pending.remove(target.display);e.close();}retryAt=SystemClock.elapsedRealtime()+5000;status="Home photo unavailable; existing wallpaper retained: "+error.getClass().getSimpleName();note.accept(status);}
 }
 void attached(Entry e,int result){
  if(closed||pending.get(e.target.display)!=e){e.close();return;}
  pending.remove(e.target.display);
  if(result!=0){e.close();retryAt=SystemClock.elapsedRealtime()+5000;status="Home photo attach rejected ("+result+"); existing wallpaper retained";note.accept(status);return;}
  Entry previous=entries.get(e.target.display);
  try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
   tx.setLayer(e.anchor,-1).setVisibility(e.anchor,true);
   // Relative Z inherits hidden state, but not the launcher's alpha or transform.
   relative.invoke(tx,e.photo,e.anchor,1);
   tx.setBuffer(e.photo,buffer).setAlpha(e.photo,1f);geometry(tx,e);tx.setVisibility(e.photo,true);
   if(previous!=null)previous.detach(tx);
   tx.apply();entries.put(e.target.display,e);if(previous!=null)previous.release();
   status="Home transition photo attached below launcher content on display "+e.target.display;
   note.accept(status+"; window="+e.target.window);
  }catch(Throwable error){e.close();retryAt=SystemClock.elapsedRealtime()+5000;status="Home photo placement failed; existing wallpaper retained: "+error.getClass().getSimpleName();note.accept(status);}
 }
 void geometry(SurfaceControl.Transaction tx,Entry e)throws Exception{
  Display d=context.getSystemService(DisplayManager.class).getDisplay(e.target.display);
  if(d==null||!d.isValid())throw new IllegalStateException("Display unavailable");
  Point size=new Point();d.getRealSize(size);if(size.x<=0||size.y<=0)throw new IllegalStateException("Empty display");
  int stack=(Integer)Display.class.getMethod("getLayerStack").invoke(d);layerStack.invoke(tx,e.photo,stack);
  Rect src=new Rect(0,0,hardware.getWidth(),hardware.getHeight());float scale=Math.max((float)size.x/src.width(),(float)size.y/src.height());
  int w=Math.min(src.width(),Math.max(1,Math.round(size.x/scale))),h=Math.min(src.height(),Math.max(1,Math.round(size.y/scale)));
  src.set((hardware.getWidth()-w)/2,(hardware.getHeight()-h)/2,(hardware.getWidth()+w)/2,(hardware.getHeight()+h)/2);
  tx.setGeometry(e.photo,src,new Rect(0,0,size.x,size.y),Surface.ROTATION_0);
 }
 void refreshGeometry(){if(closed)return;try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){for(Entry e:entries.values())geometry(tx,e);tx.apply();}catch(Exception e){status="Home photo geometry waiting: "+e.getClass().getSimpleName();}}
 void setPhoto(Bitmap photo){
  Bitmap next=photo.copy(Bitmap.Config.HARDWARE,false);if(next==null)throw new IllegalStateException("Cannot prepare Home photo");
  HardwareBuffer old=buffer;hardware=next;buffer=next.getHardwareBuffer();
  try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){for(Entry e:entries.values()){tx.setBuffer(e.photo,buffer);geometry(tx,e);}tx.apply();}catch(Exception e){status="Home photo update failed: "+e.getClass().getSimpleName();}finally{if(old!=null)old.close();}
 }
 void close(){if(closed)return;closed=true;worker.shutdown();for(Entry e:pending.values())e.close();pending.clear();for(Entry e:entries.values())e.close();entries.clear();if(buffer!=null){buffer.close();buffer=null;}hardware=null;}
 static final class Target{final int window,display;final String pkg;Target(int w,int d,String p){window=w;display=d;pkg=p;}}
 static final class Entry{final Target target;final SurfaceControl anchor;SurfaceControl photo;boolean released;Entry(Target t,SurfaceControl a){target=t;anchor=a;}void detach(SurfaceControl.Transaction tx){if(photo!=null)tx.setVisibility(photo,false).reparent(photo,null);tx.setVisibility(anchor,false).reparent(anchor,null);}void release(){if(released)return;released=true;if(photo!=null)photo.release();anchor.release();}void close(){if(released)return;try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){detach(tx);tx.apply();}catch(RuntimeException ignored){}finally{release();}}}
}
