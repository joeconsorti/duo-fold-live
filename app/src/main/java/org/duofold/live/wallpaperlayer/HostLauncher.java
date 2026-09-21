package org.duofold.live.wallpaperlayer;
import android.os.*;
import android.util.Base64;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;
/** Shell only starts the Android-managed test and observes Samsung logs; it creates no windows. */
public class HostLauncher extends Binder {
 public static final String TOKEN="org.duofold.live.wallpaperlayer.Launcher";
 volatile String report="Duo Wallpaper Layer 0.13\nReady",launchOutput="";
 volatile java.lang.Process test,logs;
 volatile boolean active;
 volatile int responses;
 volatile float angle=Float.NaN;
 volatile long last;
 final Set<Float> angles=Collections.synchronizedSet(new HashSet<>());
 final Handler main=new Handler(Looper.getMainLooper());
 final StringBuilder transitions=new StringBuilder();volatile String windowSnapshot="No transition captured";volatile boolean capturing;long lastCapture;
 synchronized void observeTransition(String event){
  transitions.append(event).append('\n');if(transitions.length()>12000)transitions.delete(0,transitions.length()-12000);
  if(capturing||SystemClock.elapsedRealtime()-lastCapture<200)return;capturing=true;lastCapture=SystemClock.elapsedRealtime();
  new Thread(()->{java.lang.Process dump=null;try{
   dump=new ProcessBuilder("dumpsys","-t","2","window","windows").redirectErrorStream(true).start();
   StringBuilder selected=new StringBuilder("Triggered by: "+event+"\n");
   try(BufferedReader reader=new BufferedReader(new InputStreamReader(dump.getInputStream()))){String line;boolean relevant=false;while((line=reader.readLine())!=null){
    if(line.matches(".*Window #.*Window\\{.*"))relevant=line.contains("Duo persistent wallpaper photo")||line.contains("FoldInteractive");
    if(relevant&&selected.length()<16000)selected.append(line).append('\n');
   }}windowSnapshot=selected.toString();
  }catch(Exception e){windowSnapshot="Window trace failed: "+e;}finally{if(dump!=null)dump.destroy();capturing=false;}},"Wallpaper transition trace").start();
 }
 synchronized String transitionReport(){return "\nTransition events:\n"+transitions+"\nLatest wallpaper window snapshot:\n"+windowSnapshot;}
 int owner=-1;
 public HostLauncher(){attachInterface(null,TOKEN);main.postDelayed(()->{if(!active)System.exit(0);},180000);}
 protected boolean onTransact(int code,Parcel in,Parcel out,int flags)throws RemoteException{
  if(code==INTERFACE_TRANSACTION){out.writeString(TOKEN);return true;}
  if(code==16777115){if(test!=null)test.destroy();if(logs!=null)logs.destroy();System.exit(0);return true;}
  in.enforceInterface(TOKEN);
  synchronized(this){int uid=Binder.getCallingUid();if(owner<0)owner=uid;if(owner!=uid)throw new SecurityException("Wrong caller");}
  if(code==1){ParcelFileDescriptor fd=in.readTypedObject(ParcelFileDescriptor.CREATOR);try{if(fd!=null)fd.close();}catch(IOException ignored){}startTest();out.writeNoException();out.writeString("Starting registered photo host. You can return to fold settings while the photo runs.");return true;}
  if(code==2){out.writeNoException();out.writeString(snapshot());return true;}
  if(code==3){out.writeNoException();out.writeString("Stop requested; removing photo layer.");return true;}
  return super.onTransact(code,in,out,flags);
 }
 synchronized void startTest(){
  if(active)return;
  active=true;report="Starting Android-managed photo host…";launchOutput="";responses=0;last=0;angles.clear();angle=Float.NaN;
  new Thread(()->{
   java.lang.Process observer=null;
   try{
    if(android.os.Process.myUid()!=2000)throw new IllegalStateException("Shizuku ADB mode required");
    observer=startObserver();
    test=new ProcessBuilder("am","instrument","-w","-r","--no-restart","--no-hidden-api-checks","org.duofold.live/org.duofold.live.wallpaperlayer.PhotoInstrumentation").redirectErrorStream(true).start();
    try(BufferedReader r=new BufferedReader(new InputStreamReader(test.getInputStream()))){String line;while((line=r.readLine())!=null){
     String prefix="INSTRUMENTATION_STATUS: duo_report=";
     if(line.startsWith(prefix)){report=new String(Base64.decode(line.substring(prefix.length()),Base64.DEFAULT),StandardCharsets.UTF_8);}
     else if(line.startsWith("INSTRUMENTATION_STATUS: duo_transition=")){observeTransition(new String(Base64.decode(line.substring("INSTRUMENTATION_STATUS: duo_transition=".length()),Base64.DEFAULT),StandardCharsets.UTF_8));}
     else if(!line.startsWith("INSTRUMENTATION_STATUS_CODE:")) {launchOutput+=line+"\n";if(launchOutput.length()>6000)launchOutput=launchOutput.substring(launchOutput.length()-6000);}
    }}
    int result=test.waitFor();launchOutput+="\nLauncher exit: "+result;
   }catch(Throwable e){report="Registered host launch failed: "+e;}
   finally{if(observer!=null)observer.destroy();active=false;test=null;main.postDelayed(()->{if(!active)System.exit(0);},180000);}
  },"Registered photo launcher").start();
 }
 java.lang.Process startObserver()throws IOException{
  final java.lang.Process child=new ProcessBuilder("logcat","-v","epoch","-T","1","-s","SprWallpaper|FoldInteractive:V","*:S").redirectErrorStream(true).start();logs=child;
  new Thread(()->{Pattern p=Pattern.compile("mCurrentAngle=([0-9]+(?:\\.[0-9]+)?)");try(BufferedReader r=new BufferedReader(new InputStreamReader(child.getInputStream()))){String line;while((line=r.readLine())!=null){Matcher m=p.matcher(line);if(!m.find())continue;try{long age=System.currentTimeMillis()-(long)(Double.parseDouble(line.trim().split("\\s+",2)[0])*1000);if(age< -100||age>1500)continue;float a=Float.parseFloat(m.group(1));if(a<0||a>180)continue;angle=a;last=SystemClock.elapsedRealtime()-Math.max(0,age);responses++;angles.add(a);}catch(Exception ignored){}}}catch(IOException ignored){}},"Samsung angle observer").start();return child;
 }
 String snapshot(){return report+"\n\nShizuku observer UID: "+android.os.Process.myUid()+"\nObserved Samsung angle: "+angle+"°; age: "+(last==0?-1:SystemClock.elapsedRealtime()-last)+" ms\nObserved events: "+responses+"; distinct: "+angles.size()+"\nThese include existing Duo Fold Live requests.\n"+launchOutput+transitionReport();}
}
