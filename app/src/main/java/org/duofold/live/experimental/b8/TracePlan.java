package org.duofold.live.experimental.b8;
final class TracePlan {
 static String config(int seconds,boolean detail,boolean colorfade){
  if(seconds<10||seconds>30||colorfade&&!detail)throw new IllegalArgumentException("Invalid trace plan");
  String s="buffers { size_kb: 8192 fill_policy: RING_BUFFER }\n";
  if(detail)s+="buffers { size_kb: 32768 fill_policy: RING_BUFFER }\n";

  if(detail)s+="write_into_file: true\nfile_write_period_ms: 250\nmax_file_size_bytes: 201326592\n";
  s+="duration_ms: "+seconds*1000+"\ndata_sources { config { name: \"android.surfaceflinger.frametimeline\" } }\ndata_sources { config { name: \"com.android.wm.shell.transition\" } }\n";
  if(detail)s+="data_sources { config { name: \"android.surfaceflinger.layers\" target_buffer: 1 surfaceflinger_layers_config { mode: MODE_ACTIVE trace_flags: TRACE_FLAG_COMPOSITION "+(colorfade?"trace_flags: TRACE_FLAG_EXTRA ":"")+"} } }\n";
  return s;
 }
}
