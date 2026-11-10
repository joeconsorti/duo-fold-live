package org.duofold.live;
import android.content.*;
import android.app.KeyguardManager;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;
import java.util.*;
/** Observation only. Bounded in-memory history; no wake locks or power/state requests. */
public final class ClosureDiagnostics implements DisplayManager.DisplayListener, SensorEventListener {
 private static final ArrayDeque<String> events=new ArrayDeque<>();
 private static Context context;
 private static float raw=Float.NaN,filtered=Float.NaN;
 private static long stamp;
 private static String angleKey="";
 private final DisplayManager displays;
 private final SensorManager sensors;
 private final Map<Integer,String> displayStates=new HashMap<>();
 private final Map<Sensor,String> sensorStates=new HashMap<>();
 private String inventory="";
 private static String route(){return context!=null&&context.getSharedPreferences("standalone",0).getBoolean("dual",false)?"screenshot":"normal";}
 private static synchronized void add(String text){FoldDiagnostics.event("DISPLAY/POWER",text);events.addLast(SystemClock.elapsedRealtime()+" ms route="+route()+" "+text);while(events.size()>180)events.removeFirst();}
 public ClosureDiagnostics(Context c,Handler handler){
  context=c.getApplicationContext();displays=c.getSystemService(DisplayManager.class);sensors=c.getSystemService(SensorManager.class);
  add("MONITOR_START wallMs="+System.currentTimeMillis());
  try{displays.registerDisplayListener(this,handler);for(Display d:displays.getDisplays())display(d.getDisplayId());}catch(Exception e){add("DISPLAY_UNAVAILABLE "+e.getClass().getSimpleName());}
  StringBuilder found=new StringBuilder();int candidates=0;
  try{for(Sensor s:sensors.getSensorList(Sensor.TYPE_ALL)){
   String name=(s.getName()+" "+s.getStringType()).toLowerCase(Locale.ROOT);
   if(!(name.contains("hall")||name.contains("fold")||name.contains("flip")||s.getType()==Sensor.TYPE_HINGE_ANGLE))continue;
   candidates++;boolean eligible=!s.isWakeUpSensor()&&s.getReportingMode()==Sensor.REPORTING_MODE_ON_CHANGE;
   String result="not subscribed (wake-up or non-on-change sensor)";
   if(eligible)try{result="subscribed="+sensors.registerListener(this,s,SensorManager.SENSOR_DELAY_NORMAL,handler);}catch(Exception e){result="unavailable="+e.getClass().getSimpleName();}
   found.append(s.getName()).append(" type=").append(s.getType()).append(" stringType=").append(s.getStringType()).append(" ").append(result).append('\n');
  }}catch(Exception e){found.append("Sensor enumeration unavailable: ").append(e.getClass().getSimpleName());}
  inventory="Exposed Hall/fold/hinge candidates="+candidates+"\n"+found;
  synchronized(ClosureDiagnostics.class){sensorInventory=inventory;}
 }
 private static String sensorInventory="Monitor not started";
 private static int band(float v){return !Float.isFinite(v)?-1:v<=0?0:v<=1?1:v<=5?5:v<=10?10:v<=20?20:180;}
 public static synchronized void angle(float r,float f,long sample){
  raw=r;filtered=f;stamp=sample;
  boolean fresh=sample>0&&SystemClock.elapsedRealtime()-sample<750;
  String key=route()+":"+band(r)+":"+band(f)+":"+fresh;
  if(!key.equals(angleKey)){angleKey=key;add("HINGE_BAND raw="+r+" filtered="+f+" sampleMs="+sample+" ageMs="+(sample>0?SystemClock.elapsedRealtime()-sample:-1)+" fresh="+fresh);}
 }
 public static synchronized void screen(String action){
  if(context==null)return;
  String state="";try{state=" interactive="+context.getSystemService(PowerManager.class).isInteractive()+" keyguard="+context.getSystemService(KeyguardManager.class).isKeyguardLocked()+" fold_lock_behavior="+android.provider.Settings.System.getString(context.getContentResolver(),"fold_lock_behavior");}catch(Exception e){state=" snapshot unavailable="+e.getClass().getSimpleName();}
  add("BROADCAST "+action+state+" raw="+raw+" filtered="+filtered+" angleAgeMs="+(stamp>0?SystemClock.elapsedRealtime()-stamp:-1));
 }
 private void display(int id){Display d=displays.getDisplay(id);String value=d==null?"absent":"physical="+PanelIdentity.identity(d)+" state="+d.getState()+" rotation="+d.getRotation()+" mode="+d.getMode().getPhysicalWidth()+"x"+d.getMode().getPhysicalHeight();if(!value.equals(displayStates.put(id,value)))add("DISPLAY D"+id+" "+value);}
 public void onDisplayAdded(int id){display(id);}public void onDisplayRemoved(int id){display(id);}public void onDisplayChanged(int id){display(id);}
 public void onSensorChanged(SensorEvent e){String values=Arrays.toString(Arrays.copyOf(e.values,Math.min(e.values.length,4)));String key=e.sensor.getType()==Sensor.TYPE_HINGE_ANGLE&&e.values.length>0?"band="+band(e.values[0]):values;if(!key.equals(sensorStates.put(e.sensor,key)))add("SENSOR name="+e.sensor.getName()+" type="+e.sensor.getType()+" eventMs="+(e.timestamp/1000000L)+" values="+values);}
 public void onAccuracyChanged(Sensor s,int accuracy){}
 public void close(){try{displays.unregisterDisplayListener(this);}catch(Exception ignored){}try{sensors.unregisterListener(this);}catch(Exception ignored){}add("MONITOR_STOP");}
 public static synchronized String report(){return "Full-close diagnostics (elapsed-realtime ms; callback receipt except sensor eventMs). Display state: 1=OFF, 2=ON, 3=DOZE, 4=DOZE_SUSPEND, 5=VR, 6=ON_SUSPEND.\nBase state is derived OS state, not proof of magnet activation. No raw Hall event unless SENSOR explicitly reports one. No wake-up sensors enabled; sleep can delay callbacks. In-memory bounded history.\n"+sensorInventory+"\n"+String.join("\n",events);}
}
