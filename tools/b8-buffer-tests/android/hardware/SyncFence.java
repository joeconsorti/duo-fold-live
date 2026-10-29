package android.hardware;import java.util.concurrent.*;
public class SyncFence{public volatile boolean closed;public final CountDownLatch signal=new CountDownLatch(1),waiting=new CountDownLatch(1);
 public SyncFence(boolean ready){if(ready)signal.countDown();}public boolean isValid(){return true;}public boolean await(java.time.Duration d){waiting.countDown();try{return signal.await(2,TimeUnit.SECONDS);}catch(Exception e){return false;}}public void close(){closed=true;}}
