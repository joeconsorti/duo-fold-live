package org.duofold.live;
/** No cross-panel substitution or geometry scaling in any experiment. */
final class SystemBarExperimentPolicy {
 static final String KEY="system_bar_experiment";
 static final int OFF=0,NATIVE=1,DESTINATION=2,CACHE=3;
 static int mode(int value){return value>=OFF&&value<=CACHE?value:OFF;}
 static boolean matches(String target,int w,int h,int rotation,String physical,int sw,int sh,int sr){
  return PanelIdentityPolicy.known(target)&&target.equals(physical)&&w==sw&&h==sh&&rotation==sr;
 }
 static boolean fresh(long now,long stamp,long ttl){return stamp>0&&now>=stamp&&now-stamp<=ttl;}
 static String edge(int[] b,int w,int h){
  if(b[1]==0&&b[3]-b[1]<=h/4)return "top";
  if(b[1]>=h-h/4)return "bottom";
  return b[0]==0?"left":"right";
 }
 static boolean keepCached(int[] bounds,int w,int h,long stamp,long now,int[][] fresh){
  if(!fresh(now,stamp,300_000))return false;
  for(int[] b:fresh)if(edge(bounds,w,h).equals(edge(b,w,h)))return false;
  return true;
 }
 static boolean keepSession(int[] bounds,long stamp,long now,int[][] fresh){
  if(!fresh(now,stamp,30_000))return false;
  // A newly available gesture handle does not cover the apps above it. Draw
  // retained current-session pixels first and newer pixels over their overlap.
  for(int[] b:fresh)if(b[0]<=bounds[0]&&b[1]<=bounds[1]&&b[2]>=bounds[2]&&b[3]>=bounds[3])return false;
  return true;
 }
 // Crop the whole owned scene (including its black guard), not just its pixels.
 // Only full-span bounded edges are eligible; never guess taskbar height.
 static int[] contentCrop(int w,int h,int[][] regions){
  int l=0,t=0,r=w,b=h;
  for(int[] v:regions){
   if(v==null||v.length!=4||v[0]<0||v[1]<0||v[2]>w||v[3]>h||v[2]<=v[0]||v[3]<=v[1])continue;
   if(v[0]==0&&v[2]==w&&v[1]==0&&v[3]<=h/4)t=Math.max(t,v[3]);
   else if(v[0]==0&&v[2]==w&&v[3]==h&&h-v[1]<=h/4)b=Math.min(b,v[1]);
   else if(v[1]==0&&v[3]==h&&v[0]==0&&v[2]<=w/8)l=Math.max(l,v[2]);
   else if(v[1]==0&&v[3]==h&&v[2]==w&&w-v[0]<=w/8)r=Math.min(r,v[0]);
  }
  return new int[]{l,t,r,b};
 }
}
