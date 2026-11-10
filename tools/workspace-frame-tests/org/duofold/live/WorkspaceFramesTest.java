package org.duofold.live;
import android.graphics.*;import android.hardware.HardwareBuffer;import android.os.Handler;import android.accessibilityservice.AccessibilityService;import java.util.concurrent.*;import org.duofold.live.wallpaperlayer.HomeCaptureSource;import kotlin.Unit;
public class WorkspaceFramesTest{
 static int checks;static GlassFrame result;static String note;static boolean delivered;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void reset(){LiveAngles.guards=0;LiveAngles.rejectBefore=false;LiveAngles.rejectAfter=false;Bitmap.failCopy=false;Bitmap.all.clear();HardwareBuffer.all.clear();Canvas.draws.clear();HomeCaptureSource.generation++;HomeCaptureSource.captures=0;HomeCaptureSource.changed=false;HomeCaptureSource.mismatch=false;HomeCaptureSource.failSecond=false;HomeCaptureSource.lateSecond=false;HomeCaptureSource.panes=2;result=null;note="";delivered=false;}
 static void start(){int[] tasks=HomeCaptureSource.panes==3?new int[]{10,11,12}:new int[]{10,11};WorkspaceFrames.INSTANCE.incoming(tasks,"cover",true,(frame,message,retry)->{result=frame;note=message;delivered=true;return Unit.INSTANCE;});}
 static void finish()throws Exception{Runnable r=Handler.queue.poll(2,TimeUnit.SECONDS);check(r!=null,"bounded main delivery");r.run();check(delivered,"capture finished");}
 static void rejected(String why){check(result==null,why);check(HardwareBuffer.all.stream().allMatch(b->b.closed),"all received hardware buffers released");check(Bitmap.all.stream().filter(b->b!=HomeCaptureSource.last.wallpaper).allMatch(b->b.recycled),"all unpublished bitmaps recycled");check(Canvas.draws.isEmpty(),"rejected panes never composed");}
 public static void main(String[] args)throws Exception{
  reset();start();finish();check(result!=null&&LiveAngles.guards==2,"two panes validated before and after capture");check(result.getDarkSamples()==0,"known non-dark capture does not trigger unknown-content retry");check(result.getLevels().size()==7,"shared blur mip chain preserved");check(Canvas.draws.size()==4&&Canvas.draws.get(0).equals("color")&&Canvas.draws.get(1).startsWith("background")&&Canvas.draws.get(2).startsWith("pane:0.0")&&Canvas.draws.get(3).startsWith("pane:"),"background once followed by each app at its bounds");check(!HomeCaptureSource.last.wallpaper.recycled,"borrowed wallpaper retained");check(HardwareBuffer.all.stream().allMatch(b->b.closed),"success closes hardware buffers");
  reset();HomeCaptureSource.panes=3;start();finish();check(result!=null&&Canvas.draws.size()==5,"three panes all composed over one background");
  reset();LiveAngles.rejectBefore=true;start();finish();check(HomeCaptureSource.captures==0,"failed preflight never captures apps");rejected("preflight rejected");
  reset();LiveAngles.rejectAfter=true;start();finish();rejected("failed postflight rejects every pane");
  reset();HomeCaptureSource.changed=true;start();finish();rejected("window membership change rejects entire group");
  reset();HomeCaptureSource.mismatch=true;start();finish();rejected("full display buffer cannot be squeezed into a pane");
  reset();HomeCaptureSource.failSecond=true;start();finish();rejected("secure/missing second pane cannot publish first alone");
  reset();HomeCaptureSource.lateSecond=true;start();finish();rejected("timeout cannot publish partial workspace");AccessibilityService.ScreenshotResult late=new AccessibilityService.ScreenshotResult(400,1972);HomeCaptureSource.late.onSuccess(late);check(late.getHardwareBuffer().closed,"late callback after timeout releases buffer");
  System.out.println("WorkspaceFramesTest: "+checks+" production capture/composition/guard/timeout cleanup checks passed (fake pixels and Binder)");System.exit(0);
 }
}
