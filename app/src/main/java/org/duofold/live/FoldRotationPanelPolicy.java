package org.duofold.live;

/** Hold angles follow physical panels, not Samsung's interchangeable logical IDs. */
final class FoldRotationPanelPolicy {
 static int rotation(String physical,String primary,int primaryRotation,String secondary,int secondaryRotation,int fallback){
  if(physical==null||!physical.startsWith("local:"))return fallback;
  if(physical.equals(primary))return primaryRotation;
  if(physical.equals(secondary))return secondaryRotation;
  return fallback;
 }
}
