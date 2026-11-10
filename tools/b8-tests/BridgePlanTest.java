package org.duofold.live.experimental.b8;
public class BridgePlanTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  for(int option=0;option<=14;option++)check(BridgePlan.enabled(option)==(option>=9&&option<=14));
  for(long age=0;age<=30000;age++){
   check(BridgePlan.prepare(age,true,false)==(age>=8000&&age<10000));
   check(!BridgePlan.prepare(age,false,false));check(!BridgePlan.prepare(age,true,true));
   check(BridgePlan.remove(age,-1)==(age>=14000));
   check(BridgePlan.remove(age,10500)==(age>=11250));
   check(BridgePlan.remove(age,13900)==(age>=14000));
  }
  for(int option=0;option<=14;option++){
   check(BridgePlan.stagedReveal(option)==(option>=11&&option<=14));
   for(long age=0;age<=30000;age++){
    check(BridgePlan.recoveryDue(option,age,true,true,false)==(option==12&&age>=13000&&age<16000));
    check(!BridgePlan.recoveryDue(option,age,false,true,false));
    check(!BridgePlan.recoveryDue(option,age,true,false,false));
    check(!BridgePlan.recoveryDue(option,age,true,true,true));
   }
  }
  for(int bits=0;bits<8;bits++)check(BridgePlan.canPromote((bits&1)!=0,(bits&2)!=0,(bits&4)!=0)==(bits==3));
  System.out.println("BridgePlanTest: preparation window, one-shot guard, commit/closed promotion gate, settle and hard removal deadline passed");
 }
}
