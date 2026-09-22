package org.duofold.live;
/** One-shot, elapsed-time bounded experiment. No Android dependencies. */
final class ContinuityProbePolicy {
 static final int NORMAL=0,HOLD=1,FINISH=2;
 private long consumed,armedAt,started; private boolean armed,closedSeen,holding;
 String status="Continuity test idle";
 boolean holding(){return holding;}
 void abort(){armed=false;holding=false;closedSeen=false;status="Continuity test stopped";}
 int update(long now,long request,float angle,boolean fresh,boolean allowed,boolean coverOwned){
  if(request>consumed){
   consumed=request;
   if(!holding){armed=false;closedSeen=false;if(request<=now && now-request<30000){armed=true;armedAt=request;status="Armed: fully close, then unfold within 30 seconds";}}
  }
  if(holding){
   if(request==0||!allowed||!fresh||!Float.isFinite(angle)||!coverOwned||angle<=0||now-started>=20000){
    holding=false;armed=false;status="Continuity test ended; normal mapping restored (exit flash possible)";return FINISH;
   }
   status="ACTIVE: fixed cover-primary mapping; "+(20000-(now-started))/1000+" seconds remaining";return HOLD;
  }
  if(!armed)return NORMAL;
  if(request==0||!allowed||now-armedAt>=30000){armed=false;status="Continuity test canceled or arm expired";return NORMAL;}
  if(!fresh||!Float.isFinite(angle))return NORMAL;
  if(angle<=0)closedSeen=true;
  if(closedSeen&&angle>0&&angle<80&&coverOwned){holding=true;armed=false;started=now;status="ACTIVE: fixed cover-primary mapping";return HOLD;}
  return NORMAL;
 }
}
