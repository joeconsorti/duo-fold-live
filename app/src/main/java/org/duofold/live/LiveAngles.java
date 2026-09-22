package org.duofold.live;
import android.app.WallpaperManager;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import java.util.*;
import rikka.shizuku.Shizuku;
/** Session-scoped wallpaper commands; Binder reads run off the UI thread. */
public final class LiveAngles {
 public interface Listener { void angle(float value,long nanos); }
 private static final Set<Listener> listeners=new HashSet<>();
 private static float angle=Float.NaN;
 private static long last=0;
 public static boolean dualActive=false;
 public static boolean coverPreview=false;
 private static volatile long continuityRequest;
 public static String continuityStatus="Continuity test idle",continuityTrace="No continuity samples";
 public static void startContinuityProbe(){continuityRequest=SystemClock.elapsedRealtime();handoffReady();}
 public static void cancelContinuityProbe(){continuityRequest=0;handoffReady();}
 public static String handoffFade="Handoff fade idle";
 public static String bridgeTrace="No bridge";
 public static String expansionStatus="Expansion idle";
 public static String mirrorStatus="Inner live mirror idle";
 public static boolean nativeInner=false,continuityNative=false;
 private static volatile LiveAngles current;
 private static int mirrorSerial;
 public interface MirrorCallback {void ready(boolean ok,String status);}
 public static int attachMirror(SurfaceControl parent,int width,int height,MirrorCallback callback){
  int id=++mirrorSerial;LiveAngles self=current;
  if(self==null||!self.running||self.worker==null){callback.ready(false,"Angle reader unavailable");return id;}
  self.worker.post(()->{
   Parcel p=Parcel.obtain(),r=Parcel.obtain();boolean ok=false;String message;
   try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);p.writeInt(id);p.writeTypedObject(parent,0);p.writeInt(width);p.writeInt(height);
    IBinder binder=self.reader;if(binder==null||!binder.transact(4,p,r,0))throw new IllegalStateException("Mirror reader unavailable");
    r.readException();Bundle b=r.readBundle(LiveAngles.class.getClassLoader());ok=b.getBoolean("ok");message=b.getString("status");
   }catch(Exception e){message="Mirror error: "+e.getClass().getSimpleName()+": "+e.getMessage();}
   finally{p.recycle();r.recycle();}
   boolean success=ok;String status=message;self.main.post(()->callback.ready(success,status));
  });return id;
 }
 public static void detachMirror(int id){LiveAngles self=current;if(id<=0||self==null||self.worker==null)return;
  self.worker.post(()->{Parcel p=Parcel.obtain(),r=Parcel.obtain();try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);p.writeInt(id);if(self.reader!=null){self.reader.transact(5,p,r,0);r.readException();}}catch(Exception ignored){}finally{p.recycle();r.recycle();}});
 }
 public static void previewCommand(int code,Parcel p,Parcel r)throws Exception{
  LiveAngles self=current;IBinder binder=self==null?null:self.reader;
  if(binder==null || !binder.transact(code,p,r,0))throw new IllegalStateException("Preview helper unavailable");
 }
 public static IBinder captureBinder()throws Exception{
  LiveAngles self=current;IBinder remote=self==null?null:self.reader;
  if(remote==null)throw new IllegalStateException("Shizuku reader unavailable");
  Parcel p=Parcel.obtain(),r=Parcel.obtain();try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);remote.transact(6,p,r,0);r.readException();return r.readStrongBinder();}finally{p.recycle();r.recycle();}
 }
 public static String handoffStatus="Normal display control";
 public static String status="Angle reader off";
 public static long ageMs(){return last==0?-1:SystemClock.elapsedRealtime()-last;}
 public static boolean fresh(){return last>0 && SystemClock.elapsedRealtime()-last<750;}
 public static void add(Listener l){listeners.add(l);if(fresh())l.angle(angle,last*1000000L);}
 public static void remove(Listener l){listeners.remove(l);}
 private final Context context; private final Handler main=new Handler(Looper.getMainLooper());
 private HandlerThread thread; private Handler worker; private volatile boolean running; private volatile IBinder reader;
 private WindowManager wm; private View anchor;
 private WindowManager secondaryWm;private View secondaryAnchor;private String anchorKey="",secondaryKey=""; private Shizuku.UserServiceArgs args;
 private volatile long lastPoll;
 private long secondaryRefreshAt,statusFormatAt;
 private boolean pollInFlight,urgentPoll;
 private long pollStarted;
 private static long roundTripMs;
 private static String rateSummary="Collecting polling rates";
 private long rateStarted;private int ratePolls,rateReplies,rateChanges,rateReceived;
 public static String latencyReport(){return "Angle poll target period: 4 ms; last round trip: "+roundTripMs+" ms; "+rateSummary;}
 public static void handoffReady(){LiveAngles self=current;if(self==null)return;self.main.post(()->{
  if(!self.running)return;
  if(self.pollInFlight){self.urgentPoll=true;return;}
  self.main.removeCallbacks(self.poll);self.main.post(self.poll);
 });}

 public boolean stalled(){return running && reader!=null && SystemClock.elapsedRealtime()-lastPoll>6000;}
 private boolean bound; private String action; private int count;
 public LiveAngles(Context c){context=c;}
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName n,IBinder binder){main.post(()->{if(!running)return;reader=binder;lastPoll=SystemClock.elapsedRealtime();RecoveryLog.add("Angle reader connected");worker.post(()->{try{call(1);main.post(poll);}catch(Exception e){fail(e);}});});}
  public void onServiceDisconnected(ComponentName n){main.post(()->{if(running){RecoveryLog.add("Angle reader Binder disconnected");status="Angle reader disconnected — reconnecting automatically";stop();}});}
 };
 public boolean isRunning(){return running;}
 public void start(){
  if(running)return;
  try{
   if(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=0)throw new IllegalStateException("Authorize Shizuku, then reconnect");
   if(!android.provider.Settings.canDrawOverlays(context))throw new IllegalStateException("Allow display over other apps, then reconnect");
   running=true;current=this;last=0;count=0;action="org.duofold.live.wallpaperprobe.READ_"+SystemClock.elapsedRealtime();
   ensureAnchors();
   thread=new HandlerThread("duofold-angle-ipc");thread.start();worker=new Handler(thread.getLooper());
   args=new Shizuku.UserServiceArgs(new ComponentName(context,AngleReader.class)).daemon(false).processNameSuffix("fold_angles").version(BuildConfig.VERSION_CODE);
   bound=true;Shizuku.bindUserService(args,connection);status="Connecting to Shizuku…";
   main.postDelayed(()->{if(running&&reader==null){status="Connection timed out — reconnect in app";stop();}},12000);
  }catch(Exception e){status=e.getMessage();stop();}
 }
 private String key(Display d){return d.getDisplayId()+":"+d.getMode().getPhysicalWidth()+"x"+d.getMode().getPhysicalHeight();}
 private void ensureAnchors(){
  android.hardware.display.DisplayManager dm=context.getSystemService(android.hardware.display.DisplayManager.class);
  Display primary=dm.getDisplay(0);if(primary==null)return;
  String next=key(primary);
  if(anchor==null||!next.equals(anchorKey)){
   if(anchor!=null)try{wm.removeViewImmediate(anchor);}catch(Exception ignored){}
   Context c=context.createDisplayContext(primary).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
   wm=c.getSystemService(WindowManager.class);anchor=new View(c);wm.addView(anchor,anchorParams());anchorKey=next;
   RecoveryLog.add("Angle anchor attached on primary "+next);
  }
  Display second=dualActive?dm.getDisplay(1):null;
  String nextSecond=second==null?"":key(second);
  if(secondaryAnchor!=null&&!nextSecond.equals(secondaryKey)){try{secondaryWm.removeViewImmediate(secondaryAnchor);}catch(Exception ignored){}secondaryAnchor=null;}
  if(second!=null&&secondaryAnchor==null){
   Context c=context.createDisplayContext(second).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
   secondaryWm=c.getSystemService(WindowManager.class);View v=new View(c);
   try{secondaryWm.addView(v,anchorParams());secondaryAnchor=v;secondaryKey=nextSecond;}catch(Exception e){RecoveryLog.add("Secondary angle anchor unavailable: "+e.getClass().getSimpleName());}
  }
 }
 private WindowManager.LayoutParams anchorParams(){
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.LEFT;p.setTitle("Duo angle source");return p;
 }
 private void sendAngleCommand(View v){if(v!=null&&v.getWindowToken()!=null)WallpaperManager.getInstance(v.getContext()).sendWallpaperCommand(v.getWindowToken(),action,0,0,0,null);}
 private Bundle call(int code)throws Exception{
  IBinder b=reader;if(b==null)throw new IllegalStateException("Reader disconnected");
  Parcel p=Parcel.obtain(),r=Parcel.obtain();try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);if(code==1)p.writeString(action);if(code==2)p.writeInt(StandaloneService.Companion.getInstance()!=null && context.getSharedPreferences("standalone",0).getBoolean("enabled",false) && context.getSystemService(PowerManager.class).isInteractive() && !context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()?1:0);
   if(code==2){
    p.writeInt(context.getSharedPreferences("standalone",0).getBoolean("dual",false)?1:0);
    android.view.Display display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
    android.view.Display.Mode mode=display.getMode();p.writeInt(Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight())/(float)Math.max(mode.getPhysicalWidth(),mode.getPhysicalHeight())>.7f?1:0);
    StandaloneService host=StandaloneService.Companion.getInstance();p.writeInt(host!=null&&host.secondaryReady()?1:0);p.writeInt(HandoffFrames.readySource());p.writeFloat(context.getSharedPreferences("standalone",0).getFloat("open_threshold",172f));p.writeInt(context.getSharedPreferences("standalone",0).getBoolean("cover_preview",true)?1:0);p.writeInt(context.getSharedPreferences("standalone",0).getBoolean("enabled",false)?1:0);p.writeFloat(context.getSharedPreferences("standalone",0).getFloat("closed_threshold",1f));p.writeLong(continuityRequest);p.writeFloat(context.getSharedPreferences("standalone",0).getFloat("fade_smoothing_ms",FadeSettings.DEFAULT_SMOOTHING));p.writeFloat(context.getSharedPreferences("standalone",0).getFloat("fade_gradualness",FadeSettings.DEFAULT_GRADUALNESS));
   }
   if(!b.transact(code,p,r,0))throw new IllegalStateException("Unsupported reader");r.readException();return code==2?r.readBundle(getClass().getClassLoader()):null;
  }finally{p.recycle();r.recycle();}
 }
 private final Runnable poll=new Runnable(){public void run(){
  if(!running||pollInFlight)return;
  pollInFlight=true;pollStarted=SystemClock.elapsedRealtime();
  boolean on=context.getSystemService(PowerManager.class).isInteractive();
  try{if(on){ensureAnchors();sendAngleCommand(anchor);sendAngleCommand(secondaryAnchor);}}
  catch(Exception e){fail(e);return;}
  worker.post(()->{try{Bundle b=call(2);
   if(ReaderRecovery.needsRestart(b.getString("state"))){call(1);main.post(()->{if(running){last=0;count=0;lastPoll=SystemClock.elapsedRealtime();pollInFlight=false;urgentPoll=false;RecoveryLog.add("Restarting expired wallpaper reader lease");status="Restarting wallpaper log reader";main.postDelayed(poll,50);}});return;}
   main.post(()->{
   if(!running)return;
   String nextHandoff=b.getString("handoff", "Unknown display status");
   if(!nextHandoff.equals(handoffStatus))RecoveryLog.add(nextHandoff);
   handoffStatus=nextHandoff;continuityStatus=b.getString("continuityProbe","Continuity status unavailable");continuityTrace=b.getString("continuityTrace","");
   handoffFade=b.getString("handoffFade","Handoff fade idle");dualActive=b.getBoolean("dualActive");coverPreview=b.getBoolean("coverPreview");expansionStatus=b.getString("expansion","Expansion idle");bridgeTrace=b.getString("bridgeTrace","No bridge");
   mirrorStatus=b.getString("mirror","Inner mirror status unavailable");nativeInner=b.getBoolean("nativeInner");continuityNative=b.getBoolean("continuityNative");
   StandaloneService host=StandaloneService.Companion.getInstance();if(host!=null&&SystemClock.elapsedRealtime()>=secondaryRefreshAt){secondaryRefreshAt=SystemClock.elapsedRealtime()+8;host.refreshSecondary();}
   lastPoll=SystemClock.elapsedRealtime();
   long stamp=b.getLong("last");int received=b.getInt("count");
   ratePolls++;
   if(received>rateReceived){rateReplies+=received-rateReceived;if(Float.compare(angle,b.getFloat("angle"))!=0)rateChanges++;}
   rateReceived=received;
   long rateNow=SystemClock.elapsedRealtime();
   if(rateStarted==0){rateStarted=rateNow;ratePolls=rateReplies=rateChanges=0;}
   if(rateNow-rateStarted>=1000){
    double seconds=(rateNow-rateStarted)/1000.0;
    rateSummary=String.format(Locale.US,"%.1f polls/s; %.1f replies/s; %.1f observed angle changes/s",ratePolls/seconds,rateReplies/seconds,rateChanges/seconds);
    rateStarted=rateNow;ratePolls=rateReplies=rateChanges=0;
   }
   if(received>count && stamp>0 && SystemClock.elapsedRealtime()-stamp<750){
    count=received;angle=b.getFloat("angle");last=stamp;
    for(Listener l:new ArrayList<>(listeners))l.angle(angle,stamp*1000000L);
   }
   android.view.Display currentDisplay=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
   android.view.Display.Mode currentMode=currentDisplay.getMode();
   boolean innerNow=Math.min(currentMode.getPhysicalWidth(),currentMode.getPhysicalHeight())/(float)Math.max(currentMode.getPhysicalWidth(),currentMode.getPhysicalHeight())>.7f;
   HandoffFrames.update(innerNow,dualActive,angle,fresh(),context.getSharedPreferences("standalone",0).getFloat("open_threshold",172f),context.getSharedPreferences("standalone",0).getBoolean("enabled",false) && context.getSharedPreferences("standalone",0).getBoolean("dual",false) && context.getSystemService(PowerManager.class).isInteractive() && !context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked());
   if(SystemClock.elapsedRealtime()>=statusFormatAt){statusFormatAt=SystemClock.elapsedRealtime()+250;status=(fresh()?String.format(Locale.US,"LIVE %.0f° (closed ≤%.0f°)",b.getFloat("rawAngle"),context.getSharedPreferences("standalone",0).getFloat("closed_threshold",1f)):"Waiting for fresh wallpaper angles")+" · "+received+" replies · "+b.getInt("unique")+" distinct · UID "+b.getInt("uid");}
   roundTripMs=SystemClock.elapsedRealtime()-pollStarted;
   pollInFlight=false;
   long delay=PollCadence.delay(on,roundTripMs,urgentPoll);urgentPoll=false;
   main.postDelayed(poll,delay);
  });}catch(Exception e){fail(e);}});
 }};
 private void fail(Exception e){main.post(()->{if(running){RecoveryLog.add("Reader error: "+e.getClass().getSimpleName());status="Reader error: "+e.getMessage();stop();}});}
 public void stop(){continuityRequest=0;HandoffFrames.clear();if(current==this)current=null;nativeInner=false;continuityNative=false;coverPreview=false;running=false;pollInFlight=false;urgentPoll=false;dualActive=false;last=0;if(status.startsWith("LIVE")||status.startsWith("Waiting")||status.startsWith("Connecting"))status="Angle reader stopped";main.removeCallbacksAndMessages(null);
  if(bound){try{Shizuku.unbindUserService(args,connection,true);}catch(Exception ignored){}bound=false;}
  reader=null;if(thread!=null){thread.quitSafely();thread=null;}
  if(secondaryAnchor!=null){try{secondaryWm.removeViewImmediate(secondaryAnchor);}catch(Exception ignored){}secondaryAnchor=null;}
  if(anchor!=null){try{wm.removeViewImmediate(anchor);}catch(Exception ignored){}anchor=null;}
 }
}
