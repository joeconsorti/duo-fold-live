package android.hardware.devicestate;
public class IDeviceStateManager {
 public static class Info {public DeviceStateManager.State baseState=new DeviceStateManager.State(3,"OPENED"),currentState=new DeviceStateManager.State(DeviceStateManager.current,"");}
 public Info getDeviceStateInfo(){return new Info();}
}
