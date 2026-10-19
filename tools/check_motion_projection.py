"""Compile the actual shared AGSL warp as C++ and check its geometry invariants."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/org/duofold/live/MotionGlassShader.kt').read_text().split('"""')[1]
code=r'''
#include <cmath>
#include <cassert>
#include <cstdio>
struct float2 {float x,y; float2(float a,float b):x(a),y(b){} float2 operator*(float s)const{return {x*s,y*s};}};
float mix(float a,float b,float t){return a+(b-a)*t;}
float clamp(float x,float a,float b){return x<a?a:(x>b?b:x);}
float smoothstep(float a,float b,float v){float t=clamp((v-a)/(b-a),0,1);return t*t*(3-2*t);}
float progress=1, reflectedCover=0, horizontal=0, reverse=0, inner=0, fallback=0;
float2 motionOffset(0,0);
'''+source+r'''
bool near(float a,float b){return std::abs(a-b)<0.00001f;}
float2 point(float axis,float across){float a=mix(axis,1-axis,reverse);float2 uv=horizontal>.5?float2(across,a):float2(a,across);if(reflectedCover>.5)uv.x=1-uv.x;return uv;}
int main(){
 for(int reflected=0;reflected<2;reflected++)for(int r=0;r<4;r++)for(int panel=0;panel<2;panel++)for(int fb=0;fb<2;fb++){
  reflectedCover=reflected;horizontal=r%2;reverse=(r==1||r==2);inner=panel;fallback=fb;
  float anchor=panel?(fb?1.f:.5f):(reflected?1.f:0.f);
  for(int a=0;a<=100;a++)for(int b=0;b<=10;b++){
   auto uv=point(a/100.f,b/10.f);motionOffset={0,0};auto same=motionWarp(uv);
   assert(near(uv.x,same.x)&&near(uv.y,same.y));
  }
  for(int tx=-2;tx<=2;tx++)for(int ty=-2;ty<=2;ty++)for(int b=0;b<=10;b++){
   motionOffset={(float)tx,(float)ty};auto uv=point(anchor,b/10.f);auto fixed=motionWarp(uv);
   assert(near(uv.x,fixed.x)&&near(uv.y,fixed.y));
   if(panel&&!fb)for(int a=50;a<=100;a++){uv=point(a/100.f,b/10.f);fixed=motionWarp(uv);assert(near(uv.x,fixed.x)&&near(uv.y,fixed.y));}
  }
 }
 // Positive tilt reverses only the actual cover, not inner or reflected glass.
 horizontal=0;reverse=0;fallback=0;motionOffset={.5f,0};
 inner=0;reflectedCover=0;auto cover=motionWarp({.25f,.5f});assert(cover.x>.25f);
 inner=1;auto in=motionWarp({.25f,.5f});assert(in.x>.25f);
 inner=0;reflectedCover=1;auto reflected=motionWarp({.75f,.5f});assert(reflected.x<.75f);
 for(float gain: {0.25f,0.5f,1.f,2.f,4.f}){
  float previous=-2;
  for(int i=0;i<=500;i++){float x=i/1000.f;float y=motionAxis(x,gain,.5,1);assert(std::isfinite(y)&&y>=previous&&y>=-1.5&&y<=.5);previous=y;}
  assert(near(motionAxis(0,gain,.5,0),0));
  assert(near(motionAxis(.5,gain,.5,1),.5));
  if(gain!=1)assert(!near(motionAxis(0,gain,.5,1),0));
  assert(near(motionLever(.5,.5,1),0));
  assert(!near(motionLever(0,.5,1),0));
 }
 assert(motionAxis(.25,2,.5,0)<.25);assert(motionAxis(.25,.5,.5,0)>.25);
 puts("PASS: zero-tilt identity, cover left anchor, inner seam anchor and free left edge, clean inner half across rotations/reflection/fallback, finite monotonic projection, bidirectional stretch");
}
'''
code=code.replace('#include <cstdio>','#include <cstdio>\n#include <initializer_list>')
with tempfile.TemporaryDirectory() as td:
 p=Path(td);(p/'test.cpp').write_text(code)
 subprocess.run(['c++','-std=c++17','-O2',str(p/'test.cpp'),'-o',str(p/'test')],check=True)
 subprocess.run([str(p/'test')],check=True)
