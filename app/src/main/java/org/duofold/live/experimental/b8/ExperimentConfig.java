package org.duofold.live.experimental.b8;
import java.util.*;

/** Versioned, bounded recipe shared by UI and shell. No arbitrary commands. */
final class ExperimentConfig {
 int duration=20, initial=1, settle=2000, period=2500, switches=4;
 int target=0, backend=0, renewal=0, wake=0, sample=25, flags=0, cancellation=0, order=0;
 boolean alternate=true, snapshots=false;
 static ExperimentConfig preset(boolean on){ExperimentConfig c=new ExperimentConfig();c.target=on?3:0;c.order=1;return c;}
 String encode(){return "1,"+duration+","+initial+","+settle+","+period+","+switches+","+target+","+backend+","+renewal+","+wake+","+sample+","+flags+","+cancellation+","+order+","+(alternate?1:0)+","+(snapshots?1:0);}
 static ExperimentConfig parse(String text){
  if(text==null||text.length()>256)throw new IllegalArgumentException("Missing/oversized recipe");
  String[] a=text.split(",",-1);if(a.length!=16||!a[0].equals("1"))throw new IllegalArgumentException("Unknown recipe format");
  int[] v=new int[16];for(int i=1;i<16;i++)v[i]=Integer.parseInt(a[i]);
  ExperimentConfig c=new ExperimentConfig();c.duration=v[1];c.initial=v[2];c.settle=v[3];c.period=v[4];c.switches=v[5];c.target=v[6];c.backend=v[7];c.renewal=v[8];c.wake=v[9];c.sample=v[10];c.flags=v[11];c.cancellation=v[12];c.order=v[13];c.alternate=v[14]==1;c.snapshots=v[15]==1;
  range(v[14],0,1);range(v[15],0,1);c.validate();return c;
 }
 void validate(){range(duration,10,30);range(initial,0,2);range(settle,1000,5000);range(period,1000,5000);range(switches,1,12);range(target,0,3);range(backend,0,1);range(renewal,0,1000);if(renewal!=0&&renewal<250)throw new IllegalArgumentException("Renewal must be 0 or 250–1000 ms");range(wake,0,4);range(sample,10,100);range(flags,0,1);range(cancellation,0,1);range(order,0,1);if(alternate&&initial==0)throw new IllegalArgumentException("Alternation needs an initial primary");if(backend==1&&target!=0&&target!=3)throw new IllegalArgumentException("Global ON requires both displays as target");}
 private static void range(int n,int lo,int hi){if(n<lo||n>hi)throw new IllegalArgumentException("Recipe value outside "+lo+".."+hi+": "+n);}
 String describe(){return "recipe="+encode()+"\nDuration="+duration+" s; initial="+new String[]{"unchanged","inner","cover"}[initial]+"; alternate="+alternate+"; settle="+settle+" ms; switch interval="+period+" ms; switch limit="+switches+"\nON target="+new String[]{"none","D0","D1","both"}[target]+"; API="+(backend==0?"per-display Samsung token":"global Samsung token")+"; renewal="+renewal+" ms (0=once); order="+(order==0?"before initial request":"after settling")+"\nWake lock="+new String[]{"none","partial","dim","bright","full"}[wake]+"; sample="+sample+" ms; request flags="+flags+"; cancellation="+(cancellation==0?"stop":"retry, maximum 3, 500 ms spacing")+"; switch snapshots="+snapshots+"\nSequence requires both displays sampled ON and committed ON before its first switch. No optical proof. Timer limits depend on worker scheduling.\n";}
}
