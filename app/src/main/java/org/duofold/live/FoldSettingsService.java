package org.duofold.live;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Dedicated Shizuku process: setting writes cannot wait behind screen capture. */
public final class FoldSettingsService extends Binder {
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
   String before=command(user,"get",null),expected;
   if(!known(before))throw new IllegalStateException("Unrecognized fold setting: "+before);
   if("always".equals(action))expected="stay_awake_on_fold_key";
   else if("restore".equals(action)&&known(original))expected=original;
   else throw new IllegalArgumentException("Unknown fold setting action");
   if(!expected.equals(before))command(user,"null".equals(expected)?"delete":"put",expected);
   String actual=command(user,"get",null);if(!expected.equals(actual))throw new IOException("Readback mismatch: "+actual);
   out.putBoolean("ok",true);out.putString("previous",before);out.putString("value",actual);out.putInt("user",user);
  }catch(Exception e){out.putString("error",e.toString());}finally{Binder.restoreCallingIdentity(identity);}
  reply.writeNoException();reply.writeBundle(out);return true;
 }
 private static boolean known(String s){return Arrays.asList("null","stay_awake_on_fold_key","selective_stay_awake_key","sleep_on_fold_key").contains(s);}
 private static String command(int user,String verb,String value)throws Exception{
  List<String> args=new ArrayList<>(Arrays.asList("settings","--user",Integer.toString(user),verb,"system","fold_lock_behavior"));if("put".equals(verb))args.add(value);
  java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  try{if(!p.waitFor(2,TimeUnit.SECONDS))throw new IOException("Settings command timed out");String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();if(p.exitValue()!=0)throw new IOException("Settings command failed: "+text);return text;}finally{p.destroyForcibly();}
 }
}
