package org.duofold.live

/** Classic projection with a bounded cover stretch and inward inner-panel fold. */
internal object ClassicGlassShader {
 val source="""
 uniform shader content;
 uniform shader mip1; uniform shader mip2; uniform shader mip3; uniform shader mip4; uniform shader mip5;
 uniform float aaStrength;
 half3 sampleLevel(float2 p,float lod){
  // A bounded trilinear prefilter reuses existing mip levels, including at zero blur.
  lod=max(lod,aaStrength);
  if(lod<1.0)return mix(content.eval(p).rgb,mip1.eval(p).rgb,half(lod));
  if(lod<2.0)return mix(mip1.eval(p).rgb,mip2.eval(p).rgb,half(lod-1.0));
  if(lod<3.0)return mix(mip2.eval(p).rgb,mip3.eval(p).rgb,half(lod-2.0));
  if(lod<4.0)return mix(mip3.eval(p).rgb,mip4.eval(p).rgb,half(lod-3.0));
  return mix(mip4.eval(p).rgb,mip5.eval(p).rgb,half(clamp(lod-4.0,0.0,1.0)));
 }
 uniform float2 texSize;
 uniform float2 origin;
 uniform float2 extent;
 uniform float2 sampleScale;
 uniform float2 sampleOffset;
 uniform float2 motionOffset;
 uniform float progress;
 uniform float classicPreset;
 uniform float verticalCompression;
 uniform float foldRadians;
 uniform float opaqueContent;
 uniform float inner;
 uniform float fallback;
 uniform float horizontal;
 uniform float reverse;
 uniform float intensity;
 uniform float blurStrength;
 uniform float seamOffset;
 uniform float reflectedCover;
 uniform float windowReveal;
 uniform float earlyStretch;
 uniform float endStretch;
 uniform float startupEasing;
 float coverAt(float x,float t){
  return x*cos(t)*40.0/max(1.0,40.0-x*15.7987*sin(t));
 }
 float earlyCover(float x,float t,float original){
  float joinAngle=1.5;
  if(t>=joinAngle)return original;
  float end=coverAt(x,joinAngle),distance=max(0.0,x-end);
  float endSlope=-(coverAt(x,joinAngle+0.0001)-coverAt(x,joinAngle-0.0001))/0.0002*joinAngle;
  // Slider preserves the original 0–100% scale and extrapolates to 300%.
  float startSlope=max(0.0,3.0*distance-endSlope)*mix(0.05,0.9,clamp(earlyStretch,0.0,3.0));
  float u=clamp(t/joinAngle,0.0,1.0),u2=u*u,u3=u2*u;
  float stretch=(u3-2.0*u2+u)*startSlope+(-2.0*u3+3.0*u2)*distance+(u3-u2)*endSlope;
  return x-stretch;
 }

 float innerAt(float x,float t,float span){return coverAt(x*span,t)/span;}
 float earlyInner(float x,float t,float original,float span){
  float joinAngle=1.5;
  if(t>=joinAngle)return original;
  float end=innerAt(x,joinAngle,span),distance=max(0.0,x-end);
  float endSlope=-(innerAt(x,joinAngle+0.0001,span)-innerAt(x,joinAngle-0.0001,span))/0.0002*joinAngle;
  // Slider preserves the original 0–100% scale and extrapolates to 300%.
  float startSlope=max(0.0,3.0*distance-endSlope)*mix(0.05,0.9,clamp(earlyStretch,0.0,3.0));
  float u=clamp(t/joinAngle,0.0,1.0),u2=u*u,u3=u2*u;
  float stretch=(u3-2.0*u2+u)*startSlope+(-2.0*u3+3.0*u2)*distance+(u3-u2)*endSlope;
  return x-stretch;
 }

 // Zero slope at both ends avoids the old late stretch surge.
 float classicCoverScale(float p){return 1.0+0.28*smoothstep(0.0,1.0,p);}
 float classicInnerWidth(float p){return mix(1.0,0.16,smoothstep(0.0,1.0,p));}
 ${MotionGlassShader.source}
 half4 main(float2 p) {
  float2 uv=(p-origin)/extent;
  if(any(lessThan(uv,float2(0))) || any(greaterThan(uv,float2(1)))) return half4(0);
  uv=motionWarp(uv);
  float axis=mix(uv.x,uv.y,horizontal);
  axis=mix(axis,1.0-axis,reverse);
  float edge=inner>0.5 ? (fallback>0.5 ? 1.0-axis : 1.0-2.0*axis) : axis;
  if(inner>0.5 && fallback<0.5 && axis>=0.5+seamOffset) return half4(0);
  float motion=smoothstep(0.0,1.0,progress);
  float a=min(progress,0.97)*1.570796327;
  // Fixed eye at z=40, intersect projected folded strip with the flat UI plane.
  float hinge=inner>0.5 && fallback<0.5 ? 0.5 : (inner>0.5 ? 1.0 : 0.0);
  float local=(axis-hinge)*15.7987;
  float z=abs(local)*sin(a);
  float depth=40.0/max(1.0,40.0-z);
  float projected=hinge+local*cos(a)*depth/15.7987;
  float corrected=mix(projected,1.0-projected,reverse);
  float2 sourceUV=uv;
  if(horizontal>0.5) sourceUV.y=corrected; else sourceUV.x=corrected;
  // Advance cover stretch continuously; keep the existing inner correction.
  // Original coordinates pass through beyond the early-curve join.
  if(windowReveal>0.5){
   float limited=inner<0.5 ? earlyCover(axis,a,projected) : (axis<hinge ? max(projected,axis) : min(projected,axis));
   if(inner<0.5)limited/=mix(1.0,clamp(endStretch,0.8,1.5)/0.8,smoothstep(0.523598776,1.047197551,a));
   if(inner>0.5){
    float innerHinge=fallback>0.5 ? 1.0 : 0.5;
    if(axis<innerHinge){
     float x=(innerHinge-axis)/innerHinge;
     float distance=earlyInner(x,a,innerAt(x,a,innerHinge),innerHinge);
     // Low settings reduce the displacement from flat; they must never
     // divide the source distance by <1 and introduce inward compression.
     if(endStretch<0.8)distance=mix(x,clamp(distance,0.0,x),clamp(endStretch/0.8,0.0,1.0));
     else distance/=mix(1.0,clamp(endStretch,0.8,1.5)/0.8,smoothstep(0.523598776,1.047197551,a));
     limited=innerHinge-innerHinge*distance;
    }else limited=axis;
   }
   float correctedV2=mix(limited,1.0-limited,reverse);
   if(horizontal>0.5)sourceUV.y=correctedV2;else sourceUV.x=correctedV2;
  }
  // Destination-to-source mapping: expanding source distance compresses the
  // visible inner image TOWARD the hinge; opening traverses the same curve backward.
  // Keep the cover bounded instead of the near-singular 90-degree stretch.
  if(classicPreset>0.5){
   float h=inner>0.5 ? (fallback>0.5 ? 1.0 : 0.5) : 0.0;
   float mappedAxis=inner>0.5
    ? h+(axis-h)/classicInnerWidth(progress)
    : axis/classicCoverScale(progress);
   float correctedClassic=mix(mappedAxis,1.0-mappedAxis,reverse);
   if(horizontal>0.5)sourceUV.y=correctedClassic;else sourceUV.x=correctedClassic;
  }
  // Use Windowed Glass's cross-axis perspective, including the fixed cover
  // edge and rotation handling. Keep Classic's horizontal mapping above intact.
  if(classicPreset>0.5){
   float across=mix(uv.y,uv.x,horizontal);
   float c=cos(foldRadians), sn=sin(foldRadians);
   float panelWidth=inner>0.5 ? 15.7987 : 7.73936;
   float px=inner>0.5 ? (-7.89935+axis*panelWidth) : (-0.23396-axis*panelWidth);
   if(inner>0.5 && fallback>0.5)px=(axis-1.0)*7.89935;
   float py=(1.0-across)*11.1035+0.34562-5.8974;
   float pz=inner>0.5 ? 0.24948 : -0.27463;
   if(inner<0.5 || px<0.0){float z=pz-0.275454;pz=-sn*px+c*z+0.275454;}
   float projectedY=py*(0.24948-40.0)/(pz-40.0);
   if(inner<0.5){
    float foldedZ=-sn*(-0.23396)+c*(-0.27463-0.275454)+0.275454;
    projectedY/=(0.24948-40.0)/(foldedZ-40.0);
   }
   float projectedAcross=1.0-(projectedY-(0.34562-5.8974))/11.1035;
   if(horizontal>0.5)sourceUV.x=projectedAcross;else sourceUV.y=projectedAcross;
  }
  float seamMask=0.0;
  if(inner>0.5 && fallback<0.5 && seamOffset>0.0){
   seamMask=1.0-smoothstep(max(0.5,0.5+seamOffset-0.05),0.5+seamOffset,axis);
   if(axis>=0.5)sourceUV=uv;
  }
  // Cover startup: reach opacity before separating the captured image from
  // the live image below it. Smooth geometry only within the first few degrees.
  if(windowReveal>0.5 && startupEasing>0.5)sourceUV=mix(uv,sourceUV,smoothstep(0.015,0.05,progress));
  if(classicPreset>0.5 && verticalCompression!=1.0){
   float compression=clamp(verticalCompression,0.0,2.0);
   if(horizontal>0.5)sourceUV.x=uv.x+(sourceUV.x-uv.x)*compression;
   else sourceUV.y=uv.y+(sourceUV.y-uv.y)*compression;
  }
  sourceUV=sourceUV*sampleScale+sampleOffset;
  float blurEdge=edge;
  if(inner>0.5 && fallback<0.5)blurEdge=(edge+2.0*seamOffset)/(1.0+2.0*seamOffset);
  if(reflectedCover>0.5)blurEdge=max(blurEdge,(2.0*seamOffset-axis)/(1.0+2.0*seamOffset));
  float radius=72.0*(texSize.x/1600.0)*motion*pow(clamp(blurEdge,0.0,1.0),1.35);
  radius*=blurStrength*(inner>0.5?1.25:1.0);
  float2 footprint=max((0.5+aaStrength)/texSize,float2(radius)*0.75/texSize);
  half3 color=half3(0);
  if(radius<0.01){
   float2 coverage=smoothstep(-footprint,footprint,sourceUV)*(1.0-smoothstep(1.0-footprint,1.0+footprint,sourceUV));
   color=sampleLevel(clamp(sourceUV,float2(0),float2(1))*texSize,0.0)*half(coverage.x*coverage.y);
  }else{
  for(int y=-2;y<=2;y++) {
   for(int x=-2;x<=2;x++) {
    float wx=x==0?6.0:(abs(float(x))==1.0?4.0:1.0);
    float wy=y==0?6.0:(abs(float(y))==1.0?4.0:1.0);
    float2 sampleUV=sourceUV+float2(float(x),float(y))*radius/texSize;
    float2 coverage=smoothstep(-footprint,footprint,sampleUV)*(1.0-smoothstep(1.0-footprint,1.0+footprint,sampleUV));
    color+=sampleLevel(clamp(sampleUV,float2(0),float2(1))*texSize,log2(max(1.0,radius)))*half(coverage.x*coverage.y*wx*wy/256.0);
   }
  }
  }
  float effect=motion*pow(clamp((edge-0.2)/0.8,0.0,1.0),1.35);
  color*=half(1.0-min(1.0,effect*2.0*intensity));
  float alpha=opaqueContent>0.5?1.0:smoothstep(0.0,(windowReveal>0.5 && startupEasing>0.5)?0.015:0.035,progress);
  if(inner>0.5 && fallback<0.5 && axis>=0.5)alpha*=seamMask;
  return half4(color*half(alpha),half(alpha));
 }
 """.trimIndent()
}
