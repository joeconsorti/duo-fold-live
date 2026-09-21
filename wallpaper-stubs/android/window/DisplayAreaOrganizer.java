package android.window;
import java.util.*;import java.util.concurrent.Executor;import android.view.SurfaceControl;
public class DisplayAreaOrganizer {
 public DisplayAreaOrganizer(Executor executor){}
 public List<DisplayAreaAppearedInfo> registerOrganizer(int feature){throw new UnsupportedOperationException();}
 public void unregisterOrganizer(){}
 public void onDisplayAreaAppeared(DisplayAreaInfo info,SurfaceControl leash){}
 public void onDisplayAreaVanished(DisplayAreaInfo info){}
 public void onDisplayAreaInfoChanged(DisplayAreaInfo info){}
}
