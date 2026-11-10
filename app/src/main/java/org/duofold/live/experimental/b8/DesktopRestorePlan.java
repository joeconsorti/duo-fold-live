package org.duofold.live.experimental.b8;
/** Generates the exact recovery program used by the independent watchdog. */
final class DesktopRestorePlan {
 static String script(String marker,String original){
  if(!marker.matches("/[A-Za-z0-9_./-]+"))throw new IllegalArgumentException("Invalid marker path");
  if(!original.equals("null")&&!original.equals("0")&&!original.equals("1"))throw new IllegalArgumentException("Invalid desktop value");
  String command=original.equals("null")?"settings delete global force_desktop_mode_on_external_displays":"settings put global force_desktop_mode_on_external_displays "+original;
  return "#!/system/bin/sh\n[ -f '"+marker+"' ] || exit 0\ncurrent=$(settings get global force_desktop_mode_on_external_displays) || exit 1\ncase \"$current\" in 1)\n "+command+" || exit 1\n after=$(settings get global force_desktop_mode_on_external_displays) || exit 1\n [ \"$after\" = '"+original+"' ] || exit 1\n ;;\n *) echo 'Desktop setting changed externally; preserving it' ;;\nesac\nrm -f '"+marker+"'\n";
 }
}
