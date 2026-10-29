package org.duofold.live.experimental.b8;
public class LiveB8Power {
 public static class Info {public String uniqueId;public int layerStack,logicalWidth=100,logicalHeight=200,rotation;Info(String s,int n){uniqueId=s;layerStack=n;}}
 public Info[] rows={new Info("inner",10),new Info("cover",20)};
 Object info(int n){return rows[n];}static String physical(Object i){return ((Info)i).uniqueId;}
 static int number(Object i,String key)throws Exception{return i.getClass().getField(key).getInt(i);}
}
