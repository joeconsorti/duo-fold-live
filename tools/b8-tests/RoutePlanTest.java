package org.duofold.live.experimental.b8;
public class RoutePlanTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  for(int flags=0;flags<4;flags++)for(long age=0;age<=30000;age+=1){
   check(RoutePlan.offPanel(age,flags,false,false)==-1);
   int out=RoutePlan.offPanel(age,flags,true,false),back=RoutePlan.offPanel(age,flags,true,true);
   check(out==(((flags&2)!=0&&age>=8000&&age<14000)?0:-1));
   check(back==(((flags&2)!=0&&age>=18000&&age<24000)?1:-1));
   if(age>=14000&&age<=17000)check(out==-1&&back==-1);
  }
  for(int flags:new int[]{-1,15,255}){boolean rejected=false;try{RoutePlan.validate(flags);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
  for(int option:new int[]{11,12,13,14}){RoutePlan.validate(option);check(RoutePlan.promotes(option));for(long age=0;age<=30000;age++)check(RoutePlan.offPanel(age,option,true,false)==-1);}
  RoutePlan.validate(9);RoutePlan.validate(10);check(!RoutePlan.promotes(9));check(RoutePlan.promotes(10));
  for(long age=0;age<=30000;age++){check(RoutePlan.offPanel(age,9,true,false)==-1);check(RoutePlan.offPanel(age,10,true,false)==-1);}
  RoutePlan.validate(4);check(RoutePlan.promotes(4));check(RoutePlan.promotes(5));check(!RoutePlan.promotes(6));
  for(long t=0;t<=30000;t++){check(RoutePlan.offPanel(t,4,true,true)==-1);check(RoutePlan.offPanel(t,5,true,true)==-1);check(RoutePlan.offPanel(t,7,true,false)==-1);check(RoutePlan.offPanel(t,8,true,false)==(t>=8000&&t<14000?0:-1));check(RoutePlan.offPanel(t,8,true,true)==(t>=18000&&t<24000?1:-1));check(RoutePlan.offPanel(t,6,true,false)==(t>=8000&&t<14000?0:-1));check(RoutePlan.offPanel(t,6,true,true)==(t>=18000&&t<24000?1:-1));check(RoutePlan.promotionDue(t,true,false)==(t>=10000&&t<14000));check(!RoutePlan.promotionDue(t,false,false));check(!RoutePlan.promotionDue(t,true,true));}
  System.out.println("RoutePlanTest: timing boundaries, both-ON return gap, failed move and option validation passed");
 }
}
