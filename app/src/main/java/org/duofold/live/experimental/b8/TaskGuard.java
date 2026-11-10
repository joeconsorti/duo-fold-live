package org.duofold.live.experimental.b8;
final class TaskGuard {
 static boolean activityAlias(Object rootTop,Object taskTop,Object rootReal,Object taskReal,Object rootOrig,Object taskOrig){
  return rootTop!=null&&taskTop!=null&&rootReal!=null&&rootOrig!=null
   &&rootReal.equals(taskReal)&&rootOrig.equals(taskOrig)
   &&((rootTop.equals(rootOrig)&&taskTop.equals(rootReal))
     ||(rootTop.equals(rootReal)&&taskTop.equals(rootOrig)));
 }
 static boolean exclusive(int root,int task,int[] children){
  if(children==null||children.length==0)return root==task;
  return children.length==1&&children[0]==task;
 }
 static boolean destinationVisible(int selectedRoot,int destination,boolean selectedVisible,int[] roots,int[] displays,boolean[] visible,int[] types){
  if(!selectedVisible||roots.length!=displays.length||roots.length!=visible.length||roots.length!=types.length)return false;
  boolean found=false;
  for(int i=0;i<roots.length;i++)if(displays[i]==destination&&visible[i]){
   if(roots[i]==selectedRoot)found=true;
   else if(types[i]==1)return false;
  }
  return found;
 }
 static void validate(String pkg,ExperimentConfig c){
  if(pkg==null||pkg.length()>200||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("Invalid selected package");
  if(c.duration!=30||c.alternate||c.initial==0||c.target!=3||c.backend!=0||c.renewal!=500||c.cancellation!=0)throw new IllegalArgumentException("Task route requires the fixed 30-second recipe");
 }
}
