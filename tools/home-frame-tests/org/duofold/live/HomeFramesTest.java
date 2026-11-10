package org.duofold.live;
import android.graphics.*;import android.hardware.HardwareBuffer;import android.os.Handler;import java.util.concurrent.*;import org.duofold.live.wallpaperlayer.HomeCaptureSource;import kotlin.Unit;
public class HomeFramesTest{
 static int checks;static GlassFrame result;static String note;static boolean delivered;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void reset(){LiveAngles.guards.set(0);LiveAngles.validating=new CountDownLatch(1);LiveAngles.release=new CountDownLatch(1);LiveAngles.rejectBefore=false;LiveAngles.rejectAfter=false;Bitmap.copied=new CountDownLatch(1);Bitmap.failCopy=false;Bitmap.all.clear();HomeCaptureSource.windowId++;result=null;note="";delivered=false;}
 static void start(){HomeFrames.INSTANCE.incoming(1,"cover",true,null,(frame,message,retry)->{result=frame;note=message;delivered=true;return Unit.INSTANCE;});}
 static void finish()throws Exception{Runnable r=Handler.queue.poll(2,TimeUnit.SECONDS);check(r!=null,"capture delivers bounded result");r.run();check(delivered,"main delivery completes");}
 public static void main(String[] args)throws Exception{
  reset();start();check(LiveAngles.validating.await(1,TimeUnit.SECONDS),"post-capture validation started");check(Bitmap.copied.await(1,TimeUnit.SECONDS),"pixel build proceeds while validation is blocked");check(Handler.queue.poll(50,TimeUnit.MILLISECONDS)==null,"unverified pixels never delivered");LiveAngles.release.countDown();finish();check(result!=null&&LiveAngles.guards.get()==2,"both guards required for successful frame");check(result.getWidth()==1248&&result.getHeight()==1972&&result.getLevels().size()==7,"physical geometry and full mip chain preserved");check(HardwareBuffer.last.closed,"source hardware buffer closed");check(!HomeCaptureSource.last.wallpaper.recycled,"borrowed wallpaper preserved");
  reset();LiveAngles.rejectAfter=true;start();check(Bitmap.copied.await(1,TimeUnit.SECONDS),"rejected attempt can build pixels in parallel");LiveAngles.release.countDown();finish();check(result==null&&note.contains("guard rejected"),"failed post-guard cannot publish prebuilt pixels");check(Bitmap.all.stream().filter(b->b!=HomeCaptureSource.last.wallpaper).allMatch(b->b.recycled),"failed attempt recycles raw, image, diagnostic layer and mips");
  reset();LiveAngles.rejectBefore=true;int captures=HomeCaptureSource.captures;start();finish();check(result==null&&HomeCaptureSource.captures==captures&&LiveAngles.guards.get()==1,"preflight rejection never requests screenshot");
  reset();Bitmap.failCopy=true;start();check(LiveAngles.validating.await(1,TimeUnit.SECONDS),"failing build still has an owned validator");check(Handler.queue.poll(50,TimeUnit.MILLISECONDS)==null,"failed build drains validator before allowing another attempt");LiveAngles.release.countDown();finish();check(result==null&&note.contains("copy failed"),"build failure retained");check(HardwareBuffer.last.closed,"failed readback releases hardware");
  System.out.println("HomeFramesTest: "+checks+" compiled production capture overlap/validation/cleanup checks passed (fake pixels and Binder)");System.exit(0);
 }
}
