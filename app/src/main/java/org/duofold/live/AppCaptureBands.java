package org.duofold.live;

import java.util.function.IntBinaryOperator;

/** Describe the interior of the existing guarded app image before bar or glass composition. */
final class AppCaptureBands {
 private final int[] alpha=new int[3],luma=new int[3],count=new int[3],visible=new int[3];
 boolean hasPixels;
 static AppCaptureBands sample(int width,int height,IntBinaryOperator pixel){
  AppCaptureBands bands=new AppCaptureBands();
  // Preserve the original 8 x 8 inset grid and empty-image criterion.
  for(int y=0;y<8;y++)for(int x=0;x<8;x++){
   int argb=pixel.applyAsInt((int)((.1f+(x+.5f)*.1f)*width),(int)((.1f+(y+.5f)*.1f)*height));
   int band=y<3?0:y<5?1:2;
   int opacity=argb>>>24;bands.count[band]++;bands.alpha[band]+=opacity;
   if(opacity==0)continue;
   bands.hasPixels=true;bands.visible[band]++;
   bands.luma[band]+=(54*(argb>>16&255)+183*(argb>>8&255)+19*(argb&255))>>8;
  }
  return bands;
 }
 // Only compare an incoming app against an opaque reference from this fold.
 // A dark movie or a transparent letterbox can still have an opaque content band.
 boolean opaqueReference(){
  for(int i=0;i<3;i++)if(alpha[i]<240*count[i])return false;
  return true;
 }
 boolean fadedFromOpaque(boolean expectOpaque){
  if(!expectOpaque)return false;
  for(int i=0;i<3;i++)if(alpha[i]>=192*count[i])return false;
  return true;
 }
 String report(){
  return "appGrid top/mid/bottom meanAlpha="+means(alpha,count)
    +" visibleMeanLuma="+means(luma,visible)+" (0-255, -1=no visible samples)";
 }
 private static String means(int[] totals,int[] counts){
  StringBuilder out=new StringBuilder();
  for(int i=0;i<3;i++){if(i>0)out.append('/');out.append(counts[i]==0?-1:totals[i]/counts[i]);}
  return out.toString();
 }
}
