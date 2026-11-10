package org.duofold.live;
import java.util.Locale;
/** Tolerant central-80% RGB comparison, never a claim of optical correctness. */
final class CompositionEvidence {
 static String compare(int[] expected,int[] actual,int width,int height){
  if(width<10||height<10||expected.length!=width*height||actual.length!=expected.length)return "INCONCLUSIVE invalid geometry";
  int count=0,changed=0;long sum=0;
  for(int y=height/10;y<height-height/10;y++)for(int x=width/10;x<width-width/10;x++){
   int a=expected[y*width+x],b=actual[y*width+x];
   int d=Math.max(Math.abs((a>>16&255)-(b>>16&255)),Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a&255)-(b&255))));
   count++;sum+=d;if(d>24)changed++;
  }
  double percent=100.0*changed/count,mean=(double)sum/count;
  return (percent<=1&&mean<=6?"MATCH_WITHIN_TOLERANCE":"PIXEL_DIFFERENCE")+String.format(Locale.US," changedPercent=%.2f meanMaxRGB=%.2f samples=%d",percent,mean,count);
 }
}
