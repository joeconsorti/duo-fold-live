package org.duofold.live;
import android.graphics.*;
import android.view.SurfaceControl;
import android.hardware.HardwareBuffer;
import android.window.ScreenCapture.*;
import java.util.*;

public class HomeNativeCaptureTest {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static class Shot {
  final Bitmap bitmap;final HardwareBuffer buffer;final boolean secure;
  Shot(Bitmap b,boolean secure){bitmap=b;buffer=new HardwareBuffer(b.width,b.height);this.secure=secure;}
  public HardwareBuffer getHardwareBuffer(){return buffer;}
  public boolean containsSecureLayers(){return secure;}
  int readbacks;
  public Bitmap asBitmap(){readbacks++;return bitmap;}
 }
 public static class Wm {
  Shot sceneShot;
  final int mode;final List<CaptureArgs> requests=new ArrayList<>();final List<ScreenCaptureListener> callbacks=new ArrayList<>();
  Wm(int mode){this.mode=mode;}
  public void captureDisplay(int display,CaptureArgs args,ScreenCaptureListener callback){
   check(display==0,"pair stays on guarded primary");requests.add(args);callbacks.add(callback);
   // Neither callback is delivered until both requests have been dispatched.
   if(requests.size()!=2)return;
   if(mode==1){callbacks.get(0).callback.accept(shot(0),0);throw new IllegalStateException("second submission failed");}
   if(mode!=9){sceneShot=shot(1);callbacks.get(1).callback.accept(sceneShot,mode==2?1:0);}
   if(mode!=3)callbacks.get(0).callback.accept(shot(0),0);
  }
  Shot shot(int which){
   long uid=requests.get(which).uid;Bitmap bitmap;
   if(uid==12000)bitmap=HomeNativeCompositionTest.icons(mode==5?80:255);
   else if(uid==10001)bitmap=HomeNativeCompositionTest.image(0xff0000ff); // registered live wallpaper
   else if(uid==-1){ // actual composed scene includes photo owned by another UID
    Bitmap icons=HomeNativeCompositionTest.icons(mode==7?100:255);
    bitmap=mode==6?HomeNativeCompositionTest.image(0):mode==10?new Bitmap(63,64):HomeNativeCompositionTest.scene(0xff00ff00,icons);icons.recycle();
   }else throw new AssertionError("Unexpected owner "+uid);
   return new Shot(bitmap,(which==0&&mode==4)||(which==1&&mode==8));
  }
 }
 public static void main(String[] args)throws Exception{
  SurfaceControl[] exclusions={new SurfaceControl()};
  for(int mode=0;mode<=10;mode++){
   Wm wm=new Wm(mode);int buffers=HardwareBuffer.all.size(),bitmaps=Bitmap.all.size();Bitmap published=null;
   try{
    HomeNativeComposition.Result r=HomeNativeCapture.capture(wm,Wm.class,0,1248,1972,exclusions,12000,null);
    check(mode==0,"failing pair must not publish partial result");published=r.bitmap;
    check(published.getPixel(25,25)==0xffff0000,"pair preserves actual launcher icons");
    check(published.getPixel(5,5)==0xff00ff00,"cross-owner replacement photo survives; wallpaper-owner filtering would wrongly return blue");
   }catch(Exception expected){check(mode!=0,"normal pair succeeds: "+expected);}
   check(wm.requests.size()==2,"both requests submitted before either wait");
   check(wm.requests.get(0).uid==12000&&wm.requests.get(1).uid==-1,"owner filter only on validation probe, never on returned Home scene");
   for(CaptureArgs request:wm.requests){check(request.excluded==exclusions,"both requests retain Duo exclusions");check(request.crop.right==1248&&request.crop.bottom==1972,"both requests use identical physical geometry");check(Math.abs(request.scale-1440f/1972)<.00001f,"same bounded raster");}
   if(mode==5)check(wm.sceneShot.readbacks==0&&wm.sceneShot.buffer.closed,"faded launcher skips the full scene readback and releases the pending shot");
   if(mode==0)check(wm.sceneShot.readbacks==1,"accepted scene is read once");
   if(mode==3||mode==9){int which=mode==3?0:1;Shot late=wm.shot(which);wm.callbacks.get(which).callback.accept(late,0);check(late.buffer.closed,"late callback after timeout releases its buffer");}
   for(int i=buffers;i<HardwareBuffer.all.size();i++)check(HardwareBuffer.all.get(i).closed,"every callback buffer closed on success/failure/timeout");
   // Hardware wrappers for never-consumed shots are fake-owned. Their buffers
   // above must close; every CPU copy/sample is covered by composition tests.
   if(published!=null){check(!published.recycled,"successful output remains alive for caller");published.recycle();}
  }
  System.out.println("HomeNativeCaptureTest: "+checks+" paired request, owner selection, failure, late callback and hardware-buffer checks passed (fake platform)");
 }
}
