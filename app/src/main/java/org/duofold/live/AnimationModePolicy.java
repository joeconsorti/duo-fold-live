package org.duofold.live;
/** Windowed Glass appearance with an independent fold/unfold direction gate. */
final class AnimationModePolicy {
 static final String DEFAULT="windowed";
 static final String BOTH="both";
 static void initialize(android.content.SharedPreferences prefs){
  String oldMode=prefs.getString("animation_mode",DEFAULT);
  prefs.edit().putString("animation_behavior",initialBehavior(prefs.getString("animation_behavior",null),oldMode))
   .putString("animation_mode",DEFAULT).putString("animation_style","duo").putBoolean("debug_mode",false).commit();
 }
 static String style(String value){return DEFAULT;}
 static String behavior(String value){return "fold_only".equals(value)||"unfold_only".equals(value)?value:BOTH;}
 static String initialBehavior(String saved,String oldMode){return behavior(saved==null?oldMode:saved);}
 static String behaviorLabel(String value){switch(behavior(value)){case "fold_only":return "Fold-Only";case "unfold_only":return "Unfold-Only";default:return "Fold & Unfold";}}
 private float anchor=Float.NaN;
 private int direction;
 static String sanitize(String value){return "fold_only".equals(value)||"unfold_only".equals(value)?value:DEFAULT;}
 static boolean mirrors(String mode){return true;}
 static boolean blackFade(String mode,boolean debug){return false;}
 static String label(String mode){return "Windowed Glass";}
 boolean update(String mode,float angle,boolean fresh){
  mode=sanitize(mode);
  if(fresh&&Float.isFinite(angle)){
   if(!Float.isFinite(anchor))anchor=angle;
   if(angle-anchor>=2){direction=1;anchor=angle;}else if(angle-anchor<=-2){direction=-1;anchor=angle;}
   if(direction==1)anchor=Math.max(anchor,angle);else if(direction==-1)anchor=Math.min(anchor,angle);
   if(angle<=2){direction=1;anchor=angle;}else if(angle>=172){direction=-1;anchor=angle;}
  }
  if("fold_only".equals(mode))return direction==-1;
  if("unfold_only".equals(mode))return direction==1;
  return true;
 }
}
