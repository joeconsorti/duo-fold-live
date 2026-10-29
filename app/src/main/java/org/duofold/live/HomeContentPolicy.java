package org.duofold.live;

/** Conservative rejection of missing/faded content in an ISOLATED Home window.
 * Never applied to app frames or opaque whole-display screenshots. This is a
 * content heuristic, not proof that every icon is present or optically visible. */
final class HomeContentPolicy {
 static final class Sample {
  final int count,visible,solid;
  Sample(int count,int visible,int solid){this.count=count;this.visible=visible;this.solid=solid;}
  double coverage(){return count==0?0:solid/(double)count;}
  public String toString(){return "launcherAlpha samples="+count+" visible="+visible+" solid="+solid;}
 }
 static Sample measure(int[] pixels,int width,int height){
  if(width<16||height<16||pixels.length!=width*height)throw new IllegalArgumentException("Invalid Home sample");
  int count=0,visible=0,solid=0;
  // System bars and rounded display edges cannot stand in for launcher content.
  for(int y=height/10;y<height-height/10;y++)for(int x=width/20;x<width-width/20;x++){
   int alpha=pixels[y*width+x]>>>24;count++;if(alpha>=16)visible++;if(alpha>=240)solid++;
  }
  return new Sample(count,visible,solid);
 }
 static String rejection(Sample candidate,Sample source){
  if(candidate==null||candidate.count==0)return "HOME_ALPHA_UNAVAILABLE";
  if(candidate.visible>=8&&candidate.solid<Math.max(1,candidate.visible*.03))return "HOME_CONTENT_FADED";
  // Different panel layouts need not have equal coverage. Reject only a large
  // collapse, including the video's one-icon/empty incoming image. Never promote
  // a rejected frame merely because a timeout elapsed.
  if(source!=null&&source.coverage()>=.01&&candidate.coverage()<Math.max(.002,source.coverage()*.18))return "HOME_CONTENT_MISSING";
  return "";
 }
}
