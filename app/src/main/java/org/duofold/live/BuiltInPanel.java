package org.duofold.live;
import android.view.Display;
/** Accept internal panels of any resolution, never external/virtual displays. */
public final class BuiltInPanel {
 public static boolean accepts(Display display){
  if(display==null || !display.isValid() || (display.getDisplayId()!=0 && display.getDisplayId()!=1))return false;
  try{return ((Integer)Display.class.getMethod("getType").invoke(display))==1;}
  catch(Exception unavailable){
   // Preserve the previously supported panels if type inspection is unavailable.
   Display.Mode mode=display.getMode();
   int lo=Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight()),hi=Math.max(mode.getPhysicalWidth(),mode.getPhysicalHeight());
   return (lo==1248 && hi==1972)||(lo==1848 && hi==2448);
  }
 }
}
