package org.duofold.live;
import android.content.Context;import android.view.SurfaceControl;import android.graphics.HardwareBufferRenderer;import android.hardware.*;import kotlin.Unit;import java.util.concurrent.TimeUnit;
public class B8BufferLayerTest{
 static int checks,presented;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static boolean draw(B8BufferLayer p){return p.draw(c->Unit.INSTANCE,()->{presented++;return Unit.INSTANCE;},e->{throw new AssertionError(e);},()->null,()->true,()->Unit.INSTANCE);}
 public static void main(String[] args)throws Exception{
  // Production callbacks: latch may pace another GPU frame while presentation
  // is still pending. It must not acknowledge pixels or free the first buffer.
  B8BufferLayer paced=new B8BufferLayer(new Context(),new SurfaceControl(),100,200,100,200);
  int[] latches={0},completions={0};
  check(paced.draw(c->Unit.INSTANCE,()->{completions[0]++;return Unit.INSTANCE;},e->{throw new AssertionError(e);},()->null,()->true,()->{latches[0]++;return Unit.INSTANCE;}),"first paced draw");
  HardwareBufferRenderer.finish();SurfaceControl.latch();
  check(latches[0]==1&&completions[0]==0,"latch precedes presentation and supplies no presentation evidence");
  HardwareBuffer first=paced.getLayer().buffer;
  check(draw(paced),"next GPU frame can begin before previous completion");HardwareBufferRenderer.finish();
  check(paced.getLayer().buffer!=first&&!first.closed,"overlap uses a separate buffer and retains unreleased predecessor");
  paced.close();SurfaceControl.present();
  check(completions[0]==0&&latches[0]==1,"close suppresses delayed latch and completion");
  SurfaceControl.releases.clear();HardwareBuffer.all.clear();presented=0;
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
  // A second signaled fence must free its slot while the first remains blocked.
  SyncFence fast=new SyncFence(true);SurfaceControl.releases.remove(0).accept(fast);
  long fastEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(!fast.closed&&System.nanoTime()<fastEnd)Thread.yield();
  check(fast.closed&&!fence.closed,"slow first fence does not block another buffer release");
  fence.signal.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(!fence.closed&&System.nanoTime()<end)Thread.yield();
  boolean accepted=false;while(!(accepted=draw(p))&&System.nanoTime()<end)Thread.yield();check(accepted,"signaled release permits reuse");
  p.close();check(!p.getLayer().visible&&p.getLayer().buffer==null&&p.getLayer().released&&root.released,"close hides, detaches buffer and releases handles");
  int prior=presented;HardwareBufferRenderer.finish();SurfaceControl.present();check(presented==prior,"late GPU/presentation callback cannot revive closed scene");
  check(HardwareBuffer.all.stream().allMatch(b->b.closed),"all local buffers close including in-flight GPU frame");
  check(!draw(p),"closed renderer rejects new submissions");p.close();
  B8BufferLayer early=new B8BufferLayer(new Context(),new SurfaceControl(),100,200,1000,2000);
  early.close();check(HardwareBuffer.all.size()==4&&HardwareBuffer.all.stream().allMatch(b->b.closed),"capture cancelled before first draw releases sole prepared buffer");
  SurfaceControl.releases.clear();
  B8BufferLayer retired=new B8BufferLayer(new Context(),new SurfaceControl(),100,200,1000,2000);
  for(int n=0;n<3;n++){check(draw(retired),"allocate live slot");HardwareBufferRenderer.finish();SurfaceControl.present();}
  int allocated=HardwareBuffer.all.size();SyncFence never=new SyncFence(false);SurfaceControl.releases.remove(0).accept(never);
  long timeoutEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!never.closed&&System.nanoTime()<timeoutEnd)Thread.yield();
  check(never.closed,"unsignaled fence has bounded retirement");
  boolean replaced=false;while(!(replaced=draw(retired))&&System.nanoTime()<timeoutEnd)Thread.yield();
  check(replaced&&HardwareBuffer.all.size()==allocated+1,"retired slot is replaced, not reused or permanently counted against capacity");
  retired.close();HardwareBufferRenderer.finish();SurfaceControl.present();
  check(HardwareBuffer.all.stream().allMatch(b->b.closed),"replacement and in-flight close release local ownership");
  SurfaceControl changedRoot=new SurfaceControl();
  B8BufferLayer changed=new B8BufferLayer(new Context(),changedRoot,100,200,1000,2000);
  boolean[] valid={true};
  check(changed.draw(c->Unit.INSTANCE,()->{throw new AssertionError("obsolete presentation");},e->{throw new AssertionError(e);},()->null,()->valid[0],()->Unit.INSTANCE),"queue while panel matches");
  valid[0]=false;HardwareBufferRenderer.finish();SurfaceControl.present();
  check(!changedRoot.visible&&changed.getLayer().buffer==null,"in-flight geometry change cannot expose an obsolete buffer");
  check(draw(changed),"fresh frame can use replacement slot");HardwareBufferRenderer.finish();SurfaceControl.present();
  check(changedRoot.visible&&changed.getLayer().buffer!=null,"valid geometry resumes presentation");changed.close();
  SurfaceControl.releases.clear();SurfaceControl resizeRoot=new SurfaceControl();
  B8BufferLayer resizing=new B8BufferLayer(new Context(),resizeRoot,462,612,1848,2448);
  check(draw(resizing),"initial portrait frame");HardwareBufferRenderer.finish();SurfaceControl.present();
  HardwareBuffer portrait=resizing.getLayer().buffer;
  resizing.resize(612,462,2448,1848);
  check(resizeRoot.visible&&resizing.getLayer().buffer==portrait&&!portrait.closed,"resize retains displayed buffer and its ownership");
  check(draw(resizing),"new landscape geometry can render while old buffer is held");
  check(resizing.getLayer().buffer==portrait,"old pixels retained during asynchronous GPU draw");
  HardwareBufferRenderer.finish();SurfaceControl.present();
  HardwareBuffer landscape=resizing.getLayer().buffer;
  check(landscape!=portrait&&landscape.width==612&&landscape.height==462,"new matching raster replaces old pixels on same visible layer");
  resizing.resize(462,612,1848,2448);check(draw(resizing),"second rotation uses last slot");HardwareBufferRenderer.finish();SurfaceControl.present();
  resizing.resize(612,462,2448,1848);check(!draw(resizing),"rotation churn cannot exceed three unreleased buffers");
  SyncFence rotationFence=new SyncFence(true);SurfaceControl.releases.remove(0).accept(rotationFence);
  long rotationEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
  while(!rotationFence.closed&&System.nanoTime()<rotationEnd)Thread.yield();
  boolean resized=false;while(!(resized=draw(resizing))&&System.nanoTime()<rotationEnd)Thread.yield();
  check(resized&&portrait.closed,"only released wrong-size slot is retired and replaced");
  resizing.close();HardwareBufferRenderer.finish();SurfaceControl.present();
  check(HardwareBuffer.all.stream().allMatch(b->b.closed),"resize cleanup includes old-size and in-flight slots");
  System.out.println("B8BufferLayerTest: "+checks+" compiled production buffer lifecycle checks passed (fake GPU/compositor)");System.exit(0);
 }
}
