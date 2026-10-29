package android.view;import android.hardware.*;import android.graphics.Rect;import java.util.*;import java.util.concurrent.Executor;import java.util.function.Consumer;
public class SurfaceControl{
 public static List<Runnable> presentations=new ArrayList<>();public static List<Consumer<SyncFence>> releases=new ArrayList<>();
 public boolean visible,released;public HardwareBuffer buffer;public Consumer<SyncFence> callback;public SurfaceControl parent;
 public void release(){released=true;}public static void present(){List<Runnable> q=new ArrayList<>(presentations);presentations.clear();for(Runnable r:q)r.run();}
 public static class Builder{SurfaceControl p;public Builder setName(String s){return this;}public Builder setParent(SurfaceControl s){p=s;return this;}public Builder setBufferSize(int w,int h){return this;}public SurfaceControl build(){SurfaceControl s=new SurfaceControl();s.parent=p;return s;}}
 public static class TransactionStats{}
 public static class Transaction implements java.io.Closeable{
  List<Runnable> changes=new ArrayList<>(),done=new ArrayList<>();
  public Transaction setBuffer(SurfaceControl s,HardwareBuffer b){return setBuffer(s,b,null,null);}
  public Transaction setBuffer(SurfaceControl s,HardwareBuffer b,SyncFence f,Consumer<SyncFence> cb){changes.add(()->{if(s.callback!=null)releases.add(s.callback);s.buffer=b;s.callback=cb;});return this;}
  public Transaction setVisibility(SurfaceControl s,boolean v){changes.add(()->s.visible=v);return this;}
  public Transaction reparent(SurfaceControl s,SurfaceControl p){changes.add(()->s.parent=p);return this;}
  public Transaction setDataSpace(SurfaceControl s,int n){return this;}public Transaction setCrop(SurfaceControl s,Rect r){return this;}public Transaction setScale(SurfaceControl s,float x,float y){return this;}public Transaction setPosition(SurfaceControl s,float x,float y){return this;}public Transaction setLayer(SurfaceControl s,int z){return this;}public Transaction setOpaque(SurfaceControl s,boolean v){return this;}
  public Transaction addTransactionCompletedListener(Executor e,Consumer<TransactionStats> c){done.add(()->e.execute(()->c.accept(new TransactionStats())));return this;}
  public void apply(){for(Runnable r:changes)r.run();presentations.addAll(done);}public void close(){}
 }
}
