package org.duofold.live.wallpaperlayer;
import java.util.ArrayDeque;
public final class ProbeJournal {
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private String failure="none",stage="Not started";private int attempts;
 public synchronized void begin(){attempts++;failure="none";stage="Start accepted";event("Attempt "+attempts+": "+stage);}
 public synchronized void phase(String s){stage=s;event(s);}
 public synchronized void fail(String s){failure=stage+": "+s;event("FAIL: "+failure);}
 public synchronized void stopped(String s){event(s);}
 private void event(String s){if(events.size()>=32)events.removeFirst();events.addLast(s);}
 public synchronized String report(){return "Start attempts: "+attempts+"\nLast stage: "+stage+"\nLast failure: "+failure+"\nHistory:\n"+String.join("\n",events);}
}
