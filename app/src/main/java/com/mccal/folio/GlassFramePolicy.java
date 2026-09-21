package com.mccal.folio;
final class GlassFramePolicy {
 static boolean usable(long stamp,long now,int width,int height,int currentWidth,int currentHeight){
  long age=now-stamp;
  return age>=0 && age<=350 && width>0 && height>0 && width==currentWidth && height==currentHeight;
 }
}
