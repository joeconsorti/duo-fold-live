package org.duofold.live.experimental.b8;
public final class ExperimentConfigTest {
 private static int assertions;
 private static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("Check "+assertions);}
 private static void reject(String value){boolean rejected=false;try{ExperimentConfig.parse(value);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
 public static void main(String[] args){
  for(boolean on:new boolean[]{false,true}){ExperimentConfig c=ExperimentConfig.preset(on);c.validate();check(c.alternate&&c.initial==1&&c.switches==4&&c.duration==20&&c.order==1);check(c.target==(on?3:0));check(c.encode().equals(ExperimentConfig.parse(c.encode()).encode()));}
  ExperimentConfig c=new ExperimentConfig();String good=c.encode();
  reject(null);reject("");reject(good+",0");reject(good.replaceFirst("1,","2,"));reject("1,"+new String(new char[300]).replace('\0','1'));
  int[][] invalid={{1,9},{1,31},{2,3},{3,999},{3,5001},{4,999},{4,5001},{5,0},{5,13},{6,4},{7,2},{8,249},{8,1001},{9,5},{10,9},{10,101},{11,2},{12,2},{13,2},{14,2},{15,2}};
  for(int[] entry:invalid){String[] a=good.split(",");a[entry[0]]=String.valueOf(entry[1]);reject(String.join(",",a));}
  c.initial=0;reject(c.encode());c.alternate=false;c.validate();
  c.target=1;c.backend=1;reject(c.encode());c.target=3;c.validate();
  for(int duration:new int[]{10,20,30})for(int wake=0;wake<=4;wake++)for(int renewal:new int[]{0,250,500,1000})for(int target=0;target<=3;target++){
   c=new ExperimentConfig();c.duration=duration;c.wake=wake;c.renewal=renewal;c.target=target;check(c.encode().equals(ExperimentConfig.parse(c.encode()).encode()));
  }
  System.out.println("ExperimentConfigTest: "+assertions+" assertions passed");
 }
}
