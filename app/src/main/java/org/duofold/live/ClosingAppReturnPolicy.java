package org.duofold.live;
/** Optional screenshot-only test of primary-app focus before native closed mode. */
final class ClosingAppReturnPolicy {
 static boolean shouldReturn(boolean enabled,boolean screenshot,boolean innerPrimary,boolean owned,
                            boolean fresh,boolean unlocked,boolean attempted,float angle){
  return enabled&&screenshot&&innerPrimary&&owned&&fresh&&unlocked&&!attempted
    &&Float.isFinite(angle)&&angle>0&&angle<=15;
 }
}
