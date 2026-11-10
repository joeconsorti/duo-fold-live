package org.duofold.live.experimental.b8;
public final class TaskGuardTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  check(TaskGuard.exclusive(10,10,null));check(TaskGuard.exclusive(10,10,new int[]{}));
  check(TaskGuard.exclusive(10,12,new int[]{12}));check(!TaskGuard.exclusive(10,12,null));
  check(!TaskGuard.exclusive(10,12,new int[]{12,13}));check(!TaskGuard.exclusive(10,12,new int[]{13}));
  // Captured Chrome shape: same task, alias in root and actual activity in task list.
  check(TaskGuard.activityAlias("Main","Tabbed","Tabbed","Tabbed","Main","Main"));
  check(TaskGuard.activityAlias("Tabbed","Main","Tabbed","Tabbed","Main","Main"));
  check(!TaskGuard.activityAlias("Main","Other","Tabbed","Tabbed","Main","Main"));
  check(!TaskGuard.activityAlias("Main","Tabbed","Tabbed","Other","Main","Main"));
  check(!TaskGuard.activityAlias("Main","Tabbed","Tabbed","Tabbed","Main","Other"));
  check(!TaskGuard.activityAlias("Main","Tabbed",null,null,"Main","Main"));
  check(!TaskGuard.activityAlias(null,"Tabbed","Tabbed","Tabbed","Main","Main"));
  // Focus can remain on the other display: visibility and task identity decide.
  check(TaskGuard.destinationVisible(10,1,true,new int[]{10,20},new int[]{1,0},new boolean[]{true,true},new int[]{1,1}));
  check(!TaskGuard.destinationVisible(10,1,false,new int[]{10},new int[]{1},new boolean[]{true},new int[]{1}));
  check(!TaskGuard.destinationVisible(10,1,true,new int[]{10,20},new int[]{1,1},new boolean[]{true,true},new int[]{1,1}));
  check(!TaskGuard.destinationVisible(10,1,true,new int[]{20},new int[]{1},new boolean[]{true},new int[]{1}));
  check(TaskGuard.destinationVisible(10,1,true,new int[]{10,20},new int[]{1,1},new boolean[]{true,true},new int[]{1,2}));
  ExperimentConfig c=ExperimentConfig.preset(true);c.duration=30;c.alternate=false;c.renewal=500;
  TaskGuard.validate("org.example.test",c);
  for(String bad:new String[]{null,"", "org.example;reboot","org.example\nother"}){boolean rejected=false;try{TaskGuard.validate(bad,c);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
  c.alternate=true;boolean rejected=false;try{TaskGuard.validate("org.example",c);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  System.out.println("TaskGuardTest: exclusive root, package and fixed-recipe guards passed");
 }
}
