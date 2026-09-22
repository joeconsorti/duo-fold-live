package org.duofold.live;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Dedicated Shizuku process: setting writes cannot wait behind screen capture. */
public class FoldSettingsService extends Binder {
 public static final String TOKEN="org.duofold.live.FoldSettings";
 private int owner=-1;
 public FoldSettingsService(){attachInterface(null,TOKEN);}
 @Override protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==INTERFACE_TRANSACTION){reply.writeString(TOKEN);return true;}
  if(code==16777115){System.exit(0);return true;}
  if(code!=1)return super.onTransact(code,data,reply,flags);
  data.enforceInterface(TOKEN);int caller=Binder.getCallingUid();if(owner<0)owner=caller;if(owner!=caller)throw new SecurityException("Wrong caller");
  String action=data.readString(),original=data.readString();int user=caller/100000;
  Bundle out=new Bundle();long identity=Binder.clearCallingIdentity();
  try{
   if("always".equals(action))FoldRotationHold.recoverAbandoned(user);
   if(action.startsWith("decor_")){out=decor(action,original);}else{
   String before=command(user,"get",null),expected;
   if(!known(before))throw new IllegalStateException("Unrecognized fold setting: "+before);
   if("always".equals(action))expected="stay_awake_on_fold_key";
   else throw new IllegalArgumentException("Unknown fold setting action");
   if(!expected.equals(before))command(user,"null".equals(expected)?"delete":"put",expected);
   String actual=command(user,"get",null);if(!expected.equals(actual))throw new IOException("Readback mismatch: "+actual);
   out.putBoolean("ok",true);out.putString("previous",before);out.putString("value",actual);out.putInt("user",user);
   }
  }catch(Exception e){out.putString("error",e.toString());}finally{Binder.restoreCallingIdentity(identity);}
  reply.writeNoException();reply.writeBundle(out);return true;
 }
 private static String displayApis(){
  StringBuilder available=new StringBuilder();
  for(String name:new String[]{"android.view.IWindowManager","android.hardware.display.IDisplayManager"}){
   try{for(java.lang.reflect.Method m:Class.forName(name).getMethods()){
    String n=m.getName().toLowerCase(Locale.ROOT);
    if(n.contains("decor")||n.contains("displaycontent")||n.contains("desktop")||n.contains("focus")||n.contains("primary")||n.contains("fold"))available.append(m.toGenericString()).append("; ");
   }}catch(Exception e){available.append(name).append(": ").append(e.getClass().getSimpleName());}
  }
  return available.toString();
 }
 private static Bundle decor(String action,String saved)throws Exception{
  if("decor_apis".equals(action)){
   Bundle result=new Bundle();result.putBoolean("ok",true);result.putString("displayApis",displayApis());return result;
  }
  Object dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  java.lang.reflect.Method get=dm.getClass().getMethod("getDisplayInfo",int.class);
  int display=1;Object info=get.invoke(dm,display);
  org.json.JSONObject lease=saved==null?null:new org.json.JSONObject(saved);
  if("decor_restore".equals(action)){
   info=null;
   for(int id:new int[]{0,1}){Object candidate=get.invoke(dm,id);if(candidate!=null&&lease.getString("physical").equals(candidate.getClass().getField("uniqueId").get(candidate))){display=id;info=candidate;break;}}
  }
  if(info==null)throw new IllegalStateException("Inner physical display unavailable; retry restoration when available");
  String physical=String.valueOf(info.getClass().getField("uniqueId").get(info));
  if(!physical.startsWith("local:"))throw new SecurityException("Not a built-in physical display");
  if(lease!=null&&!physical.equals(lease.getString("physical")))throw new IllegalStateException("Physical mapping changed; refusing stale display ID");
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
  Object wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
  Class<?> api=Class.forName("android.view.IWindowManager");
  boolean before=(boolean)api.getMethod("shouldShowSystemDecors",int.class).invoke(wm,display);
  java.lang.reflect.Method setter=null;
  try{setter=api.getMethod("setShouldShowSystemDecors",int.class,boolean.class);}catch(NoSuchMethodException unavailable){}
  String available=displayApis();
  if("decor_enable".equals(action)||"decor_restore".equals(action)){
   if(lease==null)throw new IllegalArgumentException("Missing saved display policy");
   boolean wanted="decor_enable".equals(action)||lease.getBoolean("original");
   // An old journal may represent a request that failed before any write.
   // Confirm the saved value on this physical panel before clearing it.
   if(before!=wanted){
    if(setter==null)throw new UnsupportedOperationException("No decoration setter; original policy not confirmed. Available APIs: "+available);
    setter.invoke(wm,display,wanted);
   }
   boolean actual=(boolean)api.getMethod("shouldShowSystemDecors",int.class).invoke(wm,display);
   if(actual!=wanted)throw new IllegalStateException("Decoration readback mismatch; journal retained");
  }else if(!"decor_read".equals(action))throw new IllegalArgumentException("Unknown display action");
  Bundle out=new Bundle();out.putBoolean("ok",true);out.putString("physical",physical);out.putBoolean("original",before);
  out.putBoolean("decor",(boolean)api.getMethod("shouldShowSystemDecors",int.class).invoke(wm,display));
  out.putBoolean("nav",(boolean)api.getMethod("hasNavigationBar",int.class).invoke(wm,display));
  out.putBoolean("setterAvailable",setter!=null);out.putString("displayApis",available.toString());
  out.putInt("display",display);return out;
 }
 private static boolean known(String s){return Arrays.asList("null","stay_awake_on_fold_key","selective_stay_awake_key","sleep_on_fold_key").contains(s);}
 private static String command(int user,String verb,String value)throws Exception{
  List<String> args=new ArrayList<>(Arrays.asList("settings","--user",Integer.toString(user),verb,"system","fold_lock_behavior"));if("put".equals(verb))args.add(value);
  java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  try{if(!p.waitFor(2,TimeUnit.SECONDS))throw new IOException("Settings command timed out");String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();if(p.exitValue()!=0)throw new IOException("Settings command failed: "+text);return text;}finally{p.destroyForcibly();}
 }
}
