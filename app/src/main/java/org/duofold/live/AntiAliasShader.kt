package org.duofold.live

/** Spatial AA in output-pixel coordinates. No history buffers or motion ghosting. */
internal object AntiAliasShader {
 val source="""
 uniform float aaMode;
 uniform float2 aaPixelSize;
 float edgeValue(half4 c){return dot(float3(c.rgb),float3(0.2126,0.7152,0.0722));}
 half4 main(float2 p){
  if(aaMode<0.5 || aaStrength<=0.0)return shade(p);
  if(aaMode>1.5){
   // Four subpixel evaluations of the entire effect, including coverage and projection.
   float2 d=aaPixelSize*(0.25*aaStrength/0.35);
   return (shade(p+float2(-d.x,-d.y))+shade(p+float2(d.x,-d.y))+
           shade(p+float2(-d.x,d.y))+shade(p+float2(d.x,d.y)))*half(0.25);
  }
  half4 c=shade(p);
  half4 n=shade(p-float2(0.0,aaPixelSize.y));
  half4 s=shade(p+float2(0.0,aaPixelSize.y));
  half4 w=shade(p-float2(aaPixelSize.x,0.0));
  half4 e=shade(p+float2(aaPixelSize.x,0.0));
  float lc=edgeValue(c),ln=edgeValue(n),ls=edgeValue(s),lw=edgeValue(w),le=edgeValue(e);
  float low=min(lc,min(min(ln,ls),min(lw,le)));
  float high=max(lc,max(max(ln,ls),max(lw,le)));
  float alphaRange=float(max(max(n.a,s.a),max(w.a,e.a))-min(min(n.a,s.a),min(w.a,e.a)));
  float contrast=max(high-low,alphaRange);
  float weight=smoothstep(0.03,0.15,contrast)*aaStrength;
  // Filter across the strongest local edge, leaving flat regions untouched.
  float gx=abs(le-lw)+abs(float(e.a-w.a));
  float gy=abs(ls-ln)+abs(float(s.a-n.a));
  float2 direction=gx>gy?float2(aaPixelSize.x*0.5,0.0):float2(0.0,aaPixelSize.y*0.5);
  half4 filtered=(shade(p-direction)+shade(p+direction))*half(0.5);
  return mix(c,filtered,half(weight));
 }
 """.trimIndent()
}
