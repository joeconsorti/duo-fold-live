package android.view;
import android.window.ScreenCapture;
public class IWindowManager {
 public void captureDisplay(int id,ScreenCapture.CaptureArgs args,ScreenCapture.ScreenCaptureListener listener){listener.callback.accept(new ScreenCapture.Shot(args.crop));}
}
