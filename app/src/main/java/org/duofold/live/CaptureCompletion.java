package org.duofold.live;
import java.util.concurrent.*;
import java.util.function.Consumer;
/** Owns exactly one async result, including cleanup after timeout or cancellation. */
final class CaptureCompletion<T> implements AutoCloseable {
 private final CountDownLatch ready=new CountDownLatch(1);private final Consumer<T> dispose;
 private boolean closed,received;private T value;private int status;
 CaptureCompletion(Consumer<T> dispose){this.dispose=dispose;}
 synchronized void accept(T item,int code){
  if(closed||received){if(item!=null)dispose.accept(item);return;}
  received=true;value=item;status=code;ready.countDown();
 }
 T await(long ms)throws Exception{
  if(!ready.await(ms,TimeUnit.MILLISECONDS))throw new TimeoutException("WindowManager capture callback timed out after "+ms+" ms");
  synchronized(this){
   if(status!=0)throw new IllegalStateException("WindowManager capture status="+status);
   if(value==null)throw new IllegalStateException("WindowManager capture callback returned an empty buffer (status=0)");
   T result=value;value=null;closed=true;return result;
  }
 }
 public synchronized void close(){closed=true;if(value!=null){dispose.accept(value);value=null;}}
}
