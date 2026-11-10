package org.duofold.live.experimental.b8;
import java.util.*;
import java.util.regex.*;
/** Parses physical SurfaceFlinger commands, not optical observations. */
final class BrightnessSamples {
 static final Pattern LINE=Pattern.compile("^\\s*([0-9]+\\.[0-9]+).*setDisplayBrightness\\(([0-9]+)\\): displayBrightness: ([^,]+), displayBrightnessNits: ([^,]+), sdrWhitePointNits: ([^,]+),?\\s*,?\\s*dimmingRatio: ([0-9.]+)");
 static final class Sample {long ms,id;float value,nits,sdr,ratio;}
 static Map<Long,Sample> parse(String text){Map<Long,Sample> result=new HashMap<>();for(String line:text.split("\n")){Matcher m=LINE.matcher(line);if(!m.find())continue;try{Sample s=new Sample();s.ms=(long)(Double.parseDouble(m.group(1))*1000);s.id=Long.parseLong(m.group(2));s.value=Float.parseFloat(m.group(3));s.nits=Float.parseFloat(m.group(4));s.sdr=Float.parseFloat(m.group(5).trim());s.ratio=Float.parseFloat(m.group(6));result.put(s.id,s);}catch(RuntimeException ignored){}}return result;}
 static boolean usable(Sample s,long now){return s!=null&&now>=s.ms&&now-s.ms<=120000&&Float.isFinite(s.value)&&s.value>0&&s.value<=1&&Float.isFinite(s.nits)&&s.nits>0&&Float.isFinite(s.sdr)&&s.sdr>0&&Math.abs(s.ratio-1)<0.0001f;}
 static String summary(String text){
 StringBuilder out=new StringBuilder("SurfaceFlinger command spans only; not optical blackout durations. Missing commands mean unknown.\n");
 Map<Long,Long> off=new HashMap<>();
 for(String line:text.split("\n")){for(Sample s:parse(line).values()){
 if(s.value<0){if(!off.containsKey(s.id)){off.put(s.id,s.ms);out.append("OFF_COMMAND physical=").append(s.id).append(" monotonicMs=").append(s.ms).append('\n');}}
 else if(off.containsKey(s.id)){long start=off.remove(s.id);out.append("NONNEGATIVE_COMMAND physical=").append(s.id).append(" monotonicMs=").append(s.ms).append(" commandSpanMs=").append(s.ms-start).append('\n');}
 }}
 for(Long id:off.keySet())out.append("Unmatched OFF command physical=").append(id).append(" since=").append(off.get(id)).append('\n');
 return out.toString();
 }
}
