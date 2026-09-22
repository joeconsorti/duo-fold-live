package org.duofold.live;
/** Additive mode gate. Windowed Glass always takes the established rendering path. */
final class AnimationModePolicy {
 static final String DEFAULT="windowed";
 private float anchor=Float.NaN;
 private int direction;
 static String sanitize(String value){return "duo_classic".equals(value)||"fold_only".equals(value)||"unfold_only".equals(value)?value:DEFAULT;}
 static boolean mirrors(String mode){return !"duo_classic".equals(sanitize(mode));}
 static String label(String mode){switch(sanitize(mode)){case "duo_classic":return "Duo Classic";case "fold_only":return "Fold-Only";case "unfold_only":return "Unfold-Only";default:return "Windowed Glass";}}
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
