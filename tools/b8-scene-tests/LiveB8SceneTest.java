package org.duofold.live.experimental.b8;
import android.view.SurfaceControl;import java.io.File;
public class LiveB8SceneTest {
 static int checks;static void check(boolean b,String text){checks++;if(!b)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception {
  LiveB8Power power=new LiveB8Power();LiveB8Scene scene=new LiveB8Scene(new File("."),power);
  SurfaceControl a=SurfaceControl.all.get(0),b=SurfaceControl.all.get(1);
  check(!scene.mapped(),"root creation not ready before presentation callback");
  check(!a.visible&&!b.visible,"roots remain hidden until app buffer is submitted");
  SurfaceControl.finish();check(scene.mapped(),"initial mapping completion enables readiness");
  new SurfaceControl.Transaction().setVisibility(a,true).setVisibility(b,true).apply();
  power.rows[0]=null;scene.refresh(power);check(!scene.mapped()&&a.stack==10&&b.stack==20,"missing mapping retains both stacks and invalidates readiness");
  power.rows[0]=new LiveB8Power.Info("inner",20);power.rows[1]=new LiveB8Power.Info("inner",10);scene.refresh(power);
  check(!scene.mapped()&&a.stack==10&&b.stack==20,"duplicate physical ids cannot detach the other panel");
  power.rows[0]=new LiveB8Power.Info("cover",20);power.rows[1]=new LiveB8Power.Info("inner",20);scene.refresh(power);
  check(!scene.mapped()&&a.stack==10&&b.stack==20,"duplicate layer stacks cannot merge coverage");
  power.rows[0]=new LiveB8Power.Info("cover",10);power.rows[1]=new LiveB8Power.Info("inner",20);scene.refresh(power);
  check(!scene.mapped()&&a.stack==20&&b.stack==10,"complete pair submitted together, readiness waits completion");
  int count=SurfaceControl.transactions;scene.refresh(power);scene.refresh(power);check(count==SurfaceControl.transactions,"pending presentation is not resubmitted every poll");
  check(a.visible&&b.visible,"exchange does not hide or detach either moving root");
  SurfaceControl.finish();check(scene.mapped(),"exchange presented");
  power.rows[1]=null;scene.refresh(power);SurfaceControl.finish();check(!scene.mapped(),"old callback cannot certify incomplete observation");
  power.rows[1]=new LiveB8Power.Info("inner",20);scene.refresh(power);check(scene.mapped(),"complete same mapping resumes without layer mutations");
  scene.opacity(.5f);check(a.alpha==.5f&&b.alpha==.5f,"pause dissolve changes both roots together");
  for(SurfaceControl guard:SurfaceControl.all)if(guard.name.startsWith("DuoB8OpaqueGuard"))check(guard.parent==a||guard.parent==b,"opaque guards inherit root dissolve; no black layer left behind");
  scene.opacity(0);check(a.alpha==0&&b.alpha==0,"fully dissolved roots expose native content");
  scene.close();check(!scene.closed()&&!scene.mapped(),"cleanup waits for completion");
  check(a.stack==-1&&b.stack==-1&&!a.visible&&!b.visible,"close detaches both layer stacks before app can submit another buffer");
  for(SurfaceControl guard:SurfaceControl.all)if(guard.name.startsWith("DuoB8OpaqueGuard"))check(!guard.visible&&guard.stack==-1&&guard.parent==null,"guard hidden and detached from every stack before becoming parentless");
  count=SurfaceControl.transactions;scene.close();scene.opacity(1);check(count==SurfaceControl.transactions,"cleanup is idempotent and late opacity cannot reattach coverage");
  SurfaceControl.finish();check(scene.closed()&&a.released&&b.released,"completed cleanup releases owned handles");
  check(android.os.Process.kills==0,"successful cleanup does not kill helper");
  System.out.println("LiveB8SceneTest: "+checks+" mapping, presentation and cleanup checks passed (fake compositor)");
 }
}
