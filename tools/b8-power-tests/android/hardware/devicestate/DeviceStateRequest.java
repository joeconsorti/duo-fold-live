package android.hardware.devicestate;
public class DeviceStateRequest {
 public int id;public interface Callback {void onRequestCanceled(DeviceStateRequest r);}
 public static Builder newBuilder(int id){return new Builder(id);}
 public static class Builder {int id;public Builder(int id){this.id=id;}public DeviceStateRequest build(){DeviceStateRequest r=new DeviceStateRequest();r.id=id;return r;}}
}
