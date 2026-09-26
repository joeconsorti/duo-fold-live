package org.duofold.live.wallpaperlayer;
import android.window.*;import android.view.*;import android.graphics.*;import android.hardware.HardwareBuffer;import android.os.*;import java.util.*;import java.util.function.Consumer;
/** Own child surfaces only; never transforms, hides or reparents Samsung's parent surfaces. */
public final class NativePhotoLayer extends DisplayAreaOrganizer {
 final HashMap<IBinder,Entry> layers=new HashMap<>();final Consumer<String> note;final Consumer<Throwable> failed;
 final android.content.Context context;final boolean independent;int independentCount;
 boolean registered,closed;Bitmap hardware;HardwareBuffer buffer;
 public NativePhotoLayer(android.content.Context context,Handler main,Consumer<String> note,Consumer<Throwable> failed){super(r->main.post(r));this.context=context;this.independent=!new java.io.File(context.getFilesDir(),"disable-wake-layer").exists();this.note=note;this.failed=failed;}
 public void start(Bitmap photo)throws Exception{
  setPhoto(photo);registered=true;
  List<DisplayAreaAppearedInfo> infos=registerOrganizer(2);
  for(DisplayAreaAppearedInfo item:infos)attach(item.getDisplayAreaInfo(),item.getLeash());
  if(layers.isEmpty())throw new IllegalStateException("Organizer returned no verified below-app photo parent");
  note.accept("Native compositor photo active: "+layers.size()+" display area(s)");
 }
 public void setPhoto(Bitmap photo){
  Bitmap next=photo.copy(Bitmap.Config.HARDWARE,false);if(next==null)throw new IllegalStateException("Hardware photo conversion failed");
  HardwareBuffer nextBuffer=next.getHardwareBuffer();HardwareBuffer old=buffer;
  hardware=next;buffer=nextBuffer;
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   for(Entry e:layers.values()){t.setBuffer(e.child,buffer);if(e.wake!=null)t.setBuffer(e.wake,buffer);geometry(t,e);}t.apply();
  }
  if(old!=null)old.close();
 }
 void attach(DisplayAreaInfo info,SurfaceControl parent)throws Exception{
  if(closed){parent.release();return;}
  String name=parent.toString();
  if(info.featureId!=2||info.displayId<0||info.displayId>1||!DisplayAreaGuard.belowApps(name)){note.accept("Native parent skipped: "+name);parent.release();return;}
  IBinder key=info.token.asBinder();Entry prior=layers.get(key);
  SurfaceControl child=new SurfaceControl.Builder().setName("Duo native wallpaper photo").setParent(parent).setBufferSize(hardware.getWidth(),hardware.getHeight()).setOpaque(true).setHidden(true).build();
  Entry e=new Entry(info,parent,child);
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   t.setBuffer(child,buffer).setLayer(child,Integer.MAX_VALUE).setAlpha(child,1f);geometry(t,e);t.setVisibility(child,true);
   if(prior!=null){t.setVisibility(prior.child,false).reparent(prior.child,null);if(prior.wake!=null)t.setVisibility(prior.wake,false).reparent(prior.wake,null);}
   t.apply();
  }catch(Exception error){e.remove();throw error;}
  layers.put(key,e);
  if(prior!=null){if(prior.wake!=null)independentCount--;prior.releaseHandles();}
  if(independent)attachIndependent(e);
  note.accept("Native photo attached below apps on display "+info.displayId+"; parent="+name);
 }
 void attachIndependent(Entry e){
  SurfaceControl root=null;
  try{
   if(context.checkSelfPermission("android.permission.ACCESS_SURFACE_FLINGER")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("No compositor permission");
   android.hardware.display.DisplayManager dm=(android.hardware.display.DisplayManager)context.getSystemService(android.content.Context.DISPLAY_SERVICE);
   Display display=dm.getDisplay(e.info.displayId);if(display==null)throw new IllegalStateException("Display unavailable");
   int stack=(Integer)Display.class.getMethod("getLayerStack").invoke(display);
   root=new SurfaceControl.Builder().setName("Duo independent wake photo").setBufferSize(hardware.getWidth(),hardware.getHeight()).setOpaque(true).setHidden(true).build();
   e.wake=root;
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,root,stack);
    // Relative Z follows the verified below-app area; there is no parent transform/alpha inheritance.
    SurfaceControl.Transaction.class.getMethod("setRelativeLayer",SurfaceControl.class,SurfaceControl.class,int.class).invoke(t,root,e.parent,1);
    t.setBuffer(root,buffer).setAlpha(root,1f);geometry(t,e);t.setVisibility(root,true);t.apply();
   }
   independentCount++;note.accept("Independent wake surface submitted on display "+e.info.displayId+"; layer stack="+stack+" (visibility needs phone verification)");
  }catch(Throwable error){
   e.wake=null;
   if(root!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(root,false).reparent(root,null).apply();}catch(Exception ignored){}root.release();}
   note.accept("Independent wake surface unavailable; native photo retained: "+error);
  }
 }
 SurfaceControl photoParent(int display){for(Entry e:layers.values())if(e.info.displayId==display&&e.parent.isValid())return e.parent;return null;}
 String rendererDescription(){return "Native compositor surface; independent wake surfaces="+independentCount+(independent?" (submitted, not a visibility guarantee)":" (disabled)");}
 void geometry(SurfaceControl.Transaction t,Entry e){
  try{
   android.hardware.display.DisplayManager dm=context.getSystemService(android.hardware.display.DisplayManager.class);
   Display display=dm.getDisplay(e.info.displayId);
   if(display==null||!display.isValid())throw new IllegalStateException("Display unavailable: "+e.info.displayId);
   Point dimensions=new Point();display.getRealSize(dimensions);
   Rect bounds=new Rect(0,0,dimensions.x,dimensions.y);
   if(e.wake!=null){int stack=(Integer)Display.class.getMethod("getLayerStack").invoke(display);SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,e.wake,stack);}
   if(bounds.width()<=0||bounds.height()<=0)throw new IllegalStateException("Empty display-area bounds");
   String size=bounds.width()+"x"+bounds.height();if(!size.equals(e.lastSize)){e.lastSize=size;note.accept("Native photo geometry display "+e.info.displayId+": "+size);}
   int w=hardware.getWidth(),h=hardware.getHeight();float target=(float)bounds.width()/bounds.height();
   Rect src=new Rect(0,0,w,h);
   if((float)w/h>target){int crop=Math.max(1,Math.round(h*target));src.left=(w-crop)/2;src.right=src.left+crop;}
   else{int crop=Math.max(1,Math.round(w/target));src.top=(h-crop)/2;src.bottom=src.top+crop;}
   t.setGeometry(e.child,src,new Rect(0,0,bounds.width(),bounds.height()),Surface.ROTATION_0);
   if(e.wake!=null)t.setGeometry(e.wake,src,new Rect(bounds),Surface.ROTATION_0);
  }catch(Exception error){throw new IllegalStateException("Native photo geometry",error);}
 }
 public void refreshDisplays(){if(closed)return;try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
  android.hardware.display.DisplayManager dm=context.getSystemService(android.hardware.display.DisplayManager.class);
  for(Entry e:layers.values()){Display d=dm.getDisplay(e.info.displayId);if(d!=null&&d.isValid())geometry(t,e);}t.apply();
 }catch(Throwable error){failed.accept(error);}}
 public void onDisplayAreaAppeared(DisplayAreaInfo info,SurfaceControl leash){try{attach(info,leash);}catch(Throwable e){failed.accept(e);}}
 public void onDisplayAreaInfoChanged(DisplayAreaInfo info){if(closed)return;Entry e=layers.get(info.token.asBinder());if(e==null)return;e.info=info;try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){geometry(t,e);t.apply();}catch(Throwable error){failed.accept(error);}}
 public void onDisplayAreaVanished(DisplayAreaInfo info){Entry e=layers.remove(info.token.asBinder());if(e!=null){if(e.wake!=null)independentCount--;e.remove();}}
 public void close(){if(closed)return;closed=true;for(Entry e:layers.values())try{e.remove();}catch(Exception error){note.accept("Native child cleanup: "+error);}layers.clear();if(registered){registered=false;try{unregisterOrganizer();}catch(Exception e){note.accept("Native organizer cleanup: "+e);}}if(buffer!=null){buffer.close();buffer=null;}hardware=null;}
 static class Entry{String lastConfiguration="",lastSize="";DisplayAreaInfo info;SurfaceControl wake;final SurfaceControl parent,child;Entry(DisplayAreaInfo i,SurfaceControl p,SurfaceControl c){info=i;parent=p;child=c;}void remove(){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(child,false).reparent(child,null);if(wake!=null)t.setVisibility(wake,false).reparent(wake,null);t.apply();}finally{releaseHandles();}}void releaseHandles(){if(wake!=null){wake.release();wake=null;}child.release();parent.release();}}
}
