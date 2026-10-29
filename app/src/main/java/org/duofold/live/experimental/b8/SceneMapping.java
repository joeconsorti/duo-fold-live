package org.duofold.live.experimental.b8;

/** Reject partial or duplicate observations without detaching retained coverage. */
public final class SceneMapping {
 public static boolean complete(String a,int sa,String b,int sb,String first,String second){
  return a!=null&&b!=null&&!a.equals(b)&&sa>=0&&sb>=0&&sa!=sb
   &&((a.equals(first)&&b.equals(second))||(a.equals(second)&&b.equals(first)));
 }
 private SceneMapping(){}
}
