package org.duofold.live;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;
/** Main-thread lifecycle for exclusively owned background UserServices. */
public final class HelperBinding implements ServiceConnection {
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static final Set<HelperBinding> active=new HashSet<>();
 private static final Map<String,String> states=new LinkedHashMap<>();
 private final Shizuku.UserServiceArgs args;
 private final String label;
 private final ServiceConnection callback;
 private final Consumer<String> failure;
 private final HelperAttempt attempt=new HelperAttempt();
 private final long started=SystemClock.elapsedRealtime();
 private final Runnable timeout=()->fail("no service connection callback after 35 s; retrying");
 public HelperBinding(Shizuku.UserServiceArgs args,String label,ServiceConnection callback,Consumer<String> failure){
  this.args=args;this.label=label;this.callback=callback;this.failure=failure;
 }
 public void start(){
  active.add(this);note("bind requested");main.postDelayed(timeout,HelperAttempt.START_TIMEOUT_MS);
  try{Shizuku.bindUserService(args,this);}catch(Exception e){fail("bind failed: "+e);}
 }
 public void onServiceConnected(ComponentName name,IBinder binder){main.post(()->{
  if(!attempt.connect())return;
  main.removeCallbacks(timeout);note("connected after "+(SystemClock.elapsedRealtime()-started)+" ms");
  callback.onServiceConnected(name,binder);
 });}
 public void onServiceDisconnected(ComponentName name){main.post(()->fail("service disconnected; retrying"));}
 public void stage(String stage){if(attempt.open())note(stage);}
 private void note(String stage){synchronized(states){states.put(label,stage);}RecoveryLog.add(label+": "+stage);}
 private void fail(String reason){if(!attempt.open())return;note(reason);close();failure.accept(label+": "+reason);}
 public void close(){
  if(!attempt.finish())return;
  main.removeCallbacks(timeout);active.remove(this);
  // remove=true alone relies on a remote death callback to clear the local SDK cache.
  // A helper that never attached cannot supply that callback. Detach explicitly first.
  try{Shizuku.unbindUserService(args,this,false);}catch(Exception e){RecoveryLog.add(label+" callback detach: "+e.getClass().getSimpleName());}
  try{Shizuku.unbindUserService(args,this,true);}catch(Exception e){RecoveryLog.add(label+" remote cleanup: "+e.getClass().getSimpleName());}
 }
 public static void reset(String reason){for(HelperBinding b:new ArrayList<>(active))b.fail(reason);}
 public static String report(){synchronized(states){StringBuilder s=new StringBuilder("Helper connections:");for(Map.Entry<String,String> e:states.entrySet())s.append("\n").append(e.getKey()).append(": ").append(e.getValue());return s.toString();}}
}
