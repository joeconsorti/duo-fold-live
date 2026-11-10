package org.duofold.live.experimental.b8;
/** Generates the exact recovery program used by the independent watchdog. */
final class AnimatorRestorePlan {
 static String script(String marker,String original){
  if(!marker.matches("/[A-Za-z0-9_./-]+"))throw new IllegalArgumentException("Invalid marker path");
  if(!original.equals("null")&&!original.matches("[0-9]+(?:\\.[0-9]+)?"))throw new IllegalArgumentException("Invalid scale");
  String command=original.equals("null")?"settings delete global animator_duration_scale":"settings put global animator_duration_scale "+original;
  return "#!/system/bin/sh\n[ -f '"+marker+"' ] || exit 0\ncurrent=$(settings get global animator_duration_scale) || exit 1\ncase \"$current\" in 0|0.0|0.00)\n "+command+" || exit 1\n after=$(settings get global animator_duration_scale) || exit 1\n [ \"$after\" = '"+original+"' ] || exit 1\n ;;\n *) echo 'Scale changed externally; preserving it' ;;\nesac\nrm -f '"+marker+"'\n";
 }
}
