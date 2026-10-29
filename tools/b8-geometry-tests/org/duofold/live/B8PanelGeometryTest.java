package org.duofold.live;
import android.content.Context;
import android.graphics.Point;
import android.view.Display;

public final class B8PanelGeometryTest {
 private static int checks;
 private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
 private static void size(Display d,int w,int h,String message){Point p=B8PanelGeometry.INSTANCE.size(d);check(p.x==w&&p.y==h,message+": got "+p.x+"x"+p.y);}
 public static void main(String[] args){
  Context c=new Context();B8PanelGeometry.INSTANCE.initialize(c);Display d=new Display();
  // Alpha47: identity/mode arrive before the old inner bounds change to cover.
  size(d,2448,1848,"initial observation");
  d.width=1248;d.height=1972;
  size(d,1248,1972,"cover bounds refresh with unchanged identity/mode/rotation and no display event");
  check(d.reads==2,"each call measures current geometry");
  check("1248,1972".equals(c.prefs.values.get("cover:1248x1972:0")),"fresh ON bounds replace saved transient bounds");
  int writes=c.prefs.writes;size(d,1248,1972,"steady ON measurement");
  check(c.prefs.writes==writes,"identical observations do not rewrite preferences");
  d.state=1;d.width=2448;d.height=1848;
  size(d,1248,1972,"OFF prewarm can use saved panel dimensions");
  d.state=2;
  size(d,2448,1848,"OFF saved result never supplies ON readiness");
  d.width=1248;d.height=1972;size(d,1248,1972,"later ON correction needs no event");
  d.width=0;d.height=0;size(d,0,0,"invalid live measurement is not repaired from saved readiness");
  check("1248,1972".equals(c.prefs.values.get("cover:1248x1972:0")),"invalid ON bounds do not overwrite saved prewarm size");
  d.physical="unknown";d.width=600;d.height=900;size(d,600,900,"unknown mapping returns live observation");
  d.state=1;d.width=700;d.height=1000;size(d,700,1000,"unknown mapping is never saved");
  check(!c.prefs.values.containsKey("unknown:1248x1972:0"),"unknown identity has no saved dimensions");
  d.physical="cover";d.rotation=1;d.width=1972;d.height=1248;d.state=2;
  size(d,1972,1248,"rotated ON bounds");d.state=1;d.width=1;d.height=1;
  size(d,1972,1248,"rotated OFF prewarm uses its own key");
  c.prefs.values.put("cover:1248x1972:1","broken,geometry");size(d,1,1,"invalid stored bounds fall back to measurement");
  d.state=2;d.rotation=0;d.mode=new Display.Mode(936,1479);d.width=936;d.height=1479;
  size(d,936,1479,"resolution change preserves measured logical size");
  Point first=B8PanelGeometry.INSTANCE.size(d);first.x=9;
  size(d,936,1479,"callers cannot mutate subsequent observations");
  System.out.println("PASS: "+checks+" production geometry checks");
 }
}
