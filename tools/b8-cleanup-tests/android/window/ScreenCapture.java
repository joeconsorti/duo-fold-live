package android.window;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
import java.util.function.Consumer;
public class ScreenCapture {
 public static class CaptureArgs {
  public Rect crop;
  public static class Builder {
   final CaptureArgs args=new CaptureArgs();public Builder(){}
   public Builder setSourceCrop(Rect r){args.crop=r;return this;}
   public Builder setFrameScale(float f){return this;}
   public Builder setExcludeLayers(SurfaceControl[] s){return this;}
   public CaptureArgs build(){return args;}
  }
 }
 public static class ScreenCaptureListener {
  public final Consumer<Object> callback;
  public ScreenCaptureListener(Consumer<Object> c){callback=c;}
 }
 public static class Shot {
  private final HardwareBuffer buffer;
  public Shot(Rect r){buffer=new HardwareBuffer(r.width,r.height);}
  public HardwareBuffer getHardwareBuffer(){return buffer;}
  public ColorSpace getColorSpace(){return new ColorSpace();}
  public boolean containsSecureLayers(){return false;}
 }
}
