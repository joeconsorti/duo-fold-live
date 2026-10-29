package org.duofold.live;
import android.content.Context;import android.view.SurfaceControl;import android.graphics.HardwareBufferRenderer;import android.hardware.*;import kotlin.Unit;import java.util.concurrent.TimeUnit;
public class B8BufferLayerTest{
 static int checks,presented;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static boolean draw(B8BufferLayer p){return p.draw(c->Unit.INSTANCE,()->{presented++;return Unit.INSTANCE;},e->{throw new AssertionError(e);});}
 public static void main(String[] args)throws Exception{
  SurfaceControl root=new SurfaceControl();B8BufferLayer p=new B8BufferLayer(new Context(),root,100,200,1000,2000);
  check(HardwareBuffer.all.size()==1,"only one buffer allocated before first presentation");
  check(draw(p),"initial GPU frame accepted");check(!root.visible&&presented==0,"unrendered buffer never exposes root or acknowledges coverage");
  HardwareBufferRenderer.finish();check(root.visible&&p.getLayer().buffer!=null&&presented==0,"root visibility and pixels applied together, readiness waits presentation");
  SurfaceControl.present();check(presented==1,"completed presentation acknowledges coverage");
  check(draw(p),"second buffer");HardwareBufferRenderer.finish();SurfaceControl.present();
  check(HardwareBuffer.all.size()==2,"allocate second buffer only while first is compositor-owned");
  check(draw(p),"third buffer");HardwareBufferRenderer.finish();SurfaceControl.present();
  check(HardwareBuffer.all.size()==3,"grow to bounded third slot when both predecessors are owned");
  check(!draw(p),"no reuse before compositor release");
  SyncFence fence=new SyncFence(false);SurfaceControl.releases.remove(0).accept(fence);
  check(fence.waiting.await(1,TimeUnit.SECONDS),"release waits off-main on real fence signal");
  check(!draw(p),"release callback alone does not authorize overwrite");
  fence.signal.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(!fence.closed&&System.nanoTime()<end)Thread.yield();
  boolean accepted=false;while(!(accepted=draw(p))&&System.nanoTime()<end)Thread.yield();check(accepted,"signaled release permits reuse");
  p.close();check(!p.getLayer().visible&&p.getLayer().buffer==null&&p.getLayer().released&&root.released,"close hides, detaches buffer and releases handles");
  int prior=presented;HardwareBufferRenderer.finish();SurfaceControl.present();check(presented==prior,"late GPU/presentation callback cannot revive closed scene");
  check(HardwareBuffer.all.stream().allMatch(b->b.closed),"all local buffers close including in-flight GPU frame");
  check(!draw(p),"closed renderer rejects new submissions");p.close();
  B8BufferLayer early=new B8BufferLayer(new Context(),new SurfaceControl(),100,200,1000,2000);
  early.close();check(HardwareBuffer.all.size()==4&&HardwareBuffer.all.stream().allMatch(b->b.closed),"capture cancelled before first draw releases sole prepared buffer");
  System.out.println("B8BufferLayerTest: "+checks+" compiled production buffer lifecycle checks passed (fake GPU/compositor)");System.exit(0);
 }
}
