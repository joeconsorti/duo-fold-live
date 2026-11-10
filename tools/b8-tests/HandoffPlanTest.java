package org.duofold.live.experimental.b8;
public class HandoffPlanTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  for(int option=0;option<=14;option++)for(long age=0;age<=35000;age++){
   boolean fast=(option==13||option==14)&&((age>=9750&&age<14500)||(option==14&&age>=22750&&age<28500));
   check(HandoffPlan.delay(option,age)==(fast?25:250));
   check(HandoffPlan.prepareReturn(option,age,true,false)==(option==14&&age>=23000&&age<24000));
   check(!HandoffPlan.prepareReturn(option,age,false,false));check(!HandoffPlan.prepareReturn(option,age,true,true));
   check(HandoffPlan.releaseReturn(option,age,false,true)==(option==14&&age>=24000&&age<26000));
   check(!HandoffPlan.releaseReturn(option,age,true,true));check(!HandoffPlan.releaseReturn(option,age,false,false));
   check(HandoffPlan.revealReturn(age,-1)==(age>=28000));
   check(HandoffPlan.revealReturn(age,24500)==(age>=25250));
   check(HandoffPlan.revealReturn(age,27900)==(age>=28000));
  }
  check(!HandoffPlan.stable(1000,-1));check(!HandoffPlan.stable(1299,1000));check(HandoffPlan.stable(1300,1000));
  System.out.println("HandoffPlanTest: baseline250ms unchanged, bounded25ms windows, one-shot committed bridge gates, verified reveal and hard deadline passed");
 }
}
