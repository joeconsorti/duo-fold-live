package android.graphics;
public class RenderNode {
 int w,h;boolean recording;RenderEffect effect;
 public RenderNode(String label){}
 public boolean setPosition(int l,int t,int r,int b){w=r-l;h=b-t;return true;}
 public boolean setRenderEffect(RenderEffect e){
  if(e!=null&&e.radius!=Math.min(w,h)*.035f)throw new AssertionError("reference blur radius");
  effect=e;return true;
 }
 public RecordingCanvas beginRecording(int width,int height){
  if(recording||width!=w||height!=h)throw new AssertionError("recording extent");
  recording=true;return new RecordingCanvas();
 }
 public void endRecording(){if(!recording)throw new AssertionError("recording ended twice");recording=false;}
 public void discardDisplayList(){if(recording)throw new AssertionError("recording still open");}
}
