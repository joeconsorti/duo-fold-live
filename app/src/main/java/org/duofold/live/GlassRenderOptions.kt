package org.duofold.live
import android.content.Context
internal object GlassRenderOptions {
 val ids=listOf("normal","light","projection","system","system_projected")
 val labels=listOf("Normal glass","Lightweight glass (default)","Projection only (no blur)","Samsung notification background blur (experimental)","Samsung notification background blur + stretch (experimental)")
 fun mode(context:Context):String {
  val p=context.getSharedPreferences("standalone",0)
  if(p.getBoolean("dual",false))return "normal"
  return p.getString("glass_render_mode","light")?.takeIf{it in ids} ?: "light"
 }
 var systemStatus="System blur: not requested"
 private var lastWide="No wide-buffer sample"
 private var lastTall="No tall-buffer sample"
 @Synchronized fun recordBlur(wide:Boolean,sample:String){if(wide)lastWide=sample else lastTall=sample}
 @Synchronized fun blurMeters()="Retained blur samples (may precede current settings; buffer shape is not panel identity):\n$lastWide\n$lastTall"
 fun report(context:Context)="Rendering: "+labels[ids.indexOf(mode(context))]+"; "+systemStatus+"\n"+blurMeters()
}
