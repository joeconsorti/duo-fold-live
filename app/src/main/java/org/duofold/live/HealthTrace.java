package org.duofold.live;
import android.content.*;
import android.os.*;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Five-minute bounded samples; no wake locks, alarms, screenshots or app content. */
public final class HealthTrace {
 private static long next;
 private static final AtomicBoolean pending=new AtomicBoolean();
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 public static void sample(Context context){
  long now=SystemClock.elapsedRealtime();if(now<next||!pending.compareAndSet(false,true))return;
  next=now+300000;Context c=context.getApplicationContext();
  worker.execute(()->{try{
   Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
   PowerManager power=c.getSystemService(PowerManager.class);
   Runtime rt=Runtime.getRuntime();
   String[] threads=new File("/proc/self/task").list(),fds=new File("/proc/self/fd").list();
   long wall=System.currentTimeMillis();
   String row=wall+" uptime="+SystemClock.elapsedRealtime()+" pid="+android.os.Process.myPid()+" cpuMs="+android.os.Process.getElapsedCpuTime()+" heapKB="+(rt.totalMemory()-rt.freeMemory())/1024+" nativeKB="+Debug.getNativeHeapAllocatedSize()/1024+" threads="+(threads==null?-1:threads.length)+" fds="+(fds==null?-1:fds.length)+" interactive="+power.isInteractive()+" thermal="+power.getCurrentThermalStatus()+" batteryTempTenthsC="+(battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1))+" plugged="+(battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1));
   android.content.SharedPreferences prefs=c.getSharedPreferences("health_trace",0);
   java.util.ArrayList<String> rows=new java.util.ArrayList<>();
   for(String old:prefs.getString("samples","").split("\n"))try{if(Long.parseLong(old.split(" ",2)[0])>=wall-86400000L)rows.add(old);}catch(Exception ignored){}
   rows.add(row);while(rows.size()>288)rows.remove(0);
   prefs.edit().putString("samples",String.join("\n",rows)).apply();
  }catch(Exception ignored){}finally{pending.set(false);}});
 }
 public static String report(Context c){return c.getSharedPreferences("health_trace",0).getString("samples","No health samples yet");}
}
