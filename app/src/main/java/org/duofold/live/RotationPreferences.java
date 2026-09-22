package org.duofold.live;
import java.util.*;
/** Validate before any rotation write; do not guess an absent or vendor-specific preference map. */
final class RotationPreferences {
 static Map<Integer,Integer> parse(String text) {
  if(text==null||text.isEmpty())throw new IllegalArgumentException("Per-posture rotation preferences unavailable; no hold applied");
  String[] parts=text.split(":",-1);
  if(parts.length%2!=0)throw new IllegalArgumentException("Invalid rotation preference map");
  Map<Integer,Integer> result=new TreeMap<>();
  for(int i=0;i<parts.length;i+=2){int posture=Integer.parseInt(parts[i]),mode=Integer.parseInt(parts[i+1]);
   if(posture<0||posture>3||mode<0||mode>2||result.put(posture,mode)!=null)throw new IllegalArgumentException("Unknown rotation preference");
  }
  return result;
 }
}
