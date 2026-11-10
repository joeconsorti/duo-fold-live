package org.duofold.live;
/** A/B/A comparison: stable pixels whose value changes only when photo leaves are excluded.
 * Reports contribution in captured composition, not optical visibility or semantic app opacity. */
final class WallpaperPixelEvidence {
 static String analyze(int[] a,int[] b,int[] c){
  if(a.length==0||a.length!=b.length||a.length!=c.length)return "INCONCLUSIVE invalid sample geometry";
  int stable=0,changed=0;
  for(int i=0;i<a.length;i++)if(distance(a[i],c[i])<=6){stable++;if(Math.min(distance(a[i],b[i]),distance(c[i],b[i]))>=24)changed++;}
  if(stable<a.length*.9)return "INCONCLUSIVE scene changed; stable="+stable+"/"+a.length;
  String outcome=changed>=Math.max(8,stable*.01)?"WALLPAPER_CONTRIBUTION":"NOT_OBSERVED_IN_SAMPLE";
  return outcome+" stable="+stable+"/"+a.length+" changed="+changed+" contributionPercent="+String.format(java.util.Locale.US,"%.2f",100.0*changed/stable);
 }
 private static int distance(int a,int b){return Math.max(Math.abs(((a>>16)&255)-((b>>16)&255)),Math.max(Math.abs(((a>>8)&255)-((b>>8)&255)),Math.abs((a&255)-(b&255))));}
}
