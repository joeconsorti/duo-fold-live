package org.duofold.live;
import android.app.*;
import android.content.*;
import android.os.*;
import rikka.shizuku.Shizuku;
/** User-enabled lifetime independent of the settings Activity. */
public class FoldBackgroundService extends Service {
 public static boolean running=false;
 private final Handler main=new Handler(Looper.getMainLooper());
 private boolean wanted(){FoldAwakeDefault.persist(this);return true;}
 private LiveAngles angles;
 private long asleepSince=-1,nextReaderAttempt;
 private String lastNotice="";
 private boolean lastReachable=false;
 private final rikka.shizuku.Shizuku.OnBinderReceivedListener binderReady=()->main.post(()->{RecoveryLog.add("Shizuku Binder received");FoldAwakeDefault.reconnect();kick();});
 private final rikka.shizuku.Shizuku.OnBinderDeadListener binderDead=()->main.post(()->{RecoveryLog.add("Shizuku Binder death notification");clearEffect();FoldAwakeDefault.reconnect();kick();});
 private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){RecoveryLog.add("Background received "+i.getAction());if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))clearEffect();else nextReaderAttempt=0;FoldAwakeDefault.reconnect();kick();}};
 private final android.database.ContentObserver foldSettingObserver=new android.database.ContentObserver(main){
  @Override public void onChange(boolean selfChange){FoldAwakeDefault.reconnect();kick();}
 };
 private final Shizuku.OnRequestPermissionResultListener permissionResult=(requestCode,grantResult)->main.post(()->{FoldAwakeDefault.reconnect();kick();});
 private void clearEffect(){StandaloneService service=StandaloneService.Companion.getInstance();if(service!=null)service.clearUnavailableEffect();}
 private void kick(){if(!running)return;main.removeCallbacks(supervise);main.post(supervise);}
 public static String connectionReport(){
  try{return "Shizuku binder: "+(Shizuku.getBinder()==null?"absent":("alive="+Shizuku.getBinder().isBinderAlive()+", ping="+Shizuku.pingBinder()))+"; permission="+(Shizuku.pingBinder()?Shizuku.checkSelfPermission():"unavailable");}
  catch(Exception e){return "Shizuku check: "+e.getClass().getSimpleName();}
 }
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();RecoveryLog.init(this);RecoveryLog.add("Background service created, PID "+android.os.Process.myPid());
  FoldAwakeDefault.persist(this);
  Shizuku.addBinderReceivedListenerSticky(binderReady);Shizuku.addBinderDeadListener(binderDead);Shizuku.addRequestPermissionResultListener(permissionResult);
  try{getContentResolver().registerContentObserver(android.provider.Settings.System.getUriFor("fold_lock_behavior"),false,foldSettingObserver);}catch(Exception e){RecoveryLog.add("Fold setting observer unavailable; periodic verification active");}
  IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);
getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("fold_live","Fold animation running",NotificationManager.IMPORTANCE_LOW));}
 private Notification notification(String message){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FoldBackgroundService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,"fold_live").setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Duo Fold Live is running").setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"Stop animation / wallpaper",stop).build()).build();
 }
 public int onStartCommand(Intent i,int flags,int id){
  // Must promote even when restoring a now-disabled service, then stop cleanly.
  startForeground(112,notification("Connecting to the live angle reader"));lastNotice="";
  if(i!=null&&"STOP".equals(i.getAction())){
   getSharedPreferences("standalone",0).edit().putBoolean("enabled",false).apply();
   try{org.duofold.live.wallpaperlayer.WallpaperRestore.setEnabled(this,false);}catch(java.io.IOException e){RecoveryLog.add("Could not save wallpaper stop: "+e.getClass().getSimpleName());}
   if(StandaloneService.Companion.getInstance()!=null)StandaloneService.Companion.getInstance().restart();
   if(!wanted()){stopSelf();return START_NOT_STICKY;}
  }
  if(!wanted()){stopSelf();return START_NOT_STICKY;}
  if(!running){running=true;main.post(supervise);}
  return START_STICKY;
 }
 private final Runnable supervise=new Runnable(){public void run(){
  if(!running)return;
  if(!wanted()){stopSelf();return;}
  boolean interactive=getSystemService(PowerManager.class).isInteractive();
  long now=SystemClock.elapsedRealtime();
  if(interactive)asleepSince=-1;else if(asleepSince<0)asleepSince=now;
  HealthTrace.sample(FoldBackgroundService.this);
  // Ignore transient screen-off during a panel handoff; suspend sustained sleep.
  if(!interactive&&now-asleepSince>=3000){
   if(angles!=null){angles.stop();angles=null;RecoveryLog.add("Reader suspended for screen-off");}
   LiveAngles.status="Screen asleep — reader suspended";
   main.postDelayed(this,30000);return;
  }
  if(interactive)org.duofold.live.wallpaperlayer.WallpaperRestore.tick(FoldBackgroundService.this);
  if(RotationMigration.needed(FoldBackgroundService.this)){
   if(angles!=null){angles.stop();angles=null;}
   clearEffect();nextReaderAttempt=0;
   if(interactive&&!getSystemService(KeyguardManager.class).isKeyguardLocked())RotationMigration.tick(FoldBackgroundService.this);
   LiveAngles.status=RotationMigration.status;
   if(!LiveAngles.status.equals(lastNotice)){lastNotice=LiveAngles.status;getSystemService(NotificationManager.class).notify(112,notification(lastNotice));}
   main.postDelayed(this,2000);return;
  }
  boolean foldEnabled=getSharedPreferences("standalone",0).getBoolean("enabled",false);
  try{
   if(!foldEnabled){if(angles!=null){angles.stop();angles=null;}LiveAngles.status="Fold animation disabled";}else{
   boolean reachable=Shizuku.pingBinder();
   if(reachable!=lastReachable){lastReachable=reachable;RecoveryLog.add("Shizuku reachability="+reachable);}
   if(!reachable){
    clearEffect();
    if(angles!=null){angles.stop();angles=null;}
    LiveAngles.status="Shizuku connection unavailable — check whether Shizuku says Running";
   }else if(Shizuku.checkSelfPermission()!=0){
    if(angles!=null){angles.stop();angles=null;}
    LiveAngles.status="Shizuku authorization needed — authorize Duo once";
   }else if(now>=nextReaderAttempt&&(angles==null||!angles.isRunning()||angles.stalled())){
    nextReaderAttempt=now+30000;
    if(angles!=null)angles.stop();
    angles=new LiveAngles(FoldBackgroundService.this);angles.start();
   }
   }
  }catch(Exception e){LiveAngles.status="Waiting for Shizuku: "+e.getClass().getSimpleName();}
  FoldAwakeDefault.tick(FoldBackgroundService.this);
  InnerDecorRecovery.tick(FoldBackgroundService.this);
  String notice=!foldEnabled?FoldAwakeDefault.status:LiveAngles.fresh()?"Live hinge control active":LiveAngles.status;
  if(!notice.equals(lastNotice)){lastNotice=notice;getSystemService(NotificationManager.class).notify(112,notification(notice));}
  SupportPrompts.tick(FoldBackgroundService.this);
  main.postDelayed(this,2000);
 }};
 public void onTaskRemoved(Intent intent){RecoveryLog.add("Settings task dismissed; background service remains running");super.onTaskRemoved(intent);}
 public void onDestroy(){RecoveryLog.add("Background service destroyed");Shizuku.removeBinderReceivedListener(binderReady);Shizuku.removeBinderDeadListener(binderDead);Shizuku.removeRequestPermissionResultListener(permissionResult);getContentResolver().unregisterContentObserver(foldSettingObserver);try{unregisterReceiver(screen);}catch(Exception ignored){}running=false;main.removeCallbacksAndMessages(null);if(angles!=null){angles.stop();angles=null;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
