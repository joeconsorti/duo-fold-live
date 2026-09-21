package com.mccal.folio;
import java.util.function.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DisplayCaptureApiTest {
 public static class Args {}
 public static class Builder {
  public Builder(){}
  public void setSourceCrop(Object crop){}
  public void setFrameScale(float scale){}
  public void setExcludeLayers(Object[] exclusions){}
  public Args build(){return new Args();}
 }
 public static class Listener {public Listener(ObjIntConsumer<Object> callback){}}
 public static class OldListener {public OldListener(Consumer<Object> callback){}}
 public static class Wm {public void captureDisplay(int id,Args args,Listener listener){}}
 public static class OldWm {public void captureDisplay(int id,Args args,OldListener listener){}}
 private Class<?> shape(String n,boolean old){
  if(n.endsWith("$Builder"))return Builder.class;
  if(n.endsWith("$ScreenCaptureListener"))return old?OldListener.class:Listener.class;
  return Args.class;
 }
 @Test public void resolves17DisplayCapture()throws Exception{
  DisplayCaptureApi a=DisplayCaptureApi.resolve(n->shape(n,false),Wm.class,Object.class,Object[].class);
  assertEquals("android.window.ScreenCaptureInternal",a.name);assertTrue(a.statusCallback);
  assertEquals("captureDisplay",a.capture.getName());
 }
 @Test public void supportsOlderCallback()throws Exception{
  DisplayCaptureApi a=DisplayCaptureApi.resolve(n->{if(n.contains("Internal"))throw new ClassNotFoundException(n);return shape(n,true);},OldWm.class,Object.class,Object[].class);
  assertEquals("android.window.ScreenCapture",a.name);assertFalse(a.statusCallback);
 }
 @Test public void rejectsMissingExclusions()throws Exception{
  try{DisplayCaptureApi.resolve(n->shape(n,false),Wm.class,Object.class,String[].class);fail();}
  catch(ClassNotFoundException e){assertTrue(e.getMessage().contains("setExcludeLayers"));}
 }
 @Test public void rejectsWrongWindowManagerSignature()throws Exception{
  try{DisplayCaptureApi.resolve(n->shape(n,false),OldWm.class,Object.class,Object[].class);fail();}
  catch(ClassNotFoundException e){assertTrue(e.getMessage().contains("captureDisplay"));}
 }
}
