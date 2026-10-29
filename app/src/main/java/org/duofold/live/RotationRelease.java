package org.duofold.live;
import java.util.List;
/** Release is best-effort across every panel, but success requires every operation. */
final class RotationRelease {
 interface Step { void run() throws Exception; }
 static void all(List<Step> steps)throws Exception{
  Exception failure=null;
  for(Step step:steps)try{step.run();}catch(Exception e){if(failure==null)failure=e;else failure.addSuppressed(e);}
  if(failure!=null)throw failure;
 }
 // Restore preferences while fixed-to-user protection is still owned. Release
 // fixed policy last even if an earlier phase fails; recovery must never strand it.
 static void restore(Step panelLocks,Step postures,Step userRotation,Step fixedPolicy)throws Exception{
  all(java.util.Arrays.asList(panelLocks,postures,userRotation,fixedPolicy));
 }
 static boolean locked(Integer postureMode,boolean original){return postureMode==null||postureMode==0?original:postureMode==1;}
}
