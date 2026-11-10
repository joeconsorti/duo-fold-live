package org.duofold.live.experimental.b8;
public class LiveB8Power {
 public static class Info {public String uniqueId;public int layerStack,logicalWidth,logicalHeight,rotation;Info(String s,int n){uniqueId=s;layerStack=n;logicalWidth=s.equals("inner")?2448:1248;logicalHeight=s.equals("inner")?1848:1972;}}
 public Info[] rows={new Info("inner",10),new Info("cover",20)};
 public Runnable afterPair;
 Object info(int n){Info result=rows[n];if(n==1&&afterPair!=null){Runnable action=afterPair;afterPair=null;action.run();}return result;}static String physical(Object i){return ((Info)i).uniqueId;}
 static int number(Object i,String key)throws Exception{return i.getClass().getField(key).getInt(i);}
}
