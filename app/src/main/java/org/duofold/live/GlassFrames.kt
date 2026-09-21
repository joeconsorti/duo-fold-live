package org.duofold.live
import android.graphics.Bitmap
import android.os.*
import android.view.SurfaceControl
import androidx.compose.runtime.*
import java.util.concurrent.Executors
internal data class GlassFrame(val bitmap:Bitmap,val width:Int,val height:Int,val stamp:Long)
internal object GlassFrames {
 var frame by mutableStateOf<GlassFrame?>(null);private set
 var status by mutableStateOf("Glass renderer ready");private set
 private val main=Handler(Looper.getMainLooper())
 private val executor=Executors.newSingleThreadExecutor()
 private var suspended=false
 fun suspendCapture(){suspended=true;generation++;frame=null;main.removeCallbacks(tick)}
 fun resumeCapture(){if(suspended){suspended=false;if(clients>0)main.post(tick)}}
 private var clients=0;private var generation=0;private var pending=false
 private val surfaces=LinkedHashMap<Any,SurfaceControl>()
 fun surface(key:Any,sc:SurfaceControl?){if(sc==null)surfaces.remove(key) else surfaces[key]=sc}
 fun acquire(){clients++;if(clients==1){generation++;main.post(tick)}}
 fun release(){clients=(clients-1).coerceAtLeast(0);if(clients==0){generation++;frame=null;main.removeCallbacks(tick)}}
 private val tick=object:Runnable{override fun run(){
  if(clients==0 || suspended)return
  if(pending){main.postDelayed(this,50);return}
  val valid=surfaces.values.filter{it.isValid}.take(4)
  if(valid.isEmpty()){frame=null;main.postDelayed(this,100);return}
  val gen=generation;pending=true
  executor.execute{
   var result:Bundle?=null;var error="Glass frame unavailable"
   val p=Parcel.obtain();val r=Parcel.obtain()
   try{val remote=LiveAngles.captureBinder();p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)}
    remote.transact(1,p,r,0);r.readException();result=r.readBundle(Bitmap::class.java.classLoader);error=result?.getString("error")?:error
   }catch(e:Exception){error=e.message?:error}finally{p.recycle();r.recycle()}
   val response=result;val message=error
   main.post{pending=false;if(gen==generation && clients>0 && !suspended){
    val bitmap=response?.getParcelable("bitmap",Bitmap::class.java)
    if(response?.getBoolean("ok")==true && bitmap!=null){frame=GlassFrame(bitmap,response.getInt("width"),response.getInt("height"),response.getLong("stamp"));status="Projected glass · live compositor frames · ${response.getString("backend") ?: "layer capture"}"}
    else{frame=null;status="Glass unavailable; debug-style fallback: $message"}
    main.postDelayed(this,if(frame==null)600 else 80)
   }else if(clients>0 && !suspended)main.post(this)}
  }
 }}
 fun freeze(done:(GlassFrame?,String)->Unit){
  val valid=surfaces.values.filter{it.isValid}.take(4)
  if(valid.isEmpty()){done(null,"Waiting for overlay exclusion surface");return}
  executor.execute{
   val p=Parcel.obtain();val r=Parcel.obtain();var captured:GlassFrame?=null;var note="Outgoing capture unavailable"
   try{
    p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)}
    LiveAngles.captureBinder().transact(3,p,r,0);r.readException();val result=r.readBundle(Bitmap::class.java.classLoader)
    val bitmap=result?.getParcelable("bitmap",Bitmap::class.java)
    if(result?.getBoolean("ok")==true && bitmap!=null){captured=GlassFrame(bitmap,result.getInt("width"),result.getInt("height"),result.getLong("stamp"));note="Outgoing frame captured"}
    else note=result?.getString("error")?:note
   }catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
   val f=captured;val message=note;main.post{done(f,message)}
  }
 }
 fun foldSetting(action:String,original:String?,done:(Bundle)->Unit){executor.execute{
  val p=Parcel.obtain();val r=Parcel.obtain();val response=try{
   p.writeInterfaceToken(GlassCapture.TOKEN);p.writeString(action);if(action=="restore")p.writeString(original)
   LiveAngles.captureBinder().transact(2,p,r,0);r.readException();r.readBundle(javaClass.classLoader)?:Bundle()
  }catch(e:Exception){Bundle().apply{putString("error",e.message)}}finally{p.recycle();r.recycle()}
  main.post{done(response)}
 }}
}
