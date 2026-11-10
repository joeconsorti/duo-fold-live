package org.duofold.live.experimental.b8;
/** Pure timing plan; fixed mapping and a both-ON interval before every move. */
final class RoutePlan {
 static void validate(int options){if(options<0||options>14)throw new IllegalArgumentException("Unknown route options");}
 static boolean promotes(int options){validate(options);return options==4||options==5||options==10||options==11||options==12||options==13||options==14;}
 static boolean promotionDue(long age,boolean outbound,boolean attempted){return outbound&&!attempted&&age>=10000&&age<14000;}
 static int offPanel(long age,int options,boolean outbound,boolean returned){
  validate(options);if(options!=2&&options!=3&&options!=6&&options!=8)return -1;
  if(outbound&&!returned&&age>=8000&&age<14000)return 0;
  if(returned&&age>=18000&&age<24000)return 1;
  return -1;
 }
}
