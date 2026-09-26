package org.duofold.live

/** Post-process only: samples a rendered layer, never re-evaluates glass or blur. */
internal object AntiAliasShader {
 val source="""
 uniform shader rendered;
 uniform float aaStrength;
 uniform float2 aaPixelSize;
 float edgeValue(half4 c){return dot(float3(c.rgb),float3(0.2126,0.7152,0.0722));}
 half4 main(float2 p){
  half4 c=rendered.eval(p);
  half4 n=rendered.eval(p-float2(0.0,aaPixelSize.y));
  half4 s=rendered.eval(p+float2(0.0,aaPixelSize.y));
  half4 w=rendered.eval(p-float2(aaPixelSize.x,0.0));
  half4 e=rendered.eval(p+float2(aaPixelSize.x,0.0));
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
  half4 filtered=(rendered.eval(p-direction)+rendered.eval(p+direction))*half(0.5);
  return mix(c,filtered,half(weight));
 }
 """.trimIndent()
}
