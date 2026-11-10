package org.duofold.live;

/** Physical-panel shape survives logical ID swaps. Accept a new orientation
 * only after a stable, powered mapping, never the other panel's transient size. */
final class B8RasterGeometry {
 int width,height,rotation;
 private final int naturalWidth,naturalHeight;
 private int candidateWidth,candidateHeight,candidateRotation=-1;
 private long candidateAt=-1;
 B8RasterGeometry(int w,int h,int r){
  width=w;height=h;rotation=r;
  naturalWidth=(r&1)==0?w:h;naturalHeight=(r&1)==0?h:w;
 }
 boolean observe(boolean poweredMapping,int w,int h,int r,long now){
  if(!poweredMapping||r<0||r>3||w!=((r&1)==0?naturalWidth:naturalHeight)||h!=((r&1)==0?naturalHeight:naturalWidth)){
   candidateAt=-1;return false;
  }
  if(w==width&&h==height&&r==rotation){candidateAt=-1;return false;}
  if(candidateAt<0||w!=candidateWidth||h!=candidateHeight||r!=candidateRotation||now<candidateAt){
   candidateWidth=w;candidateHeight=h;candidateRotation=r;candidateAt=now;return false;
  }
  if(now-candidateAt<75)return false;
  width=w;height=h;rotation=r;candidateAt=-1;return true;
 }
}
