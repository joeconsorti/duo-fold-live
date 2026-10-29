package android.hardware.display;
import android.os.Handler;
/** No events are emitted: measured geometry must recover without a second callback. */
public class DisplayManager {
 public interface DisplayListener {void onDisplayAdded(int id);void onDisplayChanged(int id);void onDisplayRemoved(int id);}
 public void registerDisplayListener(DisplayListener listener,Handler handler){}
}
