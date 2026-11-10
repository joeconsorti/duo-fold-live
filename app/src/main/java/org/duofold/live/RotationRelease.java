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
 // Remove the app/sensor override while the temporary panel locks still hold.
 // Thawing under FIXED_TO_USER_ROTATION_ENABLED can keep sensor decisions disabled
 // while asynchronous posture/user-setting callbacks select an intermediate angle.
 // Every phase is still attempted on failure; no phase may strand an owned override.
 static void restore(Step panelLocks,Step postures,Step userRotation,Step fixedPolicy)throws Exception{
  all(java.util.Arrays.asList(fixedPolicy,panelLocks,postures,userRotation));
 }
 static String userRotationTarget(boolean locked,Integer panelRotation,String global){return locked&&panelRotation!=null?Integer.toString(panelRotation):global;}
 static boolean locked(Integer postureMode,boolean original){return postureMode==null||postureMode==0?original:postureMode==1;}
}
