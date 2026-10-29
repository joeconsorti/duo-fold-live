package android.view;
import java.util.concurrent.Executor;
import android.hardware.HardwareBuffer;
public class SurfaceControl {
 public int stack=-1;public boolean released,skipScreenshot,shown;public boolean isValid(){return !released;}public void release(){released=true;}
 public static class Builder {
  public Builder(){} public Builder setName(String n){return this;} public Builder setContainerLayer(){return this;}
  public Builder setHidden(boolean h){return this;} public Builder setBufferSize(int w,int h){return this;}
  public SurfaceControl build(){return new SurfaceControl();}
 }
 public interface TransactionCommittedListener {void onTransactionCommitted();}
 public static class Transaction {
  public static Runnable commit;public static int removals,clears,hides,applies;public static boolean reject;
  public Transaction(){} public Transaction setAlpha(SurfaceControl s,float a){return this;}public Transaction hide(SurfaceControl s){hides++;s.shown=false;return this;}public Transaction show(SurfaceControl s){s.shown=true;return this;}
  public Transaction setLayer(SurfaceControl s,int z){return this;} public Transaction reparent(SurfaceControl s,SurfaceControl p){return this;}
  public Transaction setColorSpace(SurfaceControl s,android.graphics.ColorSpace c){return this;}
  public Transaction setLayerStack(SurfaceControl s,int stack){s.stack=stack;return this;}
  public Transaction setSkipScreenshot(SurfaceControl s,boolean skip){s.skipScreenshot=skip;return this;}
  public Transaction remove(SurfaceControl s){removals++;return this;}
  public Transaction setBuffer(SurfaceControl s,HardwareBuffer b){if(b==null)clears++;return this;}
  public Transaction addTransactionCommittedListener(Executor e,TransactionCommittedListener l){commit=()->e.execute(l::onTransactionCommitted);return this;}
  public void apply(){if(reject)throw new IllegalStateException("test rejected");applies++;}public void close(){}
 }
}
