package org.duofold.live;
import java.lang.reflect.*;
/** Resolve a complete API family before capture; never mix nested types between versions. */
final class CaptureApi {
 static final String[] FAMILIES={"android.window.ScreenCaptureInternal","android.window.ScreenCapture"};
 final String name; final Class<?> builder; final Constructor<?> constructor; final Method capture;
 interface Lookup { Class<?> load(String name) throws ClassNotFoundException; }
 private CaptureApi(String n,Class<?> b,Constructor<?> c,Method m){name=n;builder=b;constructor=c;capture=m;}
 static CaptureApi resolve(Lookup lookup,String[] names,Class<?> surface,Class<?> rect,Class<?> surfaces)throws ReflectiveOperationException{
  StringBuilder failures=new StringBuilder();
  for(String name:names){
   try{
    Class<?> api=lookup.load(name),args=lookup.load(name+"$LayerCaptureArgs"),b=lookup.load(name+"$LayerCaptureArgs$Builder");
    Constructor<?> c=b.getConstructor(surface);
    b.getMethod("setSourceCrop",rect);b.getMethod("setFrameScale",float.class);
    b.getMethod("setChildrenOnly",boolean.class);b.getMethod("setExcludeLayers",surfaces);b.getMethod("build");
    Method m=api.getMethod("captureLayers",args);
    if(!Modifier.isStatic(m.getModifiers()))throw new NoSuchMethodException("captureLayers not static");
    return new CaptureApi(name,b,c,m);
   }catch(ClassNotFoundException|NoSuchMethodException e){
    if(failures.length()>0)failures.append("; ");
    failures.append(name).append(": ").append(e.getClass().getSimpleName()).append(" ").append(e.getMessage());
   }
  }
  throw new ClassNotFoundException("No compatible layer capture API: "+failures);
 }
}
