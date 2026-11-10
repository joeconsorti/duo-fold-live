package org.duofold.live;
import java.util.*;
/** Image-only heuristic. Black app content, masks and omitted layers remain valid explanations. */
final class DarkFrameEvidence {
 static final class Sample {
  final int count,opaque,black,leftBlack,rightBlack;final double mean;
  Sample(int n,int o,int b,int l,int r,double m){count=n;opaque=o;black=b;leftBlack=l;rightBlack=r;mean=m;}
  boolean nearBlack(){return count>0&&opaque>=count*.99&&black>=count*.98;}
  public String toString(){return "pixels="+count+" opaque="+opaque+" nearBlackPixels="+black+" leftBlack="+leftBlack+" rightBlack="+rightBlack+" meanRGB="+String.format(Locale.ROOT,"%.2f",mean)+" nearBlack="+nearBlack();}
 }
 static Sample measure(int[] pixels,int w,int h){
  if(w<1||h<1||(long)w*h!=pixels.length)throw new IllegalArgumentException("Invalid image");
  int opaque=0,black=0,left=0,right=0;long rgb=0;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=pixels[y*w+x],a=c>>>24,r=c>>16&255,g=c>>8&255,b=c&255;rgb+=r+g+b;
   if(a>=250){opaque++;if(r<=8&&g<=8&&b<=8){black++;if(x<w/2)left++;else right++;}}
  }
  return new Sample(pixels.length,opaque,black,left,right,rgb/(3.0*pixels.length));
 }
 private static final class Previous {long at,brightAt=-1,darkAt=-1;String geometry="";}
 private final Map<String,Previous> history=new HashMap<>();
 String observe(String physical,String geometry,long at,Sample sample){
  if(!PanelIdentityPolicy.known(physical))return "UNMAPPED";
  if(!history.containsKey(physical)&&history.size()>=4)history.clear();
  Previous p=history.computeIfAbsent(physical,k->new Previous());
  if(at<=p.at)return "OUT_OF_ORDER";
  if(!geometry.equals(p.geometry)||at-p.at>1500){p.brightAt=p.darkAt=-1;}
  String result="CONTENT";
  if(sample.nearBlack()){
   result="NEAR_BLACK_CAPTURE";
   if(p.darkAt<0&&p.brightAt>=0)p.darkAt=at;
  }else{
   // A transparent/omitted capture cannot establish a bright bookend.
   if(sample.opaque>=sample.count*.99&&sample.mean>24){
    if(p.darkAt>=0&&p.brightAt>=0&&at-p.brightAt<=3000)result="BRIGHT_DARK_BRIGHT_CANDIDATE darkAt="+p.darkAt+" previousBright="+p.brightAt+" recoveredAt="+at;
    p.brightAt=at;
   }else p.brightAt=-1;
   p.darkAt=-1;
  }
  p.at=at;p.geometry=geometry;return result;
 }
}
