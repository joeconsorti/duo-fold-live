package android.hardware.devicestate;
import java.util.*;import java.util.concurrent.Executor;
public class DeviceStateManager {
 public static int current=3,cancels;public static DeviceStateRequest request;public static DeviceStateRequest.Callback callback;
 public static class State {int id;String name;public State(int id,String name){this.id=id;this.name=name;}public int getIdentifier(){return id;}public String getName(){return name;}}
 public List<State> getSupportedDeviceStates(){return Arrays.asList(new State(4,"CONCURRENT_INNER_DEFAULT"),new State(5,"CONCURRENT_OUTER_DEFAULT"));}
 public void requestState(DeviceStateRequest r,Executor ex,DeviceStateRequest.Callback c){request=r;callback=c;current=r.id;}
 public void cancelStateRequest(){cancels++;current=3;}
}
