package org.duofold.live;
/** One read-only capture per motion, with a tail at the endpoint and a hard bound. */
final class HandoffTracePolicy {
 private boolean tracking,blocked;private long started;
 String update(float angle,boolean fresh,boolean allowed,float open,long now){
  if(!allowed||!fresh||!Float.isFinite(angle)){if(tracking){tracking=false;blocked=true;return "STOP: trace host unavailable";}return "";}
  boolean endpoint=angle<=0f||angle>=open;
  if(endpoint){blocked=false;if(tracking){tracking=false;return "ENDPOINT angle="+angle;}return "";}
  if(tracking&&now-started>=30000){tracking=false;blocked=true;return "STOP: 30-second trace limit";}
  if(!tracking&&!blocked){tracking=true;started=now;return "MOTION angle="+angle;}
  return "";
 }
}
