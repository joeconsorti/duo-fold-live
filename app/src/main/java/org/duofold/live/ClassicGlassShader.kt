package org.duofold.live

/** Preserved 1.4.3 glass shader. */
internal object ClassicGlassShader {
 val source="""
 uniform shader content;
 uniform shader mip1; uniform shader mip2; uniform shader mip3; uniform shader mip4; uniform shader mip5;
 half3 sampleLevel(float2 p,float lod){
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
 uniform float progress;
 uniform float inner;
 uniform float fallback;
 uniform float horizontal;
 uniform float reverse;
 uniform float intensity;
 half4 main(float2 p) {
  float2 uv=(p-origin)/extent;
  if(any(lessThan(uv,float2(0))) || any(greaterThan(uv,float2(1)))) return half4(0);
  float axis=mix(uv.x,uv.y,horizontal);
  axis=mix(axis,1.0-axis,reverse);
  float edge=inner>0.5 ? (fallback>0.5 ? 1.0-axis : 1.0-2.0*axis) : axis;
  if(inner>0.5 && fallback<0.5 && edge<=0.0) return half4(0);
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
  sourceUV=sourceUV*sampleScale+sampleOffset;
  float radius=72.0*(texSize.x/1600.0)*motion*pow(clamp(edge,0.0,1.0),1.35);
  float2 footprint=max(0.5/texSize,float2(radius)*0.75/texSize);
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
  float alpha=smoothstep(0.0,0.035,progress);
  return half4(color*half(alpha),half(alpha));
 }
 """.trimIndent()
}
