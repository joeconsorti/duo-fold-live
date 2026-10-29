package org.duofold.live.wallpaperlayer;
import android.accessibilityservice.AccessibilityService;
import android.app.UiAutomation;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Immutable references for one Home capture; never changes a layer.
 * Wallpaper UiAutomation resolves the window. The enabled Duo accessibility
 * service performs the public window screenshot operation. */
public final class HomeCaptureSource {
 public final Bitmap wallpaper;public final Rect crop;public final int width,height,rotation;
 public final String physical;private final UiAutomation automation;private final String launcher;
 private HomeCaptureSource(Bitmap bitmap,Rect crop,Display d,Point size,UiAutomation a,String pkg)throws Exception{
  wallpaper=bitmap;this.crop=crop;width=size.x;height=size.y;rotation=d.getRotation();automation=a;launcher=pkg;
  physical=String.valueOf(Display.class.getMethod("getUniqueId").invoke(d));
 }
 public static HomeCaptureSource current()throws Exception{
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Main thread required");
  LayerHost host=WallpaperProbeSurfaces.host;
  if(host==null||!host.running||host.homePhoto==null||host.homePhoto.closed)throw new IllegalStateException("Home wallpaper connection unavailable");
  Display d=host.context.getSystemService(DisplayManager.class).getDisplay(0);
  if(d==null||d.getState()!=Display.STATE_ON)throw new IllegalStateException("Home primary OFF");
  Point size=new Point();d.getRealSize(size);
  PanelPhotos.Photo selected=host.album==null?null:host.album.forDisplay(d,size.x,size.y);
  Bitmap photo=selected==null?host.bitmap:selected.bitmap;
  if(photo==null||photo.isRecycled())throw new IllegalStateException("Home wallpaper unavailable");
  int[] fallback=WallpaperCrop.rect(photo.getWidth(),photo.getHeight(),size.x,size.y,1,.5f,.5f);
  Rect crop=selected==null?new Rect(fallback[0],fallback[1],fallback[2],fallback[3]):selected.crop(size.x,size.y);
  android.content.pm.ResolveInfo resolved=host.context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
  if(resolved==null||resolved.activityInfo==null)throw new IllegalStateException("Home package unavailable");
  return new HomeCaptureSource(photo,crop,d,size,host.homePhoto.automation,resolved.activityInfo.packageName);
 }
 public static final class Window {public final int id;public final Rect bounds;Window(int id,Rect b){this.id=id;bounds=b;}}
 /** All selected application windows, bottom to top, resolved by task ID rather
  * than focus or package (two panes can belong to the same package). */
 public static final class Pane {
  public final int task,layer;public final Window window;
  Pane(int task,int layer,Window window){this.task=task;this.layer=layer;this.window=window;}
  public String toString(){return task+":"+window.id+":"+layer+":"+window.bounds;}
 }
 public List<Pane> workspace(int[] tasks)throws Exception{
  Set<Integer> expected=new HashSet<>();for(int task:tasks)if(task<0||!expected.add(task))throw new IllegalStateException("Invalid workspace tasks");
  if(expected.isEmpty()||expected.size()>8)throw new IllegalStateException("Invalid workspace size");
  Set<Integer> found=new HashSet<>();List<Pane> panes=new ArrayList<>();
  java.lang.reflect.Method taskId=AccessibilityWindowInfo.class.getMethod("getTaskId");
  android.util.SparseArray<List<AccessibilityWindowInfo>> all=automation.getWindowsOnAllDisplays();
  try{for(int i=0;i<all.size();i++)for(AccessibilityWindowInfo w:all.valueAt(i)){
   if(w.getDisplayId()!=0||w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
   int task=(int)taskId.invoke(w);if(!expected.contains(task))continue;
   Rect bounds=new Rect();w.getBoundsInScreen(bounds);
   if(!org.duofold.live.WorkspaceBufferGeometry.exact(bounds.width(),bounds.height(),bounds.left,bounds.top,bounds.right,bounds.bottom,width,height))throw new IllegalStateException("Workspace window bounds pending");
   // A popup/subwindow may already be included in its parent's screenshot.
   // Do not blend that subtree twice or guess which window is the app surface.
   if(!found.add(task))throw new IllegalStateException("Ambiguous workspace window for task="+task);
   panes.add(new Pane(task,w.getLayer(),new Window(w.getId(),bounds)));
  }}finally{for(int i=0;i<all.size();i++)for(AccessibilityWindowInfo w:all.valueAt(i))w.recycle();}
  if(!found.equals(expected)||panes.size()>12)throw new IllegalStateException("Selected workspace windows unavailable");
  panes.sort(Comparator.comparingInt((Pane p)->p.layer).thenComparingInt(p->p.window.id));
  return panes;
 }
 public Window window()throws Exception{
  Window selected=null;
  android.util.SparseArray<List<AccessibilityWindowInfo>> all=automation.getWindowsOnAllDisplays();
  try{for(int i=0;i<all.size();i++)for(AccessibilityWindowInfo w:all.valueAt(i)){
   if(w.getDisplayId()!=0||w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||!w.isFocused())continue;
   AccessibilityNodeInfo root=w.getRoot(0);if(root==null)throw new IllegalStateException("Home window root unavailable");
   String pkg;try{pkg=String.valueOf(root.getPackageName());}finally{root.recycle();}
   if(!launcher.equals(pkg))throw new IllegalStateException("Focused window is not Home");
   Rect bounds=new Rect();w.getBoundsInScreen(bounds);
   if(bounds.isEmpty())throw new IllegalStateException("Home window geometry pending");
   if(selected!=null)throw new IllegalStateException("Ambiguous Home window");selected=new Window(w.getId(),bounds);
  }}finally{for(int i=0;i<all.size();i++)for(AccessibilityWindowInfo w:all.valueAt(i))w.recycle();}
  if(selected==null)throw new IllegalStateException("Focused Home window unavailable");return selected;
 }
 public void capture(Window window,Executor executor,AccessibilityService.TakeScreenshotCallback callback)throws Exception{
  if(android.os.Build.VERSION.SDK_INT<34)throw new UnsupportedOperationException("Isolated Home window capture requires Android 14+");
  org.duofold.live.StandaloneService service=org.duofold.live.StandaloneService.Companion.getInstance();
  if(service==null||service.getServiceInfo()==null||(service.getServiceInfo().getCapabilities()&android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT)==0)
   throw new IllegalStateException("Home window screenshot capability unavailable; reconnect Duo accessibility service");
  service.takeScreenshotOfWindow(window.id,executor,callback);
 }
}
