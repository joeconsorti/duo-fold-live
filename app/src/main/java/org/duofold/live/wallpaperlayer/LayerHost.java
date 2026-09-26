package org.duofold.live.wallpaperlayer;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.graphics.*;import android.hardware.display.*;import android.os.*;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.regex.*;
public class LayerHost extends Binder {
 public static final String TOKEN="org.duofold.live.wallpaperlayer.Host";
 final Handler main=new Handler(Looper.getMainLooper()); final HashMap<Integer,Panel> panels=new HashMap<>();
 volatile String state="Idle",details="",operation="Idle",failureTrace=""; volatile long until,last; volatile int responses,requests; volatile float angle=Float.NaN; volatile boolean running;
 final Set<Float> angles=Collections.synchronizedSet(new HashSet<>()); final String action="org.duofold.live.wallpaperlayer.READ_"+SystemClock.elapsedRealtime();
 final ProbeJournal journal=new ProbeJournal();
 NativePhotoLayer nativePhoto; String nativeStatus="Legacy window renderer"; boolean useNative;
 DisplayManager displayManager; boolean listening; int windowAdds,retainedChanges;
 final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
  public void onDisplayAdded(int id){displayChanged();}
  public void onDisplayChanged(int id){displayChanged();}
  public void onDisplayRemoved(int id){displayChanged();}
 };
 void displayChanged(){if(!running)return;try{refresh();}catch(Throwable e){recordFailure(e);stop("Display update failed: "+reason(e));}}
 java.util.function.Consumer<String> transitionSink;
 void trace(String event){journal.phase(event);if(transitionSink!=null)transitionSink.accept(event);}
 int owner=-1,type; Context context; Bitmap bitmap; java.lang.Process log; Object wallpaper; Method command;
 public LayerHost(){attachInterface(null,TOKEN);}
 public LayerHost(Context registeredContext){this();context=registeredContext;}
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==INTERFACE_TRANSACTION){reply.writeString(TOKEN);return true;}
  if(code==16777115){main.post(()->{stop("Host ended");System.exit(0);});return true;}
  data.enforceInterface(TOKEN);synchronized(this){int uid=Binder.getCallingUid();if(owner<0)owner=uid;if(owner!=uid)throw new SecurityException("Wrong caller");}
  if(code==1){final ParcelFileDescriptor fd=data.readTypedObject(ParcelFileDescriptor.CREATOR);if(fd==null)throw new IllegalArgumentException("Photo missing");main.post(()->start(fd));reply.writeNoException();reply.writeString("Starting photo layer. Press Home after the status updates.");return true;}
  if(code==2){reply.writeNoException();reply.writeString(report());return true;}
  if(code==3){main.post(()->stop("Stopped; original wallpaper unchanged"));reply.writeNoException();reply.writeString("Removing layer…");return true;}
  return super.onTransact(code,data,reply,flags);
 }
 void start(ParcelFileDescriptor fd){stop("Previous layer cleaned up");journal.begin();failureTrace="";operation="Startup";try{
  journal.phase("Check registered app context");
  if(context==null)throw new IllegalStateException("Registered instrumentation context required");
  operation="Registered window host";
  journal.phase("Check INTERNAL_SYSTEM_WINDOW permission");
  if(context.checkSelfPermission("android.permission.INTERNAL_SYSTEM_WINDOW")!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Shell lacks INTERNAL_SYSTEM_WINDOW");
  journal.phase("Resolve Samsung window type");
  type=WindowManager.LayoutParams.class.getField("TYPE_COVER_SCREEN_BASE").getInt(null);
  if(type!=2620)throw new IllegalStateException("Samsung wallpaper-depth type differs: "+type);
  journal.phase("Decode selected photo");
  bitmap=BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());if(bitmap==null)throw new IOException("Cannot decode photo");
  journal.phase("Resolve wallpaper command API");
  command=null;try{IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"wallpaper");Class<?> api=Class.forName("android.app.IWallpaperManager");wallpaper=Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);command=api.getMethod("semSendWallpaperCommand",int.class,String.class,Bundle.class);}catch(Exception e){journal.phase("Wallpaper command API unavailable: "+reason(e));}
  responses=0;requests=0;last=0;angle=Float.NaN;angles.clear();until=0;running=true;details="";state="Custom wallpaper running — no time limit";journal.phase("Start retained wallpaper windows");windowAdds=0;retainedChanges=0;
  displayManager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
  displayManager.registerDisplayListener(displayListener,main);listening=true;
  refresh();main.post(tick);
 }catch(Throwable e){recordFailure(e);stop("Unavailable: "+reason(e));}finally{try{fd.close();}catch(IOException ignored){}}}
 void activateNative(){
  NativePhotoLayer candidate=null;
  try{candidate=new NativePhotoLayer(context,main,s->journal.phase(s),e->fallbackNative(e));candidate.start(bitmap);nativePhoto=candidate;nativeStatus="Native compositor + retained wallpaper windows (below apps)";trace("Native photo active; retaining wallpaper windows through sleep/unlock");}
  catch(Throwable e){if(candidate!=null)candidate.close();recordFailure(e);nativeStatus="Legacy fallback: "+reason(e);journal.phase(nativeStatus);}
 }
 void fallbackNative(Throwable error){recordFailure(error);if(nativePhoto!=null){nativePhoto.close();nativePhoto=null;}nativeStatus="Legacy fallback: "+reason(error);journal.phase(nativeStatus);displayChanged();}
 /** Keep the existing buffers/windows; never wait for unlock to create the photo. */
 void screenEvent(String action){
  if(!running)return;
  KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
  trace("Screen event "+action+"; keyguardLocked="+keyguard.isKeyguardLocked()+"; photo windows="+panels.size());
  if(!Intent.ACTION_SCREEN_ON.equals(action)&&!Intent.ACTION_USER_PRESENT.equals(action))return;
  displayChanged();
  for(Panel panel:panels.values()){
   panel.view.setTag(action+" at "+SystemClock.elapsedRealtime());
   panel.view.requestLayout();panel.view.postInvalidateOnAnimation();
  }
 }
 void updatePhoto(Bitmap photo){bitmap=photo;if(nativePhoto!=null){try{nativePhoto.setPhoto(photo);}catch(Throwable e){fallbackNative(e);}}for(Panel panel:panels.values())panel.view.invalidate();}
 void recordFailure(Throwable e){
  journal.fail(operation+": "+reason(e));
  StringWriter w=new StringWriter();e.printStackTrace(new PrintWriter(w));failureTrace=w.toString();
 }
 static String reason(Throwable e){StringBuilder b=new StringBuilder();for(int i=0;e!=null&&i<8;i++,e=e.getCause()){if(i>0)b.append(" <- ");b.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());}return b.toString();}
 final Runnable tick=new Runnable(){int step;public void run(){if(!running)return;try{
  // Keep the wallpaper-depth windows attached through keyguard and screen-off.
  // They do not receive input, dismiss keyguard, or request display power.
  if(step%5==0){refresh();}
  if(command!=null && (nativePhoto!=null||!panels.isEmpty()) && step%2==0){for(int which:new int[]{5,17}){try{command.invoke(wallpaper,which,action,new Bundle());requests++;}catch(Exception e){details="Wallpaper commands: "+reason(e);command=null;break;}}}
  step++;main.postDelayed(this,100);
 }catch(Throwable e){recordFailure(e);stop("Stopped after error: "+reason(e));}}};
 void refresh(){if(nativePhoto!=null)nativePhoto.refreshDisplays();operation="Enumerate displays";DisplayManager dm=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);Set<Integer> keep=new HashSet<>();for(Display d:dm.getDisplays()){
  if(d.getDisplayId()!=0&&d.getDisplayId()!=1)continue;
  if(!d.isValid())continue;
  Display.Mode mode=d.getMode();int lo=Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight()),hi=Math.max(mode.getPhysicalWidth(),mode.getPhysicalHeight());
  if(!org.duofold.live.BuiltInPanel.accepts(d))continue;
  int id=d.getDisplayId();keep.add(id);String key=mode.getPhysicalWidth()+"x"+mode.getPhysicalHeight()+"/"+d.getRotation();Panel old=panels.get(id);if(old!=null){
   if(!old.key.equals(key)){old.key=key;retainedChanges++;old.view.requestLayout();old.view.invalidate();trace("Retained photo window on display "+id+" ("+key+")");}
   continue;
  }
  operation="Create photo window context on display "+id;
  journal.phase("Create photo window context on display "+id+" ("+key+")");
  Context wc=context.createDisplayContext(d).createWindowContext(type,null);WindowManager wm=(WindowManager)wc.getSystemService(Context.WINDOW_SERVICE);
  View view=new View(wc){boolean traceNextDraw=true;
   protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);traceNextDraw=true;trace("Display "+id+" window visibility="+visibility);}
   protected void onAttachedToWindow(){super.onAttachedToWindow();trace("Display "+id+" view attached");}
   protected void onDetachedFromWindow(){trace("Display "+id+" view detached");super.onDetachedFromWindow();}
   final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);traceNextDraw=true;trace("Display "+id+" resize "+oldw+"x"+oldh+" -> "+w+"x"+h);invalidate();}
   protected void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);requestLayout();invalidate();}
   protected void onDraw(Canvas c){super.onDraw(c);Bitmap b=bitmap;if(b==null||b.isRecycled())return;float scale=Math.max((float)getWidth()/b.getWidth(),(float)getHeight()/b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;c.drawBitmap(b,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),p);if(traceNextDraw||getTag()!=null){String reason=String.valueOf(getTag());setTag(null);traceNextDraw=false;trace("Display "+id+" photo draw callback "+getWidth()+"x"+getHeight()+"; "+reason);if(isHardwareAccelerated())getViewTreeObserver().registerFrameCommitCallback(()->main.post(()->trace("Display "+id+" photo frame committed; "+reason)));}}};
  view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,type,WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.OPAQUE);
  lp.packageName=context.getPackageName();lp.setTitle("Duo persistent wallpaper photo");lp.windowAnimations=0;lp.rotationAnimation=WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;lp.setFitInsetsTypes(0);lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
  operation="Attach photo window on display "+id;
  journal.phase("Attach photo window on display "+id);
  wm.addView(view,lp);windowAdds++;journal.phase("Attached display "+id);panels.put(id,new Panel(wm,view,key));
 }
 for(Integer id:new ArrayList<>(panels.keySet()))if(!keep.contains(id)){panels.remove(id).remove();}
 details="Window type "+type+"; attached panels "+panels.keySet()+". Attachment does not prove correct visual placement.";
 }
 void startLog(){try{final java.lang.Process child=new ProcessBuilder("logcat","-v","epoch","-T","1","-s","SprWallpaper|FoldInteractive:V","*:S").redirectErrorStream(true).start();log=child;
  new Thread(()->{Pattern p=Pattern.compile("mCurrentAngle=([0-9]+(?:\\.[0-9]+)?)");try(BufferedReader r=new BufferedReader(new InputStreamReader(child.getInputStream()))){String line;while((line=r.readLine())!=null){if(log!=child)return;Matcher m=p.matcher(line);if(!m.find())continue;try{long event=(long)(Double.parseDouble(line.trim().split("\\s+",2)[0])*1000);long age=System.currentTimeMillis()-event;if(age< -100||age>1500)continue;float a=Float.parseFloat(m.group(1));if(a<0||a>180)continue;angle=a;last=SystemClock.elapsedRealtime()-Math.max(age,0);responses++;angles.add(a);}catch(Exception ignored){}}}catch(IOException ignored){}} ,"Samsung angle observation").start();
 }catch(IOException e){details="Angle observation unavailable: "+e;}}
 void removePanels(){for(Panel p:panels.values())p.remove();panels.clear();}
 void stop(String message){if(nativePhoto!=null){nativePhoto.close();nativePhoto=null;}journal.stopped(message);running=false;main.removeCallbacks(tick);if(listening&&displayManager!=null){displayManager.unregisterDisplayListener(displayListener);listening=false;}removePanels();java.lang.Process old=log;log=null;if(old!=null)old.destroy();bitmap=null;state=message;until=0;}
 String report(){long now=SystemClock.elapsedRealtime();return "Duo Wallpaper Layer 0.13\n"+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n"+state+"\n"+journal.report()+"\nRenderer: "+(nativePhoto!=null?nativePhoto.rendererDescription():nativeStatus)+"\nCurrent operation: "+operation+"\n"+failureTrace+"\n"+(nativePhoto!=null?"Native surfaces active; retained wallpaper windows="+panels.keySet():details)+"\nDuration: "+(running?"Until disabled or host ends":"Stopped")+"\nWindow attaches: "+windowAdds+"; retained configuration changes: "+retainedChanges+"\nRegistered window host UID: "+android.os.Process.myUid()+"\nWallpaper commands attempted: "+requests+"\nAngle observation is supplied separately by the Shizuku launcher.\nRenderer leaves home wallpaper bindings and physical display states unchanged. Separate lock-photo action is reported by the app.";}

 static class Panel{final WindowManager wm;final View view;String key;Panel(WindowManager w,View v,String k){wm=w;view=v;key=k;}void remove(){try{wm.removeViewImmediate(view);}catch(Exception ignored){}}}
}
