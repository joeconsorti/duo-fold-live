package org.duofold.powerlab;
import android.app.*;
import android.content.*;
import android.hardware.*;
import android.hardware.display.*;

import android.os.*;
import android.view.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.zip.*;

public class RecorderService extends Service implements DisplayManager.DisplayListener,SensorEventListener {
 static volatile String status="Idle";
 static volatile boolean active=false;
 static volatile File latest;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private DisplayManager displays;private SensorManager sensors;
 private BufferedWriter events; private volatile boolean closing=false;private String outcome="";private int lines=0;

 private long lastSensor=0;
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("capture","Diagnostic recording",NotificationManager.IMPORTANCE_LOW));}
 public int onStartCommand(Intent i,int flags,int id){
  if(i!=null&&"stop".equals(i.getAction())){worker.execute(Bridge::stop);status="Stop requested; waiting for the bounded trace to flush";return START_NOT_STICKY;}
  if(active)return START_NOT_STICKY;
  active=true;status="Preparing diagnostic";
  startForeground(1,notification(status));
  int mode=i==null?0:i.getIntExtra("mode",0);
  final String recipe=i==null?null:i.getStringExtra("recipe");
  getSharedPreferences("reports",0).edit().putString("mode",Probe.modeName(mode)).apply();
  try{
   events=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(getFilesDir(),"events.txt")),StandardCharsets.UTF_8));
   event("START mode="+mode+" model="+Build.MODEL+" build="+Build.FINGERPRINT+" SDK="+Build.VERSION.SDK_INT+" wallMs="+System.currentTimeMillis());
   event("Callback receipt is not optical timing. Selected mode may request bounded power/device-state leases; consult power-experiment.txt. No overlay or capture surface.");
   displays=getSystemService(DisplayManager.class);displays.registerDisplayListener(this,main);snapshot("INITIAL");
   sensors=getSystemService(SensorManager.class);for(Sensor s:sensors.getSensorList(Sensor.TYPE_ALL)){if(s.getType()==Sensor.TYPE_HINGE_ANGLE||s.getName().toLowerCase().contains("hall")||s.getName().toLowerCase().contains("fold"))event("SENSOR available="+s.getName()+" type="+s.getType()+" wakeup="+s.isWakeUpSensor());}
   for(Sensor sensor:sensors.getSensorList(Sensor.TYPE_ALL)){
    String name=sensor.getName().toLowerCase(java.util.Locale.ROOT);
    if(!sensor.isWakeUpSensor() && (sensor.getType()==Sensor.TYPE_HINGE_ANGLE||name.contains("hall")||name.contains("fold")))
     try{event("SENSOR subscribe "+sensor.getName()+" accepted="+sensors.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME));}catch(Exception e){event("SENSOR rejected "+sensor.getName()+" "+e);}
   }
   event("Wake-up sensors deliberately not subscribed in baseline; missing sensor events are not evidence of no Hall activation.");
   event("Device-state details collected by shell logs and dumps; public device-state callback not available in this SDK.");
   worker.execute(()->{try{if(recipe==null)Bridge.start(mode);else Bridge.startRecipe(recipe);poll();}catch(Exception e){fail(e);}});
   main.postDelayed(()->{if(active){status="Recording watchdog stopped capture";Bridge.stop();finishCapture();}},120000);
  }catch(Exception e){fail(e);}
  return START_NOT_STICKY;
 }
 private Notification notification(String text){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RecorderService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("Fold Power Lab").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
 }
 private void poll(){
  if(closing)return;
  try{Parcel p=Bridge.request(2,null,null);String text=p.readString();long size=p.readLong();boolean busy=p.readInt()!=0;p.recycle();status=text;
   main.post(()->{if(!closing)getSystemService(NotificationManager.class).notify(1,notification(text));});
   if(!busy){outcome=text;if(size>0&&size<256L*1024*1024){
     File shell=new File(getFilesDir(),"shell.zip");try(FileOutputStream out=new FileOutputStream(shell)){long offset=0;while(offset<size){Parcel r=Bridge.request(3,offset,null);byte[] b=r.createByteArray();r.recycle();if(b==null||b.length==0)throw new IOException("Incomplete shell report");out.write(b);offset+=b.length;}if(offset!=size)throw new IOException("Report length mismatch");}
     main.post(()->{stopObservers();worker.execute(()->merge(shell));});
    }else{if(size>=256L*1024*1024)status="Report exceeds export limit; capture incomplete";main.post(this::finishCapture);}return;}
   main.postDelayed(()->{if(!closing)worker.execute(this::poll);},1000);
  }catch(Exception e){fail(e);}
 }
 private void merge(File shell){try{
  File result=new File(getFilesDir(),"Fold-Power-Lab-"+getSharedPreferences("reports",0).getString("mode","capture")+"-"+System.currentTimeMillis()+".zip");
  try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(result));ZipInputStream in=new ZipInputStream(new FileInputStream(shell))){ZipEntry e;byte[] b=new byte[16384];int n;while((e=in.getNextEntry())!=null){out.putNextEntry(new ZipEntry(e.getName()));while((n=in.read(b))>=0)out.write(b,0,n);out.closeEntry();}out.putNextEntry(new ZipEntry("app-events.txt"));try(InputStream ev=new FileInputStream(new File(getFilesDir(),"events.txt"))){while((n=ev.read(b))>=0)out.write(b,0,n);}out.closeEntry();}
  latest=result;getSharedPreferences("reports",0).edit().putString("latest",result.getName()).apply();shell.delete();Bridge.cleanup();status="Report ready — Save report ZIP\n"+outcome;
 }catch(Exception e){status="Export failed: "+e;}finally{main.post(this::finishCapture);}}
 private void fail(Exception e){status="Failed: "+e.getClass().getSimpleName()+": "+e.getMessage();main.post(this::finishCapture);}
 private void stopObservers(){
  if(displays!=null){displays.unregisterDisplayListener(this);displays=null;}if(sensors!=null){sensors.unregisterListener(this);sensors=null;}
  event("END interactive="+getSystemService(PowerManager.class).isInteractive());try{if(events!=null)events.close();}catch(IOException ignored){}events=null;
 }
 private void finishCapture(){if(closing)return;closing=true;stopObservers();active=false;main.removeCallbacksAndMessages(null);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 public void onDestroy(){if(active)Bridge.stop();stopObservers();active=false;main.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
 private void event(String text){try{if(events!=null&&lines++<20000){events.write(SystemClock.elapsedRealtimeNanos()+" "+text+"\n");}}catch(IOException ignored){}}
 private void snapshot(String why){if(displays==null)return;for(Display d:displays.getDisplays()){Display.Mode m=d.getMode();event(why+" D"+d.getDisplayId()+" name="+d.getName()+" state="+d.getState()+" size="+m.getPhysicalWidth()+"x"+m.getPhysicalHeight()+" rotation="+d.getRotation()+" hz="+m.getRefreshRate()+" interactive="+getSystemService(PowerManager.class).isInteractive());}}
 public void onDisplayAdded(int id){snapshot("ADDED "+id);}public void onDisplayChanged(int id){snapshot("CHANGED "+id);}public void onDisplayRemoved(int id){event("REMOVED "+id);snapshot("AFTER_REMOVE");}
 public void onAccuracyChanged(Sensor s,int a){}
 public void onSensorChanged(SensorEvent e){event("SENSOR name="+e.sensor.getName()+" type="+e.sensor.getType()+" eventNs="+e.timestamp+" values="+java.util.Arrays.toString(e.values));}
}
