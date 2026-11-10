package android.window;
import android.graphics.Rect;
import android.view.SurfaceControl;
import java.util.function.ObjIntConsumer;
public class ScreenCapture {
 public static class CaptureArgs {
  public long uid=-1;public float scale;public Rect crop;public SurfaceControl[] excluded;
  public static class Builder {
   private final CaptureArgs args=new CaptureArgs();
   public Builder(){}
   public void setUid(long uid){args.uid=uid;}
   public void setSourceCrop(Rect rect){args.crop=rect;}
   public void setFrameScale(float scale){args.scale=scale;}
   public void setExcludeLayers(SurfaceControl[] excluded){args.excluded=excluded;}
   public CaptureArgs build(){return args;}
  }
 }
 public static class ScreenCaptureListener {
  public final ObjIntConsumer<Object> callback;
  public ScreenCaptureListener(ObjIntConsumer<Object> callback){this.callback=callback;}
 }
}
