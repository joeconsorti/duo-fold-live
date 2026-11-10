"""Exercise the actual AGSL scalar shading functions as C++ across the fold range."""
from pathlib import Path
import subprocess
import tempfile
source=(Path(__file__).resolve().parents[1]/'app/src/main/java/org/duofold/live/DuoGlass.kt').read_text()
def function(name):
 start=source.index(' float '+name+'(');opening=source.index('{',start);end=opening+1;depth=1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
code='''
#include <cmath>
#include <initializer_list>
#include <cassert>
#include <cstdio>
float min(float a,float b){return a<b?a:b;}
float clamp(float x,float a,float b){return x<a?a:(x>b?b:x);}
float smoothstep(float lo,float hi,float value){float s=clamp((value-lo)/(hi-lo),0,1);return s*s*(3-2*s);}
'''+function('windowShadeEdge')+function('windowShadeAmount')+function('coverAt')+function('coverBackdropWeight')+'''
int main(){
 for(int inner=0;inner<=1;inner++)for(int fallback=0;fallback<=1;fallback++)
 for(int pos=0;pos<=100;pos++)for(int strength=0;strength<=15;strength++){
  float axis=pos/100.f,edge=windowShadeEdge(axis,inner,fallback),previous=0;
  for(int step=0;step<=1000;step++){
   float progress=step/1000.f,motion=progress*progress*(3-2*progress);
   float shade=windowShadeAmount(motion,edge,strength/10.f);
   assert(std::isfinite(shade)&&shade>=0&&shade<=1);
   assert(shade+1e-6f>=previous); // Inner closing: dark boundary cannot retreat.
   if(step==0||strength==0)assert(shade==0);
   if(inner&&!fallback&&axis>=.5f)assert(shade==0); // Preserve clean right half.
   previous=shade;
  }
 }
 // Canonical edge orientation: inner left outer edge; cover right outer edge.
 assert(windowShadeEdge(0,1,0)==1&&windowShadeEdge(.5f,1,0)==0);
 assert(windowShadeEdge(0,0,0)==0&&windowShadeEdge(1,0,0)==1);
 // Committed cover poses for the near-black YouTube frames in alpha.86.
 for(float pose : {140.67693f,126.4552f,115.29815f,108.86549f}){
  float coord=coverAt(.5f,pose*3.14159265f/180.f)/1.5625f;
  assert(coord<-.01f);
  assert(coverBackdropWeight(coord,.01f,0.f)==1.f);
 }
 assert(coverBackdropWeight(.4f,.01f,0.f)==0.f); // Other edges retain normal projection.
 assert(coverBackdropWeight(-.5f,.01f,1.f)==0.f);
 assert(coverBackdropWeight(-.5f,.01f,.25f)==.75f); // Partially valid filter footprint.
 puts("Windowed Glass: bounded, monotonic shading across panel positions and strengths");
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'test.cpp').write_text(code)
 subprocess.run(['c++','-O2',str(p/'test.cpp'),'-o',str(p/'test')],check=True)
 subprocess.run([str(p/'test')],check=True)
