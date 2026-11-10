package android.view;import android.os.IBinder;
public class SurfaceControl {
 public static volatile java.util.function.LongPredicate accept=id->true;public static volatile int writes;public static volatile java.util.function.LongConsumer onWrite;
 public static final java.util.List<Long> panels=new java.util.concurrent.CopyOnWriteArrayList<>();
 private static final class Token implements IBinder {final long id;Token(long id){this.id=id;}}
 public static long[] getPhysicalDisplayIds(){return new long[]{1,2};}
 public static IBinder getPhysicalDisplayToken(long id){return new Token(id);}
 public static boolean getDisplayBrightnessSupport(IBinder b){return true;}
 public static boolean setDisplayBrightness(IBinder b,float a,float c,float d,float e){long id=((Token)b).id;if(onWrite!=null)onWrite.accept(id);panels.add(id);writes++;return accept.test(id);}
}
