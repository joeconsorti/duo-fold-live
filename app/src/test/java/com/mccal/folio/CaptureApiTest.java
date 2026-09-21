package com.mccal.folio;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureApiTest {
 public static class Args {}
 public static class Builder {
  public Builder(Object surface){}
  public void setSourceCrop(Object crop){}
  public void setFrameScale(float scale){}
  public void setChildrenOnly(boolean children){}
  public void setExcludeLayers(Object[] exclusions){}
  public Args build(){return new Args();}
 }
 public static class MissingExclusions {
  public MissingExclusions(Object surface){}
  public void setSourceCrop(Object crop){}
  public void setFrameScale(float scale){}
  public void setChildrenOnly(boolean children){}
  public Args build(){return new Args();}
 }
 public static class Api {public static Object captureLayers(Args args){return null;}}
 private Class<?> shape(String name,boolean exclusions)throws ClassNotFoundException {
  if(name.endsWith("$Builder"))return exclusions?Builder.class:MissingExclusions.class;
  if(name.endsWith("$LayerCaptureArgs"))return Args.class;
  return Api.class;
 }
 private CaptureApi resolve(CaptureApi.Lookup lookup)throws Exception{
  return CaptureApi.resolve(lookup,CaptureApi.FAMILIES,Object.class,Object.class,Object[].class);
 }
 @Test public void android17UsesInternalEvenWhenScreenCaptureExists()throws Exception{
  CaptureApi api=resolve(n->shape(n,true));
  assertEquals("android.window.ScreenCaptureInternal",api.name);
  assertEquals(Api.class,api.capture.getDeclaringClass());
 }
 @Test public void olderAndroidUsesLegacyFamily()throws Exception{
  CaptureApi api=resolve(n->{if(n.contains("ScreenCaptureInternal"))throw new ClassNotFoundException(n);return shape(n,true);});
  assertEquals("android.window.ScreenCapture",api.name);
 }
 @Test public void missingBuilderFallsBackWithoutMixingTypes()throws Exception{
  CaptureApi api=resolve(n->{if(n.contains("ScreenCaptureInternal")&&n.endsWith("$Builder"))throw new ClassNotFoundException(n);return shape(n,true);});
  assertEquals("android.window.ScreenCapture",api.name);
 }
 @Test public void noCaptureWithoutOverlayExclusions()throws Exception{
  try{resolve(n->shape(n,false));fail("Must reject unsafe API");}
  catch(ClassNotFoundException e){assertTrue(e.getMessage().contains("setExcludeLayers"));assertTrue(e.getMessage().contains("ScreenCaptureInternal"));assertTrue(e.getMessage().contains("android.window.ScreenCapture:"));}
 }
}
