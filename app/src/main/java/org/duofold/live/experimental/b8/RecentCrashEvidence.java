package org.duofold.live.experimental.b8;

/** Select epoch-stamped crash lines without attributing historical crashes to a new fold. */
public final class RecentCrashEvidence {
 private RecentCrashEvidence(){}
 public static String select(String raw,long nowMs,long windowMs){
  StringBuilder result=new StringBuilder();int older=0,unparsed=0,kept=0;
  for(String row:raw.split("\n")){
   String value=row.trim();if(value.startsWith("---------"))continue;
   int space=value.indexOf(' ');long at;
   try{double seconds=Double.parseDouble(space<0?value:value.substring(0,space));if(!Double.isFinite(seconds))throw new NumberFormatException();at=(long)(seconds*1000d);}
   catch(NumberFormatException e){if(!value.isEmpty())unparsed++;continue;}
   if(at<nowMs-windowMs||at>nowMs+10000){older++;continue;}
   result.append(row).append('\n');kept++;
  }
  return "Crash window wallMs="+(nowMs-windowMs)+".."+nowMs+" retainedLines="+kept+" omittedOutsideWindow="+older+" unparsedLines="+unparsed+"\n"+
   (kept==0?"No timestamp-matching crash lines available. Absence is not proof of no crash.\n":result.toString());
 }
}
