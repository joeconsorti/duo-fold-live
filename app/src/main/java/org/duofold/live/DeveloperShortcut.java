package org.duofold.live;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
/** Observe only this app's activity windows; never installs a system-wide gesture. */
final class DeveloperShortcut implements Application.ActivityLifecycleCallbacks {
 static final String EXTRA="org.duofold.live.OPEN_DEVELOPER";
 public void onActivityPostCreated(Activity activity,Bundle state){
  Window.Callback original=activity.getWindow().getCallback();if(original==null)return;
  Gesture gesture=new Gesture(activity,original);
  Window.Callback wrapper=(Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,method,args)->{
   if(method.getName().equals("dispatchTouchEvent")&&gesture.touch((MotionEvent)args[0]))return true;
   try{return method.invoke(original,args);}catch(InvocationTargetException e){throw e.getCause();}
  });
  activity.getWindow().setCallback(wrapper);
 }
 private static final class Gesture {
  final Activity activity;final Window.Callback original;final float slop;
  final Map<Integer,float[]> points=new HashMap<>();
  long began;boolean captured,eligible;int maximum;
  Gesture(Activity a,Window.Callback original){activity=a;this.original=original;slop=ViewConfiguration.get(a).getScaledTouchSlop()*2f;}
  boolean touch(MotionEvent e){
   int action=e.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){began=e.getEventTime();points.clear();captured=false;eligible=true;maximum=1;}
   if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){int i=e.getActionIndex();points.put(e.getPointerId(i),new float[]{e.getX(i),e.getY(i)});}
   maximum=Math.max(maximum,e.getPointerCount());
   if(e.getEventTime()-began>500||maximum>4)eligible=false;
   for(int i=0;i<e.getPointerCount();i++){float[] p=points.get(e.getPointerId(i));if(p!=null&&(Math.abs(e.getX(i)-p[0])>slop||Math.abs(e.getY(i)-p[1])>slop))eligible=false;}
   if(action==MotionEvent.ACTION_POINTER_DOWN&&maximum==4&&!captured&&eligible){
    captured=true;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);original.dispatchTouchEvent(cancel);cancel.recycle();
   }
   boolean consume=captured;
   if(action==MotionEvent.ACTION_UP){
    if(captured&&eligible){
     if(activity instanceof MainActivity)((MainActivity)activity).openDeveloperSettings();
     else activity.startActivity(new Intent(activity,MainActivity.class).putExtra(EXTRA,true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    captured=false;points.clear();
   }else if(action==MotionEvent.ACTION_CANCEL){captured=false;eligible=false;points.clear();}
   return consume;
  }
 }
 public void onActivityCreated(Activity a,Bundle b){}
 public void onActivityStarted(Activity a){}
 public void onActivityResumed(Activity a){}
 public void onActivityPaused(Activity a){}
 public void onActivityStopped(Activity a){}
 public void onActivitySaveInstanceState(Activity a,Bundle b){}
 public void onActivityDestroyed(Activity a){}
}
