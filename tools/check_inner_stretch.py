"""Compile the shaders' actual scalar functions and check inner stretch invariants.
Run: python tools/check_inner_stretch.py (requires a C++ compiler).
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def function(source, name):
    start = source.index(' float ' + name + '(')
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

for filename in ('DuoGlass.kt',):
    source = (ROOT / 'app/src/main/java/org/duofold/live' / filename).read_text()
    start = source.index('     float distance=earlyInner(')
    end = source.index('     limited=innerHinge-innerHinge*distance;', start)
    mapping = source[start:end]
    code = '''
#include <cmath>
#include <cassert>
#include <cstdio>
float earlyStretch, endStretch;
float max(float a,float b){return a>b?a:b;}
float clamp(float v,float a,float b){return v<a?a:(v>b?b:v);}
float mix(float a,float b,float t){return a+(b-a)*t;}
float smoothstep(float a,float b,float v){float t=clamp((v-a)/(b-a),0,1);return t*t*(3-2*t);}
'''
    code += '\n'.join(function(source, n) for n in ('coverAt', 'innerAt', 'earlyInner'))
    code += '\nfloat mapped(float x,float a,float innerHinge){\n' + mapping + '\nreturn distance;\n}\n'
    max_angle = '1.570796327f'
    code += '''
int main(){
 for(int layout=0;layout<2;layout++)for(int e=0;e<=30;e++)for(int s=0;s<=30;s++)for(int p=1;p<=20;p++){
  earlyStretch=e*.1f;endStretch=s*.05f;float x=p*.05f,span=layout?1.f:.5f,previous=x;
  for(int frame=0;frame<=1000;frame++){
   float a=MAX_ANGLE*frame/1000.f,d=mapped(x,a,span);
   assert(std::isfinite(d));
   assert(d<=x+2e-6f); // No inward squeeze past the flat coordinates.
   assert(d<=previous+2e-6f); // No reversal while closing (opening traverses in reverse).
   if(s==0)assert(std::abs(d-x)<2e-6f); // 0% means no horizontal stretch at any angle.
   if(s>=16){ // Preserve the complete previously working 80–150% mapping.
    float old=earlyInner(x,a,innerAt(x,a,span),span);
    old/=mix(1.f,clamp(endStretch,.8f,1.5f)/.8f,smoothstep(.523598776f,1.047197551f,a));
    assert(std::abs(d-old)<2e-6f);
   }
   previous=d;
  }
 }
 puts("PASS: no compression/reversal; zero is flat; 80–150% unchanged");
}
'''.replace('MAX_ANGLE', max_angle)
    with tempfile.TemporaryDirectory() as folder:
        cpp = Path(folder) / 'check.cpp'
        binary = Path(folder) / 'check'
        cpp.write_text(code)
        subprocess.run(['c++', '-O2', str(cpp), '-o', str(binary)], check=True)
        print(filename, flush=True)
        subprocess.run([str(binary)], check=True)
