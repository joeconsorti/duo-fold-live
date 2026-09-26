package org.duofold.live.wallpaperlayer;

import android.app.UiAutomation;
import android.app.KeyguardManager;
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
 final IntFunction<SurfaceControl> photoParent;final WindowAttachment attachment;final Method relative,layerStack;
 final Map<Integer,Integer> attempts=new HashMap<>();
 volatile boolean closed;volatile String status="Home photo waiting for launcher";long retryAt;
 final Map<Integer,Entry> bridges=new HashMap<>();final Map<Integer,Long> homeSeen=new HashMap<>();final Set<Integer> bridgeDisplays=new HashSet<>();
 volatile long bridgeEpoch;final WakeBridgePolicy bridgePolicy=new WakeBridgePolicy();boolean bridgeTickQueued,bridgeExpiryQueued;long bridgePollUntil;
 boolean keyguardLocked(){return context.getSystemService(KeyguardManager.class).isKeyguardLocked();}
 void finishBridge(int display,String reason){
  bridgeDisplays.remove(display);Entry e=bridges.remove(display);if(e!=null){e.close();note.accept("Wake photo bridge removed: "+reason+" display="+display);}
 }
 void resetBridges(){bridgeEpoch++;main.removeCallbacks(bridgeTick);bridgeTickQueued=false;bridgeExpiryQueued=false;for(Entry e:bridges.values())e.close();bridges.clear();bridgeDisplays.clear();bridgePolicy.reset();}
 void pollBridge(){
  if(closed||bridgeTickQueued)return;bridgePollUntil=SystemClock.elapsedRealtime()+2000;bridgeTickQueued=true;
  main.post(bridgeTick);
 }
 final Runnable bridgeTick=new Runnable(){public void run(){
  bridgeTickQueued=false;if(closed||bridgeDisplays.isEmpty()||!context.getSystemService(PowerManager.class).isInteractive())return;
  long now=SystemClock.elapsedRealtime();boolean locked=keyguardLocked();
  WakeBridgePolicy.State bridgeState=bridgePolicy.observe(locked,now);
  if(bridgeState==WakeBridgePolicy.State.SHOW&&!bridgeExpiryQueued){
   bridgeExpiryQueued=true;long epoch=bridgeEpoch;
   main.postDelayed(()->endBridgeHold(epoch),WakeBridgePolicy.HOLD_MS);
  }
  if(bridgeState==WakeBridgePolicy.State.EXPIRED){endBridgeHold(bridgeEpoch);return;}
  for(Entry e:new ArrayList<>(bridges.values()))if(e.bridgeReady&&!e.released){
   boolean show=bridgeState==WakeBridgePolicy.State.SHOW;
   if(show!=e.bridgeShown)try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
    geometry(tx,e);tx.setVisibility(e.photo,show).apply();e.bridgeShown=show;if(show)bridgePolicy.shown(SystemClock.elapsedRealtime());note.accept("Wake photo bridge "+(show?"shown":"hidden")+" display="+e.target.display);
   }catch(Throwable error){finishBridge(e.target.display,"placement failed: "+failure(error));}
  }
  if(now<bridgePollUntil){bridgeTickQueued=true;main.postDelayed(this,8);}
 }};
 void prepareBridge(Target target){
  if(closed||!bridgeDisplays.contains(target.display)||bridges.containsKey(target.display)||!keyguardLocked())return;
  Entry e=null;
  try{
   SurfaceControl parent=photoParent.apply(target.display);if(parent==null||!parent.isValid())return;
   e=new Entry(target,new SurfaceControl.Builder().setName("Duo wake bridge anchor").setBufferSize(1,1).setHidden(true).build());
   bridges.put(target.display,e);
   e.photo=new SurfaceControl.Builder().setName("Duo wake bridge photo").setParent(parent).setBufferSize(hardware.getWidth(),hardware.getHeight()).setOpaque(true).setHidden(true).build();
   try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){tx.setLayer(e.anchor,-1).setVisibility(e.anchor,true);relative.invoke(tx,e.photo,e.anchor,1);tx.setBuffer(e.photo,buffer);geometry(tx,e);tx.apply();}
   Entry next=e;long epoch=bridgeEpoch;
   main.postDelayed(()->bridgeReply(next,epoch,next.request.timeout(SystemClock.elapsedRealtime())),5000);
   attachment.request(target.window,e.anchor,main,result->bridgeReply(next,epoch,next.request.reply(SystemClock.elapsedRealtime(),result)));
  }catch(Throwable error){if(e!=null)e.close();bridges.remove(target.display);bridgeDisplays.remove(target.display);note.accept("Wake photo bridge unavailable: "+failure(error));}
 }
 void bridgeReply(Entry e,long epoch,AttachmentRequest.Result outcome){
  if(outcome==AttachmentRequest.Result.IGNORED)return;
  if(closed||epoch!=bridgeEpoch||bridges.get(e.target.display)!=e){e.close();return;}
  if(outcome!=AttachmentRequest.Result.SUCCESS){finishBridge(e.target.display,"attachment "+outcome);return;}
  e.bridgeReady=true;note.accept("Wake photo bridge prepared hidden below SystemUI; display="+e.target.display+" window="+e.target.window);pollBridge();
 }
 void endBridgeHold(long epoch){
  if(closed||epoch!=bridgeEpoch)return;
  long remaining=bridgePolicy.remaining(SystemClock.elapsedRealtime());
  if(remaining>0){main.postDelayed(()->endBridgeHold(epoch),remaining);return;}
  for(int display:new ArrayList<>(bridgeDisplays)){
   Entry home=entries.get(display);
   if(!keyguardLocked()&&home!=null&&home.homeReadyEpoch==epoch)homeReady(home);
   else finishBridge(display,"1000 ms hold complete; Home unavailable");
  }
 }
 void homeReady(Entry e){
  if(keyguardLocked()||!bridgeDisplays.contains(e.target.display))return;
  e.homeReadyEpoch=bridgeEpoch;
  if(bridgePolicy.remaining(SystemClock.elapsedRealtime())>0){
   Entry held=bridges.get(e.target.display);
   if(held!=null&&held.bridgeReady)try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
    geometry(tx,held);relative.invoke(tx,held.photo,e.anchor,1);tx.setVisibility(held.photo,true).apply();
    if(!held.bridgeShown){held.bridgeShown=true;bridgePolicy.shown(SystemClock.elapsedRealtime());note.accept("Wake photo bridge shown under Home; full 1000 ms hold display="+e.target.display);}
   }catch(Throwable error){note.accept("Wake photo hold parent waiting: "+failure(error));}
   pollBridge();return;
  }
  // Put the Home photo in place and hide the bridge in the same compositor transaction.
  Entry bridge=bridges.get(e.target.display);
  try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
   geometry(tx,e);relative.invoke(tx,e.photo,e.anchor,1);tx.setVisibility(e.photo,true);
   if(bridge!=null&&bridge.photo!=null)tx.setVisibility(bridge.photo,false);
   tx.apply();finishBridge(e.target.display,"Home handoff after full 1000 ms hold");
  }catch(Throwable error){note.accept("Wake photo handoff failed: "+failure(error));finishBridge(e.target.display,"hold complete; handoff failed");}
 }
 Bitmap hardware;HardwareBuffer buffer;
 HomePhotoLayer(Context c,UiAutomation a,Handler h,Consumer<String> n,Bitmap photo,IntFunction<SurfaceControl> parent)throws Exception{
  context=c;automation=a;main=h;note=n;photoParent=parent;
  attachment=new WindowAttachment(a);
  relative=SurfaceControl.Transaction.class.getMethod("setRelativeLayer",SurfaceControl.class,SurfaceControl.class,int.class);
  layerStack=SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class);
  setPhoto(photo);
 }
 long wakeGeneration;boolean reattachQueued;
 void windowChanged(){
  if(closed||reattachQueued)return;reattachQueued=true;
  main.postDelayed(()->{reattachQueued=false;if(closed)return;for(Entry e:entries.values())reattach(e);requestRefresh();pollBridge();},0);
 }
 void screenEvent(String action){
  long generation=++wakeGeneration;
  if(Intent.ACTION_SCREEN_OFF.equals(action)){
   resetBridges();long now=SystemClock.elapsedRealtime();for(Map.Entry<Integer,Long> seen:homeSeen.entrySet())if(now-seen.getValue()<1500)bridgeDisplays.add(seen.getKey());
   return;
  }
  if(Intent.ACTION_USER_PRESENT.equals(action))bridgePolicy.unlocked(SystemClock.elapsedRealtime());
  pollBridge();requestRefresh();
  for(int delay:new int[]{0,32,100,250,500})main.postDelayed(()->{
   if(closed||generation!=wakeGeneration)return;
   for(Entry e:entries.values())reattach(e);
   requestRefresh();
  },delay);
 }
 void reattach(Entry e){
  if(closed||e.released||e.rebind!=null||SystemClock.elapsedRealtime()<e.nextRebind)return;
  e.nextRebind=SystemClock.elapsedRealtime()+30;
  AttachmentRequest request=new AttachmentRequest(SystemClock.elapsedRealtime());e.rebind=request;
  main.postDelayed(()->rebound(e,request,request.timeout(SystemClock.elapsedRealtime()),-1),5000);
  try{attachment.request(e.target.window,e.anchor,main,result->rebound(e,request,request.reply(SystemClock.elapsedRealtime(),result),result));}
  catch(Throwable error){request.cancel();e.rebind=null;status="Home photo reattachment failed: "+failure(error);note.accept(status);}
 }
 void rebound(Entry e,AttachmentRequest request,AttachmentRequest.Result outcome,int result){
  if(outcome==AttachmentRequest.Result.IGNORED||e.rebind!=request)return;
  e.rebind=null;if(closed||e.released||entries.get(e.target.display)!=e)return;
  if(outcome!=AttachmentRequest.Result.SUCCESS){status="Home photo reattachment "+outcome+" ("+result+")";note.accept(status);return;}
  refreshGeometry();homeReady(e);status="Home photo attachment acknowledged; display="+e.target.display+" window="+e.target.window+"; compositor visibility requires trace";note.accept(status);
 }
 void requestRefresh(){
  if(closed||!querying.compareAndSet(false,true))return;
  try{worker.execute(()->{try{
   if(!context.getSystemService(PowerManager.class).isInteractive())return;
   ResolveInfo home=context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
   if(home==null||home.activityInfo==null){main.post(()->status="Home photo: default launcher unavailable");return;}
   String pkg=home.activityInfo.packageName;
   ArrayList<Target> targets=new ArrayList<>(),lockTargets=new ArrayList<>();Set<Integer> activeHome=new HashSet<>(),otherActive=new HashSet<>();boolean locked=keyguardLocked();long sampled=SystemClock.elapsedRealtime(),epoch=bridgeEpoch;
   android.util.SparseArray<List<AccessibilityWindowInfo>> windows=automation.getWindowsOnAllDisplays();
   for(int i=0;i<windows.size();i++)for(AccessibilityWindowInfo w:windows.valueAt(i))try{
    if((w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION&&w.getType()!=AccessibilityWindowInfo.TYPE_SYSTEM)||w.getDisplayId()<0||w.getDisplayId()>1)continue;
    AccessibilityNodeInfo root=w.getRoot(0);if(root==null)continue;
    String windowPackage;try{windowPackage=String.valueOf(root.getPackageName());}finally{root.recycle();}
    boolean matches=w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&pkg.equals(windowPackage);
    boolean lockWindow=locked&&w.getType()==AccessibilityWindowInfo.TYPE_SYSTEM&&"com.android.systemui".equals(windowPackage)&&w.isFocused();
    if(!matches&&w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&(w.isActive()||w.isFocused()))otherActive.add(w.getDisplayId());
    if(!matches&&!lockWindow)continue;
    Rect bounds=new Rect();w.getBoundsInScreen(bounds);
    Display d=context.getSystemService(DisplayManager.class).getDisplay(w.getDisplayId());if(d==null||!d.isValid())continue;
    Point size=new Point();d.getRealSize(size);
    if(!HomePhotoPolicy.accepts(bounds.width(),bounds.height(),size.x,size.y))continue;
    if(matches){targets.add(new Target(w.getId(),w.getDisplayId(),pkg));if(w.isActive()||w.isFocused())activeHome.add(w.getDisplayId());}
    else lockTargets.add(new Target(w.getId(),w.getDisplayId(),windowPackage));
   }finally{w.recycle();}
   main.post(()->{if(closed)return;
    if(epoch==bridgeEpoch&&!locked&&!keyguardLocked()&&context.getSystemService(PowerManager.class).isInteractive()){
     homeSeen.clear();for(int id:activeHome)homeSeen.put(id,sampled);
     for(int id:new ArrayList<>(bridgeDisplays))if(otherActive.contains(id)&&!activeHome.contains(id))finishBridge(id,"Another app active");
    }
    if(epoch==bridgeEpoch)for(Target t:lockTargets)prepareBridge(t);
    for(Target t:targets)ensure(t);
   });
  }catch(Throwable e){main.post(()->{if(!closed)status="Home photo lookup waiting: "+e.getClass().getSimpleName();});}finally{querying.set(false);}});}catch(RejectedExecutionException ignored){querying.set(false);}
 }
 void ensure(Target target){
  if(closed||SystemClock.elapsedRealtime()<retryAt)return;
  Entry old=entries.get(target.display);
  if(old!=null&&old.target.window==target.window){refreshGeometry();return;}
  if(pending.containsKey(target.display))return;
  if(attempts.getOrDefault(target.window,0)>=3)return;
  Entry e=null;
  try{
   SurfaceControl anchor=new SurfaceControl.Builder().setName("Duo Home wallpaper anchor").setBufferSize(1,1).setHidden(true).build();
   e=new Entry(target,anchor);pending.put(target.display,e);
   SurfaceControl parent=photoParent.apply(target.display);if(parent==null||!parent.isValid())throw new IllegalStateException("No persistent photo parent for display "+target.display);
   e.photo=new SurfaceControl.Builder().setName("Duo Home transition photo").setParent(parent).setBufferSize(hardware.getWidth(),hardware.getHeight()).setOpaque(true).setHidden(true).build();
   try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){tx.setLayer(anchor,-1).setVisibility(anchor,true).apply();}
   Entry next=e;
   if(attempts.size()>16)attempts.clear();
   attempts.put(target.window,attempts.getOrDefault(target.window,0)+1);
   status="Home photo attaching: window="+target.window+" display="+target.display;
   main.postDelayed(()->complete(next,next.request.timeout(SystemClock.elapsedRealtime()),-1),5000);
   String sent=attachment.request(target.window,anchor,main,result->complete(next,next.request.reply(SystemClock.elapsedRealtime(),result),result));
   note.accept("Home photo direct attachment sent: "+target.pkg+" "+sent+" display="+target.display);
  }catch(Throwable error){if(e!=null){pending.remove(target.display);e.close();}retryAt=SystemClock.elapsedRealtime()+5000;status="Home photo unavailable; existing wallpaper retained: "+failure(error);note.accept(status);}
 }
 void complete(Entry e,AttachmentRequest.Result outcome,int result){
  if(outcome==AttachmentRequest.Result.IGNORED)return;
  if(closed||pending.get(e.target.display)!=e){e.close();return;}
  pending.remove(e.target.display);
  if(outcome!=AttachmentRequest.Result.SUCCESS){e.close();retryAt=SystemClock.elapsedRealtime()+5000;status="Home photo "+(outcome==AttachmentRequest.Result.TIMED_OUT?"attachment timed out after 5 seconds":"attach rejected ("+result+")")+"; attempt "+attempts.getOrDefault(e.target.window,0)+"/3; existing wallpaper retained";note.accept(status);return;}
  Entry previous=entries.get(e.target.display);
  try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
   tx.setLayer(e.anchor,-1).setVisibility(e.anchor,true);
   // Relative Z inherits hidden state, but not the launcher's alpha or transform.
   relative.invoke(tx,e.photo,e.anchor,1);
   tx.setBuffer(e.photo,buffer).setAlpha(e.photo,1f);geometry(tx,e);tx.setVisibility(e.photo,true);
   if(previous!=null)previous.detach(tx);
   tx.apply();entries.put(e.target.display,e);if(previous!=null)previous.release();
   status="Home photo attachment acknowledged; persistent photo parent on display "+e.target.display;
   note.accept(status+"; window="+e.target.window);homeReady(e);
  }catch(Throwable error){e.close();retryAt=SystemClock.elapsedRealtime()+5000;status="Home photo placement failed; existing wallpaper retained: "+failure(error);note.accept(status);}
 }
 static String failure(Throwable error){while(error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null)error=error.getCause();return error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());}
 void geometry(SurfaceControl.Transaction tx,Entry e)throws Exception{
  SurfaceControl parent=photoParent.apply(e.target.display);if(parent==null||!parent.isValid())throw new IllegalStateException("Persistent photo parent unavailable");
  tx.reparent(e.photo,parent);
  Display d=context.getSystemService(DisplayManager.class).getDisplay(e.target.display);
  if(d==null||!d.isValid())throw new IllegalStateException("Display unavailable");
  Point size=new Point();d.getRealSize(size);if(size.x<=0||size.y<=0)throw new IllegalStateException("Empty display");
  int stack=(Integer)Display.class.getMethod("getLayerStack").invoke(d);layerStack.invoke(tx,e.photo,stack);
  Rect src=new Rect(0,0,hardware.getWidth(),hardware.getHeight());float scale=Math.max((float)size.x/src.width(),(float)size.y/src.height());
  int w=Math.min(src.width(),Math.max(1,Math.round(size.x/scale))),h=Math.min(src.height(),Math.max(1,Math.round(size.y/scale)));
  src.set((hardware.getWidth()-w)/2,(hardware.getHeight()-h)/2,(hardware.getWidth()+w)/2,(hardware.getHeight()+h)/2);
  tx.setGeometry(e.photo,src,new Rect(0,0,size.x,size.y),Surface.ROTATION_0);
 }
 void refreshGeometry(){if(closed)return;try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){for(Entry e:entries.values())geometry(tx,e);for(Entry e:bridges.values())if(e.photo!=null)geometry(tx,e);tx.apply();}catch(Exception e){status="Home photo geometry waiting: "+e.getClass().getSimpleName();}}
 void setPhoto(Bitmap photo){
  Bitmap next=photo.copy(Bitmap.Config.HARDWARE,false);if(next==null)throw new IllegalStateException("Cannot prepare Home photo");
  HardwareBuffer old=buffer;hardware=next;buffer=next.getHardwareBuffer();
  try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){for(Entry e:entries.values()){tx.setBuffer(e.photo,buffer);geometry(tx,e);}for(Entry e:bridges.values())if(e.photo!=null){tx.setBuffer(e.photo,buffer);geometry(tx,e);}tx.apply();}catch(Exception e){status="Home photo update failed: "+e.getClass().getSimpleName();}finally{if(old!=null)old.close();}
 }
 void close(){if(closed)return;closed=true;resetBridges();main.removeCallbacks(bridgeTick);worker.shutdown();for(Entry e:pending.values())e.close();pending.clear();for(Entry e:entries.values())e.close();entries.clear();if(buffer!=null){buffer.close();buffer=null;}hardware=null;}
 static final class Target{final int window,display;final String pkg;Target(int w,int d,String p){window=w;display=d;pkg=p;}}
 static final class Entry{final Target target;final SurfaceControl anchor;final AttachmentRequest request=new AttachmentRequest(SystemClock.elapsedRealtime());SurfaceControl photo;AttachmentRequest rebind;long nextRebind,homeReadyEpoch=-1;boolean bridgeReady,bridgeShown,released;Entry(Target t,SurfaceControl a){target=t;anchor=a;}void detach(SurfaceControl.Transaction tx){if(photo!=null)tx.setVisibility(photo,false).reparent(photo,null);tx.setVisibility(anchor,false).reparent(anchor,null);}void release(){if(released)return;released=true;if(photo!=null)photo.release();anchor.release();}void close(){request.cancel();if(rebind!=null)rebind.cancel();if(released)return;try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){detach(tx);tx.apply();}catch(RuntimeException ignored){}finally{release();}}}
}
