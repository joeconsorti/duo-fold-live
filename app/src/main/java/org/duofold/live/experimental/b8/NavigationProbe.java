package org.duofold.live.experimental.b8;
import java.io.*;
import java.lang.reflect.*;
import android.os.SystemClock;
/** Read-only system policy and capability observations, never registers as SystemUI. */
final class NavigationProbe {
 static synchronized void sample(File dir,String phase){
  try(FileWriter w=new FileWriter(new File(dir,"navigation-policy.txt"),true)){
   w.write("\n"+SystemClock.elapsedRealtime()+" PHASE "+phase+" (policy/API observations, not visible bars)\n");
   Object dm=PowerExperiment.service("display","android.hardware.display.IDisplayManager");
   Class<?> dc=Class.forName("android.hardware.display.IDisplayManager");
   for(int id=0;id<2;id++)try{
    Object info=dc.getMethod("getDisplayInfo",int.class).invoke(dm,id);
    if(info==null){w.write("D"+id+" DisplayInfo absent\n");continue;}
    for(String name:new String[]{"uniqueId","type","flags","canHostTasks","state","committedState","displayGroupId","removeMode"})try{w.write("D"+id+" info."+name+"="+info.getClass().getField(name).get(info)+"\n");}catch(Throwable e){w.write("D"+id+" info."+name+" unavailable="+root(e)+"\n");}
    int flags=info.getClass().getField("flags").getInt(info);
    for(Field flag:android.view.Display.class.getFields())if(flag.getType()==int.class&&Modifier.isStatic(flag.getModifiers())&&flag.getName().startsWith("FLAG_"))try{int mask=flag.getInt(null);w.write("D"+id+" "+flag.getName()+" mask="+mask+" present="+(mask!=0&&(flags&mask)==mask)+"\n");}catch(Throwable ignored){}
   }catch(Throwable e){w.write("D"+id+" DisplayInfo unavailable="+root(e)+"\n");}
   w.write("Firmware source finding (not runtime proof): supplied LogicalDisplay.validateCanHostTasksLocked explicitly rejects logical D1. Task movement can still succeed; this field is not a task-placement assertion.\n");
   Object wm=PowerExperiment.service("window","android.view.IWindowManager");Class<?> c=Class.forName("android.view.IWindowManager");
   for(int id=0;id<2;id++)for(String name:new String[]{"shouldShowSystemDecors","hasNavigationBar","getDisplayImePolicy","isEligibleForDesktopMode"}){
    try{w.write("D"+id+" "+name+"="+c.getMethod(name,int.class).invoke(wm,id)+"\n");}catch(Throwable e){w.write("D"+id+" "+name+" unavailable="+root(e)+"\n");}
   }
   for(String name:new String[]{"getTopFocusedDisplayId","getImeDisplayId","hasTaskbarTarget","getCurrentAnimatorScale"})try{w.write(name+"="+c.getMethod(name).invoke(wm)+"\n");}catch(Throwable e){w.write(name+" unavailable="+root(e)+"\n");}
   if(phase.equals("before move")){
    try{Class<?> flags=Class.forName("android.window.DesktopExperienceFlags");Object flag=flags.getField("ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT").get(null);w.write("DISPLAY_CONTENT_MODE_MANAGEMENT="+flags.getMethod("isTrue").invoke(flag)+"; shell process observation\n");}catch(Throwable e){w.write("DISPLAY_CONTENT_MODE_MANAGEMENT unavailable="+root(e)+"\n");}
    for(String[] flag:new String[][]{{"com.android.window.flags.Flags","enableDisplayContentModeManagement"},{"com.android.server.power.PowerManagerUtil","SEC_FEATURE_FOLD_COVER_DISPLAY"}})try{
     Class<?> f=Class.forName(flag[0]);Object value;
     try{value=f.getMethod(flag[1]).invoke(null);}catch(NoSuchMethodException absent){value=f.getField(flag[1]).get(null);}
     w.write("POLICY_FLAG "+flag[0]+"."+flag[1]+"="+value+"; shell process observation, not server cached value\n");
    }catch(Throwable e){w.write("POLICY_FLAG unavailable "+flag[0]+" "+root(e)+"\n");}
   }
   if(phase.equals("before move"))for(String api:new String[]{"android.view.IWindowManager","com.android.internal.statusbar.IStatusBarService"}){
    try{w.write("CAPABILITIES "+api+"\n");for(Method m:Class.forName(api).getMethods()){String n=m.getName().toLowerCase();if(n.contains("bar")||n.contains("decor")||n.contains("ime")||n.contains("focus"))w.write(m.toString()+"\n");}}catch(Throwable e){w.write("Unavailable "+root(e)+"\n");}
   }
  }catch(Throwable ignored){}
 }
 private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
