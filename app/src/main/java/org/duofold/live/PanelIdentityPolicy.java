package org.duofold.live;
/** Reject absent identities and stale or incompatible shell observations. */
public final class PanelIdentityPolicy {
 public static boolean known(String id){return id!=null&&!id.isEmpty()&&!id.equals("unknown")&&!id.equals("unavailable")&&!id.equals("null");}
 public static boolean usable(String id,long age,int w,int h,int rotation,int actualW,int actualH,int actualRotation){return known(id)&&age>=0&&age<=250&&w==actualW&&h==actualH&&rotation==actualRotation;}
}
