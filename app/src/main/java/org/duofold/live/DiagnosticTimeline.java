package org.duofold.live;
import java.util.ArrayDeque;
/** Fixed memory bounds. Receipt order is retained; each row carries its observation time. */
final class DiagnosticTimeline {
 private final int limit,width;private final ArrayDeque<String> rows=new ArrayDeque<>();private long dropped;
 DiagnosticTimeline(int limit,int width){if(limit<1||width<1)throw new IllegalArgumentException();this.limit=limit;this.width=width;}
 synchronized void add(long observed,long received,String kind,String detail){
  String value=detail==null?"":detail.replace('\n',' ').replace('\r',' ').replace('\t',' ');
  if(value.length()>width)value=value.substring(0,width)+" [truncated]";
  rows.addLast(observed+"\t"+received+"\t"+kind+"\t"+value);
  while(rows.size()>limit){rows.removeFirst();dropped++;}
 }
 synchronized String report(int maximum){StringBuilder b=new StringBuilder("droppedOldest=").append(dropped).append(" retained=").append(rows.size()).append("\nobservedElapsedMs\treceivedElapsedMs\tkind\tdetails\n");int skip=Math.max(0,rows.size()-Math.max(0,maximum));for(String r:rows)if(skip-->0){}else b.append(r).append('\n');return b.toString();}
}
