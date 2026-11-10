package org.duofold.live;
import java.util.*;
/** Receipt observations only. Unknown/duplicate identities break continuity, never prove OFF. */
final class DisplayPowerEvidence {
 private static final class State { int value;long at,off=-1;State(int v,long t){value=v;at=t;} }
 private final Map<String,State> panels=new HashMap<>();
 private long last=-1,maxGap;private int offTransitions,bothOff,unknown;private String pair="";
 synchronized List<String> observe(long at,String a,int av,String b,int bv){
  List<String> out=new ArrayList<>();
  if(at<=last)return out;
  if(last>=0)maxGap=Math.max(maxGap,at-last);last=at;
  if(!PanelIdentityPolicy.known(a)||!PanelIdentityPolicy.known(b)||a.equals(b)||av<1||bv<1){
   panels.clear();unknown++;pair="unknown";return out;
  }
  String[] ids={a,b};int[] values={av,bv};
  for(int i=0;i<2;i++){
   State old=panels.get(ids[i]);int value=values[i];
   if(old==null){panels.put(ids[i],new State(value,at));continue;}
   if(value!=old.value){
    String detail="physical="+ids[i]+" state="+old.value+"->"+value+" previousObserved="+old.at+" observed="+at;
    if(value==1){old.off=at;offTransitions++;detail+=" OFF_OBSERVED";}
    if(value!=1&&old.off>=0){detail+=" offReceiptSpanMs="+(at-old.off)+" exitState="+value+"; intervening changes may be missed";old.off=-1;}
    out.add(detail);old.value=value;
   }
   old.at=at;
  }
  panels.keySet().retainAll(Arrays.asList(a,b));
  String current=av==1&&bv==1?"both OFF":"other";
  if(current.equals("both OFF")&&!current.equals(pair)){bothOff++;out.add("BOTH_PANELS_OFF_OBSERVED at="+at+"; sequential software sample, not optical duration");}
  pair=current;return out;
 }
 synchronized String report(){return "Display power observations: OFF transitions="+offTransitions+" both-OFF episodes="+bothOff+" unknown pairs="+unknown+" maxSampleGapMs="+maxGap+"; process totals, expected idle OFF included; sample/receipt spans are not optical blackout durations";}
}
