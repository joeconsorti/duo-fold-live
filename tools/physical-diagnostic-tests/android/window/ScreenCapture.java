package android.window;import android.os.*;import android.graphics.*;import android.hardware.*;import java.util.function.*;public class ScreenCapture{
public static int calls;public static boolean secure,ignoreSize;public static Runnable during;
public static class DisplayCaptureArgs{public int w,h;public static class Builder{private final DisplayCaptureArgs a=new DisplayCaptureArgs();public Builder(IBinder token){}public void setSize(int w,int h){a.w=w;a.h=h;}public DisplayCaptureArgs build(){return a;}}}
public static class ScreenCaptureListener{final Consumer<Object> c;public ScreenCaptureListener(Consumer<Object> c){this.c=c;}}
public static class Shot{private final HardwareBuffer buffer;private final Bitmap image;public Shot(int w,int h){buffer=new HardwareBuffer(w,h);image=new Bitmap(w,h);}public HardwareBuffer getHardwareBuffer(){return buffer;}public boolean containsSecureLayers(){return secure;}public Bitmap asBitmap(){return image;}}
public static int captureDisplay(DisplayCaptureArgs a,ScreenCaptureListener l){calls++;if(during!=null)during.run();l.c.accept(new Shot(ignoreSize?1000:a.w,ignoreSize?1000:a.h));return 0;}}

