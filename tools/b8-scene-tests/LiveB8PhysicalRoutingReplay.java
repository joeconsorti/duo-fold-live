package org.duofold.live.experimental.b8;

import android.view.SurfaceControl;
import java.io.File;

/** Counterexample replay, NOT a handset/compositor emulator or an optical test.
 * The production scene is unchanged. Physical output routing is independent of
 * the DisplayInfo rows, so both possible publication orders can be exercised.
 */
public class LiveB8PhysicalRoutingReplay {
 static String sample(int physicalStack,int x,int y) {
  for(SurfaceControl root:SurfaceControl.all) {
   if(!root.name.startsWith("DuoB8MovingRoot_")||!root.visible||root.stack!=physicalStack)continue;
   // Model the real renderer's fixed-size, opaque buffer at (0,0), above its guard.
   for(SurfaceControl child:SurfaceControl.all)
    if(child.parent==root&&child.name.startsWith("ReplayBuffer_")&&child.visible
      &&x>=0&&y>=0&&x<child.crop.right&&y<child.crop.bottom)return child.name;
   for(SurfaceControl child:SurfaceControl.all)
    if(child.parent==root&&child.name.startsWith("DuoB8OpaqueGuard_")&&child.visible
      &&x>=0&&y>=0&&x<child.crop.right&&y<child.crop.bottom)return "BLACK_GUARD";
  }
  return "NO_OWNED_COVERAGE";
 }
 static void run(boolean routingAlreadyChanged)throws Exception {
  SurfaceControl.all.clear();SurfaceControl.completions.clear();SurfaceControl.transactions=0;
  LiveB8Power power=new LiveB8Power();
  power.rows[0]=new LiveB8Power.Info("cover",0);
  power.rows[1]=new LiveB8Power.Info("inner",1);
  LiveB8Scene scene=new LiveB8Scene(new File("."),power);
  try {
   SurfaceControl.finish();
   for(int n=0;n<2;n++) {
    SurfaceControl root=SurfaceControl.all.get(n);
    SurfaceControl pixels=new SurfaceControl.Builder().setName("ReplayBuffer_"+(n==0?"cover":"inner")).setParent(root).build();
    new SurfaceControl.Transaction().setVisibility(root,true).setVisibility(pixels,true)
      .setCrop(pixels,new android.graphics.Rect(0,0,power.rows[n].logicalWidth,power.rows[n].logicalHeight)).apply();
   }
   if(!sample(1,1800,900).equals("ReplayBuffer_inner")||!sample(0,600,1900).equals("ReplayBuffer_cover"))
    throw new AssertionError("Initial routing must show the matching buffers");
   // Shape of the mixed row observed at elapsed=524234107 in alpha.82:
   // D0 has the inner identity but the cover geometry; D1 is the cover.
   power.rows[0]=new LiveB8Power.Info("inner",0);
   power.rows[0].logicalWidth=1248;power.rows[0].logicalHeight=1972;
   power.rows[1]=new LiveB8Power.Info("cover",1);
   scene.refresh(power);SurfaceControl.finish();
   int innerStack=routingAlreadyChanged?0:1,coverStack=routingAlreadyChanged?1:0;
   String inner=sample(innerStack,1800,900),cover=sample(coverStack,600,1900);
   System.out.println((routingAlreadyChanged?"ROUTING_CHANGED_METADATA_LAGS":"METADATA_LEADS_ROUTING")
     +" inner="+inner+" cover="+cover+" mapped="+scene.mapped());
   power.rows[0]=new LiveB8Power.Info("inner",0);
   scene.refresh(power);SurfaceControl.finish();
   if(!sample(0,1800,900).equals("ReplayBuffer_inner")||!sample(1,600,1900).equals("ReplayBuffer_cover"))
    throw new AssertionError("Coherent final routing must recover matching buffers");
  } finally {scene.close();SurfaceControl.finish();}
 }
 public static void main(String[] args)throws Exception {
  run(true);run(false);
  System.out.println("Initial and final controls passed. Intermediate results are conditional counterexamples, not measured pixels.");
 }
}
