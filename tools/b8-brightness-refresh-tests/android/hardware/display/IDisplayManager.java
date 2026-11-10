package android.hardware.display;
public interface IDisplayManager {
 Object getDisplayInfo(int id);
 Object getBrightnessInfo(int id);
 void setTemporaryBrightness(int id,float value);
 final class Stub {public static final int TRANSACTION_setTemporaryBrightness=42;}
}
