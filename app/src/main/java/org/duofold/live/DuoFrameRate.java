package org.duofold.live;

import java.util.Locale;

/** Per-renderer, per-session software rates. Gaps remain in the denominator. */
final class DuoFrameRate {
 private static final class Rate {
  long first=-1,last=-1,count;
  void record(long now){if(now<0||now<last)return;if(first<0)first=now;last=now;count++;}
  String report(){
   long span=last-first;
   return count<2||span<=0?"unmeasured (fewer than two timed frames)":
    String.format(Locale.US,"%.1f FPS (%d frames / %d ms first-to-last)",(count-1)*1000.0/span,count,span);
  }
 }
 private final Rate submissions=new Rate(),completions=new Rate();
 void submitted(long now){submissions.record(now);}
 void completed(long now){completions.record(now);}
 String report(){return "Duo session submitted="+submissions.report()+"; presentation-callback="+completions.report()+"; software rates, not panel Hz or optical FPS";}
}
