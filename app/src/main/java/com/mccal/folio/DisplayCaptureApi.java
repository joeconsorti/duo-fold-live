package com.mccal.folio;
import java.lang.reflect.*;
import java.util.function.*;
/** Capture the real display through WindowManager, preserving explicit layer exclusions. */
final class DisplayCaptureApi {
 final String name;final Class<?> builder;final Constructor<?> constructor,listenerConstructor;final Method capture;
 final boolean statusCallback;
 private DisplayCaptureApi(String n,Class<?> b,Constructor<?> c,Constructor<?> l,Method m,boolean status){name=n;builder=b;constructor=c;listenerConstructor=l;capture=m;statusCallback=status;}
 static DisplayCaptureApi resolve(CaptureApi.Lookup lookup,Class<?> wm,Class<?> rect,Class<?> surfaces)throws ReflectiveOperationException{
  StringBuilder errors=new StringBuilder();
  for(String name:CaptureApi.FAMILIES){
   try{
    Class<?> args=lookup.load(name+"$CaptureArgs"),b=lookup.load(name+"$CaptureArgs$Builder"),listener=lookup.load(name+"$ScreenCaptureListener");
    Constructor<?> c=b.getConstructor();b.getMethod("setSourceCrop",rect);b.getMethod("setFrameScale",float.class);b.getMethod("setExcludeLayers",surfaces);b.getMethod("build");
    Constructor<?> l;boolean status;
    try{l=listener.getConstructor(ObjIntConsumer.class);status=true;}catch(NoSuchMethodException e){l=listener.getConstructor(Consumer.class);status=false;}
    return new DisplayCaptureApi(name,b,c,l,wm.getMethod("captureDisplay",int.class,args,listener),status);
   }catch(ClassNotFoundException|NoSuchMethodException e){errors.append(name).append(": ").append(e).append("; ");}
  }
  throw new ClassNotFoundException("No compatible WindowManager capture API: "+errors);
 }
}
