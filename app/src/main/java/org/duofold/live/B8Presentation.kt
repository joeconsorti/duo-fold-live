package org.duofold.live

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*

/** One bounded scene owns both logical windows across a physical primary exchange.
 * Its outgoing image is captured before moving the task, never from the vacated Home.
 * Frame-commit acknowledgements are software evidence, not visible-pixel proof. */
internal object B8Presentation {
 private val main=Handler(Looper.getMainLooper())
 private var session=0L
 private var latestSession=0L
 private var blocked=0L
 private var lastUpdate=0L
 private var started=0L
 private var source=""
 private var task=-1
 private var homeScene=false
 private var workspaceScene=false
 private var workspaceTasks=intArrayOf()
 private fun nativeManaged()=homeScene||workspaceScene
 private var pauseReturnAt=0L
 private var pauseFadeAt=0L
 private fun pausePose(visual:Float,inner:Boolean)=HingePausePolicy.pose(visual,inner,sessionOpenAngle,HingePausePolicy.fraction(pauseReturnAt,SystemClock.elapsedRealtime(),HingePausePolicy.EASE_MS))
 private fun pauseAnimating()=pauseReturnAt>0
 private fun pauseOpacity()=1f-HingePausePolicy.fraction(pauseFadeAt,SystemClock.elapsedRealtime(),HingePausePolicy.FADE_MS)
 private var pending=false
 private var retryAt=0L
 private var captureDisplay=-1
 private var capturePhysical=""
 private var capturedPhysical=""
 private var capturedRevision=-1
 private val incomingGate=B8IncomingCapturePolicy()
 private var incomingGateKey=""
 private var incomingDecision="not sampled"
 private var probedCheck=0L
 private var retainedCheck=0L
 private var primaryCheck=0L
 private var readyCheck=0L
 private var readyAt=0L
 private var readyStartedAt=0L
 private var readyPhysical=""
 private var readinessPending=false
 private var readinessRetryAt=0L
 private var readinessNote=""
 private val panelShapes=HashMap<String,Pair<Int,Int>>()
 private var revision=0
 private var liveRevision=-1
 private var liveMinStamp=0L
 private var livePhysical=""
 private var livePending=false
 private var pauseCapture=false
 private var probeAllowed=false
 private var probeEpoch=0L
 private var animationEnabled=false
 private var sessionOpenAngle=172f
 private val animationStarts=HashMap<String,B8AnimationStartPolicy>()
 private var snapshots=false
 private var liveRetryAt=0L
 private var original:GlassFrame?=null
 private val heldPanels=HashMap<String,GlassFrame>()
 private val homePreviews=HashMap<String,HomePreview>()
 private interface Panel {
  val panelId:Int;val committedPhysical:String;val committedAt:Long;val committedFrame:GlassFrame?
  fun show();fun close();fun invalidate();fun updateState();fun validCommit(now:Long):Boolean
  fun committedAnimation():Boolean;fun nativeImageCommitted():Boolean;fun probe();fun diagnostic():String
 }
 private val windows=ArrayList<Panel>()
 private var direct=false;private var directMapped=false;private var directStage="";private var layersPending=false
 private val layerWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
 @Volatile private var acknowledgement=Bundle()
 private var status="Coordinated B8 presentation idle"
 private var retained=""
 private var diagnosticAt=0L
 fun ownsRendering()=session!=0L // Suppress stale legacy content from source preparation onward.
 @JvmStatic fun acknowledgement():Bundle=Bundle(acknowledgement)
 @JvmStatic fun report()=status+"\nSession standard Windowed Glass: $animationEnabled; normal shader and saved controls; backend=${if(direct) "independent moving layers; no handoff shield" else "legacy logical windows"}; moving captures enabled on direct path\nIncoming capture: "+incomingDecision+"\n"+HomeFrames.status+"\n"+WorkspaceFrames.status+"\n"+retained+"\n"+CompositionDiagnostic.report()+"\n"+WallpaperVisibilityProbe.report()+"\n"+FoldThermalTrace.report()
 private val guard=object:Runnable{override fun run(){
  if(session==0L)return
  val host=StandaloneService.instance
  val now=SystemClock.elapsedRealtime()
  if(host==null||!allowed(host)||now-lastUpdate>750||now-started>=30000){blocked=session;clear("lease/lock/timeout");return}
  main.postDelayed(this,100)
 }}
 private fun allowed(context:Context):Boolean {
  val prefs=context.getSharedPreferences("standalone",0)
  return prefs.getBoolean("enabled",false)&&prefs.getBoolean("live_b8_enabled",false)&&!prefs.getBoolean("dual",false)&&LiveAngles.effectAllowed&&LiveAngles.fresh()&&context.getSystemService(PowerManager::class.java).isInteractive&&!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
 }
 @JvmStatic fun clear(reason:String){
  val was=session
  if(was!=0L&&homeScene)HomeInnerDiagnostic.end(was,reason)
  // Keep earlier fast/slow fold decisions alongside their duration, beyond the
  // short lifecycle log. Unknown transient logical mappings are not panels.
  FoldThermalTrace.end(StandaloneService.instance,reason,animationStarts.entries.filter{PanelIdentityPolicy.known(it.key)}.sortedBy{it.key}.joinToString("; "){"${it.key}=${it.value.status()}"})
  if(was!=0L){retained=windows.joinToString("\n"){it.diagnostic()};RecoveryLog.add("B8 final readiness session=$was: $retained")}
  if(was!=0L&&!workspaceScene&&HandoffVisualPolicy.ANIMATION_ENABLED){val finalTask=task;val physical=PanelIdentity.identity(StandaloneService.instance?.getSystemService(DisplayManager::class.java)?.getDisplay(0));main.postDelayed({if(session==0L)WallpaperVisibilityProbe.sample(was,"AFTER_CLEANUP",finalTask,physical,0)},200)}
  pauseReturnAt=0;pauseFadeAt=0;layersPending=false;probeAllowed=false;probeEpoch++;session=0;primaryCheck=0;retainedCheck=0;probedCheck=0;readyCheck=0;readyAt=0;readyPhysical="";readyStartedAt=0;readinessPending=false;readinessRetryAt=0;capturedRevision=-1;capturedPhysical="";incomingGateKey="";liveRetryAt=0;pending=false;livePending=false;livePhysical="";liveRevision=-1;acknowledgement=Bundle();main.removeCallbacks(guard)
  windows.forEach{it.close()};windows.clear();homePreviews.values.forEach{it.close()};homePreviews.clear();animationStarts.clear();if(was!=0L&&snapshots)StandaloneService.instance?.onFrame(false,0f);original=null;heldPanels.clear();GlassFrames.b8CapturePause(false)
  status="Coordinated B8 presentation released: $reason"
  if(was!=0L)RecoveryLog.add("B8 PRESENTATION_RELEASE session=$was reason=$reason")
 }
 @JvmStatic fun update(context:Context,info:Bundle?){
  GlassFrames.configure(context)
  B8PanelGeometry.initialize(context)
  snapshots=context.getSharedPreferences("standalone",0).getBoolean("b8_snapshot_test",false)
  val next=info?.getLong("session")?:0L
  val host=StandaloneService.instance
  if(next==0L||host==null||!allowed(context)) {if(session!=0L)clear("controller/setting ended");GlassFrames.b8CapturePause(info?.getBoolean("pauseCapture")==true);return}
  if(next==blocked)return
  direct=info?.getBoolean("direct")==true;directMapped=info?.getBoolean("sceneMapped")==true;directStage=info?.getString("directStage","")?:""
  if(next!=session){
   clear("new session");animationEnabled=context.getSharedPreferences("standalone",0).getBoolean(HandoffVisualPolicy.KEY,false);incomingDecision="not sampled";session=next;latestSession=next;homeScene=info!!.getBoolean("home",false);workspaceScene=info.getBoolean("workspace",false);workspaceTasks=info.getIntArray("workspaceTasks")?:intArrayOf()
   HomeInnerDiagnostic.stop("new coordinated session")
   CompositionDiagnostic.pauseForAnimation(context,animationEnabled&&!direct)
   if(!animationEnabled||direct){val armed=CompositionDiagnostic.begin(context,next,homeScene&&direct);if(armed&&homeScene&&direct)HomeInnerDiagnostic.begin(context,next)}
   FoldThermalTrace.begin(context,next);started=SystemClock.elapsedRealtime();source=info.getString("source","");task=info.getInt("task",-1);retryAt=0
   sessionOpenAngle=context.getSharedPreferences("standalone",0).getFloat("open_threshold",172f)
   main.postDelayed(guard,100)
   if(snapshots){host.onFrame(false,0f);RecoveryLog.add("B8 LEGACY_HOST_SUPPRESSED session=$next; preparation through cleanup")}
  }
  // Home can be recreated by Android at remap. Only the shell's verified
  // focused Home identity may replace its task; app routes keep their exact task.
  val nextTasks=info?.getIntArray("workspaceTasks")?:intArrayOf()
  if(nativeManaged()&&(task!=info?.getInt("task",-1)||!workspaceTasks.contentEquals(nextTasks))){
   task=info?.getInt("task",-1)?:-1;workspaceTasks=nextTasks;readyAt=0;readyCheck=0;retainedCheck=0;readinessRetryAt=0
  }
  pauseReturnAt=info?.getLong("pauseReturnAt")?:0L;pauseFadeAt=info?.getLong("pauseFadeAt")?:0L
  FoldThermalTrace.sample(context)
  lastUpdate=SystemClock.elapsedRealtime()
  // Observe travel even before capture/window creation, so a quick reversal cannot
  // make a missed startup window appear eligible again.
  val dm=host.getSystemService(DisplayManager::class.java)
  for(id in 0..1){val physical=PanelIdentity.identity(dm.getDisplay(id));if(PanelIdentityPolicy.known(physical))animationStart(physical).forFrame(LiveAngles.currentAngle())}
  val allowProbe=info?.getBoolean("compositionProbeAllowed")==true
  if(allowProbe!=probeAllowed){probeAllowed=allowProbe;probeEpoch++}
  revision=info?.getInt("revision")?:0
  captureDisplay=info?.getInt("captureDisplay",-1)?:-1;capturePhysical=info?.getString("capturePhysical","")?:""
  pauseCapture=info?.getBoolean("pauseCapture")==true
  GlassFrames.b8CapturePause(pauseCapture)
  if(pauseReturnAt==0L&&original==null&&!pending&&lastUpdate>=retryAt&&task>=0){
   pending=true;val token=session;val requestedTasks=workspaceTasks.clone()
   captureScene(task,source,0){frame,note,retryDelay->
    if(token!=session||pauseReturnAt>0){discardUnpublished(frame);return@captureScene}
    pending=false
    if(!requestedTasks.contentEquals(workspaceTasks)){discardUnpublished(frame);return@captureScene}
    if(frame==null||frame.physicalId!=source){if(homeScene)discardUnpublished(frame);status="B8 presentation waiting for exact outgoing app: $note";retryAt=SystemClock.elapsedRealtime()+(retryDelay?:100);return@captureScene}
    original=frame;heldPanels[source]=frame;livePhysical=source;liveRevision=revision;liveMinStamp=frame.stamp
    panelShapes[source]=minOf(frame.width,frame.height) to maxOf(frame.width,frame.height)
    attachPanels(host,token,frame)
    windows.forEach{it.invalidate()} // Wake already-prepared renderers on this callback, not the next angle poll.
    RecoveryLog.add("B8 SOURCE_CAPTURE_READY session=$token elapsed=${SystemClock.elapsedRealtime()-started}ms; selected-app display capture, not optical proof")
   }
  }
  // Physical roots, GPU buffers and the shader need no screenshot. Prepare them
  // concurrently with the guarded capture; roots stay hidden until the first draw.
  if(direct&&directMapped||original!=null)attachPanels(host,session,original)
  val selected=dm.getDisplay(captureDisplay);val identity=PanelIdentity.identity(selected)
  val destination=(captureDisplay==1||nativeManaged()&&captureDisplay==0)&&identity==capturePhysical
  val needsCapture=if(destination)capturedRevision!=revision||capturedPhysical!=identity else captureDisplay==0&&!snapshots&&!pauseCapture&&(liveRevision!=revision||livePhysical!=identity)
  val gateKey="$session:$revision:$captureDisplay:$capturePhysical"
  if(gateKey!=incomingGateKey){incomingGateKey=gateKey;incomingGate.reset(lastUpdate);liveRetryAt=0}
  if(pauseReturnAt==0L&&original!=null&&needsCapture&&!livePending&&lastUpdate>=liveRetryAt&&PanelIdentityPolicy.known(identity)){
   livePending=true;val token=session;val rev=revision;val displayId=captureDisplay;val requestKey=gateKey;val selectedTask=task;val requestedRotation=selected?.rotation;val captureRequestedAt=SystemClock.elapsedRealtime()
   captureScene(task,identity,displayId){frame,note,retryDelay->
    if(token!=session||pauseReturnAt>0){discardUnpublished(frame);return@captureScene}
    livePending=false
    if(requestKey!=incomingGateKey||rev!=revision||selectedTask!=task){discardUnpublished(frame);return@captureScene}
    if(requestKey==incomingGateKey&&rev==revision&&frame!=null&&frame.physicalId==identity&&PanelIdentity.identity(dm.getDisplay(displayId))==identity&&frame.rotation==requestedRotation&&dm.getDisplay(displayId)?.rotation==requestedRotation){
     if(destination&&frame.homeContent==null){
      val now=SystemClock.elapsedRealtime()
      val decision=incomingGate.evaluate(frame.darkSamples,now)
      incomingDecision="$decision attempts=${incomingGate.attempts()} darkSamples=${frame.darkSamples}/1024 routeElapsedMs=${incomingGate.elapsed(now)} captureMs=${now-captureRequestedAt} sessionElapsedMs=${now-started}"
      RecoveryLog.add("B8 INCOMING_CAPTURE_DECISION session=$token revision=$rev physical=$identity stamp=${frame.stamp} $incomingDecision; capture evidence only")
      if(decision==B8IncomingCapturePolicy.RETRY){
       discardUnpublished(frame);liveRetryAt=now+incomingGate.retryDelayMs();publish();return@captureScene
      }
     }
     if(homeScene&&frame.homeContent!=null)incomingDecision=frame.exclusions
     heldPanels[identity]=frame;livePhysical=identity;liveRevision=rev;liveMinStamp=frame.stamp
     if(destination){capturedRevision=rev;capturedPhysical=identity;if(!workspaceScene&&HandoffVisualPolicy.ANIMATION_ENABLED)WallpaperVisibilityProbe.sample(token,"INCOMING_CAPTURE",task,identity,displayId,true)}
     windows.forEach{it.invalidate()}
     RecoveryLog.add("B8 INCOMING_APP_DRAWN session=$token revision=$rev physical=$identity display=$displayId; before shield, not optical proof")
    }else{discardUnpublished(frame);liveRetryAt=SystemClock.elapsedRealtime()+(retryDelay?:incomingGate.emptyRetryDelayMs());RecoveryLog.add("B8 retaining app image; incoming pending: $note")}
   }
  }
  val nextCheck=info?.getLong("primaryCheck")?:0L
  if(nextCheck!=primaryCheck){retainedCheck=0;readinessRetryAt=0;readyCheck=0;readyAt=0;readyStartedAt=0;readyPhysical=""}
  primaryCheck=nextCheck
  val readyTarget=info?.getString("primaryCheckPhysical","")?:""
  val d=dm.getDisplay(0)
  if(primaryCheck>0&&!readinessPending&&lastUpdate>=readinessRetryAt&&d?.state==Display.STATE_ON&&PanelIdentity.identity(d)==readyTarget&&(pauseReturnAt>0||!nativeManaged()||capturedRevision==revision&&capturedPhysical==readyTarget)){
   val size=B8PanelGeometry.size(d);val token=session;val check=primaryCheck;val selectedTask=task;val selectedTasks=workspaceTasks.clone();val rotation=d.rotation;val requestedAt=SystemClock.elapsedRealtime();readinessPending=true
   GlassFrames.incomingReadyTimed(task,readyTarget,size.x,size.y,if(workspaceScene)selectedTasks else null){ok,note,observedAt->
    if(token!=session)return@incomingReadyTimed
    readinessPending=false
    // An old in-flight result must not delay or acknowledge a replacement check.
    if(check!=primaryCheck||selectedTask!=task||!selectedTasks.contentEquals(workspaceTasks)){readinessRetryAt=0;return@incomingReadyTimed}
    val current=dm.getDisplay(0);val shape=current?.let{B8PanelGeometry.size(it)}?:Point()
    val retained=heldPanels[readyTarget]
    val matchingSnapshot=retained!=null&&B8SnapshotPolicy.matches(
     readyTarget,size.x,size.y,rotation,retained.physicalId,retained.width,retained.height,retained.rotation)
    val valid=ok&&check==primaryCheck&&current?.state==Display.STATE_ON&&current.rotation==rotation&&shape==size&&PanelIdentity.identity(current)==readyTarget&&(pauseReturnAt>0||!snapshots||matchingSnapshot)
    // The alpha.32 image bundle showed wallpaper baked into PRIMARY_SNAPSHOT_REFRESH
    // captures during remap, despite explicit exclusions. Keep the pre-shield native
    // panel image. HAS_DRAWN is still checked after remap; it is not image cleanliness.
    // Missing/wrong geometry never falls back to a transition-time display capture.
    if(valid&&pauseReturnAt==0L&&snapshots&&retainedCheck!=check){
     retainedCheck=check
     RecoveryLog.add(if(workspaceScene) "B8 WORKSPACE_PRIMARY_SNAPSHOT session=$token check=$check physical=$readyTarget tasks=${workspaceTasks.contentToString()} stamp=${retained?.stamp}; guarded native workspace capture after remap; software evidence only" else if(homeScene) "B8 HOME_PRIMARY_SNAPSHOT session=$token check=$check physical=$readyTarget task=$task stamp=${retained?.stamp}; guarded native Home capture after remap; software evidence only" else "B8 PRIMARY_SNAPSHOT_RETAINED session=$token check=$check physical=$readyTarget stamp=${retained?.stamp}; matching pre-shield image; no remap recapture; software evidence only")
    }
    readinessRetryAt=SystemClock.elapsedRealtime()+50
    readyCheck=check;readyAt=if(valid)observedAt else 0;readyStartedAt=if(valid)requestedAt else 0;readyPhysical=if(valid)readyTarget else ""
    val observation="check=$check physical=$readyTarget ready=$valid window=$note snapshotMatches=$matchingSnapshot"
    if(observation!=readinessNote){readinessNote=observation;RecoveryLog.add("B8 PRIMARY_APP_WINDOW session=$token $observation")}
    if(valid&&!workspaceScene&&probedCheck!=check&&HandoffVisualPolicy.ANIMATION_ENABLED){
     probedCheck=check;val selectedTask=task;val stage="AFTER_READY_$check"
     RecoveryLog.add("B8 PIXEL_PROBE_SCHEDULED session=$token stage=$stage delayMs=200")
     main.postDelayed({
      // primaryCheck intentionally becomes zero after reveal. It is not a probe lease.
      // Permit cleanup of this episode, but never capture a subsequent episode as this one.
      if(!B8ProbePolicy.accepts(token,latestSession))WallpaperVisibilityProbe.skipped(token,stage,"superseded session=$session")
      else WallpaperVisibilityProbe.sample(token,stage+"_VISIBLE",selectedTask,readyTarget,0){
       if(!B8ProbePolicy.accepts(token,latestSession))WallpaperVisibilityProbe.skipped(token,stage+"_UNDERLAY","superseded session=$session")
       else WallpaperVisibilityProbe.sample(token,stage+"_UNDERLAY",selectedTask,readyTarget,0,true)
      }
     },200)
    }
    publish()
   }
  }
  windows.toList().forEach{it.updateState();it.probe()}
  publish()
 }
 private fun captureScene(task:Int,physical:String,display:Int,done:(GlassFrame?,String,Long?)->Unit){
  val host=StandaloneService.instance
  if(workspaceScene){
   if(display!=0){done(null,"Multitask layout stays on native primary",100);return}
   if(host!=null&&org.duofold.live.wallpaperlayer.WallpaperRestore.enabled(host)){
    WorkspaceFrames.incoming(workspaceTasks,physical,animationEnabled){frame,note,retry->done(frame,note,retry)}
   }else GlassFrames.incoming(task,physical,display,animationEnabled,includeWallpaper=true,workspaceTasks=workspaceTasks.clone()){frame,note->done(frame,note,null)}
  }else if(homeScene&&host!=null&&org.duofold.live.wallpaperlayer.WallpaperRestore.enabled(host)){
   if(display!=0){done(null,"Home remains under Android control on D0",100);return}
   HomeFrames.incoming(task,physical,animationEnabled,original?.homeContent){frame,note,retry->done(frame,note,retry)}
  }else GlassFrames.incoming(task,physical,display,animationEnabled,includeWallpaper=homeScene){frame,note->done(frame,note,null)}
 }
 private fun homePreviewAnimating(physical:String)=homeScene&&homePreviews[physical]?.animating()==true
 private fun drawScene(canvas:Canvas,frame:GlassFrame,physical:String,w:Int,h:Int,rotation:Int,raw:Float,normal:(GlassFrame,Boolean)->Unit){
  val sourceFrame=original
  if(sourceFrame==null||!HomePreviewPolicy.eligible(homeScene,sourceFrame.width,sourceFrame.height,w,h)){normal(frame,false);return}
  var preview=homePreviews[physical]
  if(preview==null||preview.width!=w||preview.height!=h||preview.rotation!=rotation){
   preview?.close();preview=HomePreview(sourceFrame,w,h,rotation);homePreviews[physical]=preview
  }
  val native=B8SnapshotPolicy.matches(physical,w,h,rotation,frame.physicalId,frame.width,frame.height,frame.rotation)
  preview.draw(canvas,frame,native,raw,sessionOpenAngle){image->normal(image,true)}
 }
 // Only frames that have never been submitted to a renderer may be recycled here.
 private fun discardUnpublished(frame:GlassFrame?){frame?:return;(frame.levels+frame.bitmap+listOfNotNull(frame.homeLayer)).distinct().forEach{if(!it.isRecycled)it.recycle()}}
 private fun attachPanels(host:StandaloneService,token:Long,frame:GlassFrame?){
  if(direct){
   if(windows.isNotEmpty()||layersPending)return
   layersPending=true;val preparationStarted=SystemClock.elapsedRealtime()
   layerWorker.execute{
    var bundle:Bundle?=null;var failure:String?=null
    val p=Parcel.obtain();val r=Parcel.obtain()
    try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);p.writeLong(token);LiveAngles.previewCommand(10,p,r);r.readException();bundle=r.readBundle(SurfaceControl::class.java.classLoader)}catch(e:Exception){failure=e.javaClass.simpleName}finally{p.recycle();r.recycle()}
    val reply=bundle;val problem=failure
    main.post{
     if(token!=session){for(n in 0..1)reply?.getBundle("p$n")?.getParcelable("root",SurfaceControl::class.java)?.release();return@post}
     layersPending=false
     if(problem!=null){blocked=token;clear("independent scene unavailable: $problem");return@post}
     if(reply==null)return@post
     try{for(n in 0..1){val row=reply.getBundle("p$n")?:error("missing physical root");val panel=DirectPanel(host,token,row);row.remove("root");windows.add(panel);panel.show()}
      RecoveryLog.add("B8 RENDERERS_PREPARED session=$token elapsedMs=${SystemClock.elapsedRealtime()-started} preparationMs=${SystemClock.elapsedRealtime()-preparationStarted} capturePending=${original==null}; hidden until fresh content submission")}
     catch(e:Exception){for(n in 0..1)reply.getBundle("p$n")?.getParcelable("root",SurfaceControl::class.java)?.release();blocked=token;clear("independent scene attachment: ${e.javaClass.simpleName}")}
    }
   }
   return
  }
  frame?:return
  try{val dm=host.getSystemService(DisplayManager::class.java)
   // Learn both shapes before the first window chooses its shared raster extent.
   for(id in 0..1){val d=dm.getDisplay(id)?:continue;val identity=PanelIdentity.identity(d)
    if(d.state!=Display.STATE_ON||!PanelIdentityPolicy.known(identity)||windows.any{it.panelId==id})continue
    check(BuiltInPanel.accepts(d));val size=B8PanelGeometry.size(d);panelShapes[identity]=minOf(size.x,size.y) to maxOf(size.x,size.y)
   }
   for(id in 0..1){if(windows.any{it.panelId==id})continue;val d=dm.getDisplay(id)?:continue;val identity=PanelIdentity.identity(d)
    if(d.state!=Display.STATE_ON||!PanelIdentityPolicy.known(identity))continue
    check(BuiltInPanel.accepts(d));val size=B8PanelGeometry.size(d);panelShapes[identity]=minOf(size.x,size.y) to maxOf(size.x,size.y)
    val w=PanelWindow(host,d,token,frame);windows.add(w);w.show()
   }
  }catch(e:Exception){blocked=token;clear("attachment failed: ${e.message}")}
 }
 private fun shapeMatches(identity:String,w:Int,h:Int):Boolean {val shape=panelShapes[identity]?:return false;return B8RenderPolicy.matchesPanelShape(shape.first,shape.second,w,h)}
 private fun animationStart(identity:String)=animationStarts.getOrPut(identity){B8AnimationStartPolicy(animationEnabled)}
 private fun publish(){
  val now=SystemClock.elapsedRealtime()
  if(session!=0L&&now>=diagnosticAt){diagnosticAt=now+1000;retained=windows.joinToString("\n"){it.diagnostic()};RecoveryLog.add("B8 readiness session=$session: $retained")}
  val both=windows.size==2&&windows.all{it.validCommit(now)}
  val primary=windows.firstOrNull{it.panelId==0&&it.validCommit(now)}
  val captured=windows.firstOrNull{it.validCommit(now)&&it.committedPhysical==capturedPhysical&&it.committedFrame?.physicalId==capturedPhysical&&it.committedFrame===heldPanels[capturedPhysical]}
  acknowledgement=Bundle().apply{putLong("primaryCheck",readyCheck);putLong("readyAt",if(pauseReturnAt>0||!snapshots||(retainedCheck==readyCheck&&primary?.committedFrame===heldPanels[readyPhysical]))readyAt else 0L);putLong("readyStartedAt",readyStartedAt);putString("readyPhysical",readyPhysical);putString("capturedPhysical",if(captured!=null)capturedPhysical else "");putInt("capturedRevision",if(captured!=null)capturedRevision else -1);putLong("session",session);putBoolean("capturePaused",GlassFrames.b8CaptureQuiescent()&&!livePending&&!pending);putBoolean("prepared",both);putString("primary",primary?.committedPhysical?:"");putLong("commitAt",primary?.committedAt?:0);putBoolean("animated",primary?.committedAnimation()==true);putBoolean("nativePrimary",primary?.nativeImageCommitted()==true)}
  if(both)status="B8 ${if(snapshots) "retained screenshots" else "live app + glass"} committed on both panels; outgoing held to endpoint; session=$session"
 }
 private class PanelWindow(host:StandaloneService,display:Display,private val token:Long,private val outgoing:GlassFrame):Panel {
  override val panelId=display.displayId
  private val ctx=host.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null)
  private val wm=ctx.getSystemService(WindowManager::class.java)
  private val dm=host.getSystemService(DisplayManager::class.java)
  private val prefs=host.getSharedPreferences("standalone",0)
  private var painter:FrostSurface?=null
  private fun effects():FrostSurface=painter?:FrostSurface(ctx,standardAnimation=true,workspaceScene=workspaceScene).also{painter=it;it.layout(0,0,layoutWidth,layoutHeight)}
  private var attached=false
  private var revealed=false
  private var committedAnimated=false
  override var committedPhysical="";private set
  override var committedAt=0L;private set
  override var committedFrame:GlassFrame?=null;private set
  private var committedWidth=0;private var committedHeight=0;private var committedRotation=-1
  private val submissions=B8FrameSubmission();private var registeredAt=0L
  private var coalescedDraws=0;private var commitTimeouts=0
  private var committedRole=""
  private var committedAngle=Float.NaN
  private var draws=0;private var commits=0;private var excluded=false;private var childExcluded=false
  private var maxDrawMs=0L;private var maxCommitMs=0L;private var maxMovingGapMs=0L;private var wasMoving=false
  private var rejection="not drawn";private var visual=Float.NaN;private var drawnAt=0L
  private var held=outgoing;private var requestedAngle=Float.NaN
  private val bufferCapacity=B8BufferCapacity()
  private var bufferChanges=0
  private var mappingChangedAt=0L
  private var lastDrawPhysical=""
  private var bufferWidth=0;private var bufferHeight=0;private var layoutWidth=0;private var layoutHeight=0;private var queued=false
  private val redraw=Runnable{queued=false;drawBuffer()}
  private val motion=ScreenshotMotionPolicy()
  private val tilt=FloatArray(2)
  private val motionListener:()->Unit={
   if(attached&&animationEnabled){
    val d=dm.getDisplay(panelId);val size=d?.let{B8PanelGeometry.size(it)}?:Point()
    MotionGlass.offset(d?.rotation?:0,tilt,minOf(size.x,size.y).toFloat()/maxOf(1,maxOf(size.x,size.y))>.7f)
    if(motion.update(tilt[0],tilt[1]))schedule()
   }
  }
  private val view=object:SurfaceView(ctx){
   init{setZOrderOnTop(true);holder.setFormat(PixelFormat.OPAQUE);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
   override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);invalidateCommit();resize(w,h);schedule()}
  }
  init{view.holder.addCallback(object:SurfaceHolder.Callback{
   override fun surfaceCreated(h:SurfaceHolder){invalidateCommit();GlassFrames.surface(this@PanelWindow,view.surfaceControl);childExcluded=view.surfaceControl.isValid;runCatching{h.surface.setFrameRate(displayRefreshRate(),Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)};resize(view.width,view.height);schedule()}
   override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,height:Int){invalidateCommit();GlassFrames.surface(this@PanelWindow,view.surfaceControl);childExcluded=view.surfaceControl.isValid;schedule()}
   override fun surfaceDestroyed(h:SurfaceHolder){view.removeCallbacks(redraw);queued=false;childExcluded=false;submissions.surfaceDestroyed();invalidateCommit();GlassFrames.surface(this@PanelWindow,null)}
  })}
  private fun schedule(delay:Long=0){
   if(!attached||queued)return
   queued=true
   val wait=maxOf(delay,B8RenderPolicy.frameDelay(SystemClock.elapsedRealtime(),drawnAt))
   if(wait==0L)view.postOnAnimation(redraw) else view.postDelayed(redraw,wait)
  }
  override fun invalidate(){schedule()}
  private var observedPhysical=""
  private var observedWidth=0;private var observedHeight=0;private var observedRotation=-1;private var observedState=-1
  override fun updateState(){
   val d=dm.getDisplay(panelId)
   val size=d?.let{B8PanelGeometry.size(it)}?:Point()
   val physical=PanelIdentity.identity(d);val rotation=d?.rotation?:-1;val state=d?.state?:-1
   // Detect remaps even if dimensions are unchanged or an OFF/ON cycle reuses the surface.
   if(physical!=observedPhysical||size.x!=observedWidth||size.y!=observedHeight||rotation!=observedRotation||state!=observedState){
    mappingChangedAt=SystemClock.elapsedRealtime()
    RecoveryLog.add("B8 PANEL_MAPPING session=$token D$panelId from=$observedPhysical to=$physical size=${size.x}x${size.y} state=$state; awaiting matching frame")
    observedPhysical=physical;observedWidth=size.x;observedHeight=size.y;observedRotation=rotation;observedState=state;invalidateCommit()
    resize(view.width,view.height)
   }
   val now=SystemClock.elapsedRealtime()
   if(revealed){val lp=view.layoutParams as WindowManager.LayoutParams;val alpha=pauseOpacity();if(lp.alpha!=alpha){lp.alpha=alpha;wm.updateViewLayout(view,lp)}}
   expireSubmission(now)
   val angle=LiveAngles.currentAngle();val changed=angle!=requestedAngle;requestedAngle=angle
   val animate=animationStart(physical).forFrame(angle)
   // Controller updates remain the liveness heartbeat. Static pixels need no periodic submission.
   // During a primary readiness check, keep the existing controller's fresh-commit gate intact.
   if(state==Display.STATE_ON&&(B8RenderPolicy.shouldSubmit(animate,changed,
      validCommit(now),submissions.pending(),now-committedAt,panelId==0&&primaryCheck>0)||(homePreviewAnimating(physical)||pauseAnimating())&&!submissions.pending()))schedule()
  }
  private fun invalidateCommit(){committedAt=0;committedFrame=null;submissions.invalidate()}
  private fun expireSubmission(now:Long){if(submissions.expire(now)){commitTimeouts++;invalidateCommit();rejection="buffer commit callback timeout"}}
  private fun resize(w:Int,h:Int){
   if(w<=0||h<=0)return
   if(w!=layoutWidth||h!=layoutHeight){layoutWidth=w;layoutHeight=h;painter?.layout(0,0,w,h)}
   bufferCapacity.include(GlassEffectSettings.bufferDimension(prefs,w),GlassEffectSettings.bufferDimension(prefs,h))
   // Warm both extents before promotion. Canvas and SurfaceView apply inverse
   // axis scales, so this capacity does not change logical geometry or aspect.
   // Unknown/transient mappings cannot contribute a new size.
   for(id in 0..1){val panel=dm.getDisplay(id)?:continue;val physical=PanelIdentity.identity(panel)
    if(!PanelIdentityPolicy.known(physical))continue
    val size=B8PanelGeometry.size(panel)
    if(!shapeMatches(physical,size.x,size.y))continue
    bufferCapacity.include(GlassEffectSettings.bufferDimension(prefs,size.x),GlassEffectSettings.bufferDimension(prefs,size.y))
   }
   val bw=bufferCapacity.width();val bh=bufferCapacity.height()
   if(bw!=bufferWidth||bh!=bufferHeight){bufferWidth=bw;bufferHeight=bh;bufferChanges++;invalidateCommit();view.holder.setFixedSize(bw,bh)
    RecoveryLog.add("B8 BUFFER_CAPACITY session=$token D$panelId raster=${bw}x$bh changes=$bufferChanges; retained through panel exchanges")}
  }
  private fun drawBuffer(){
   if(session!=token||!attached||!view.holder.surface.isValid||view.width<=0||view.height<=0)return
   val now=SystemClock.elapsedRealtime();expireSubmission(now)
   // Never acquire another canvas while its predecessor is awaiting a callback.
   // Invalidations during a remap retain this gate; the callback drains it even
   // when that old frame can no longer acknowledge the new mapping.
   if(submissions.pending()){coalescedDraws++;return}
   val d=dm.getDisplay(panelId);val identity=PanelIdentity.identity(d)
   if(d?.state!=Display.STATE_ON){invalidateCommit();rejection="display not ON";return}
   val size=B8PanelGeometry.size(d);val w=view.width;val h=view.height
   // A remap may pair the new identity with the old layout. Never relearn panel shape here.
   val coherent=shapeMatches(identity,w,h)&&w==size.x&&h==size.y
   // Do not replace the retained buffer with black while logical displays remap.
   // Unknown identity/old layout also invalidates readiness; retention is not a new commit.
   if(!B8RenderPolicy.canAcquire(coherent,excluded,childExcluded)){
    invalidateCommit();rejection="retaining buffer during panel/layout/exclusion remap";schedule(16);return
   }
   val motionRevision=motion.revision()
   var rendered=false
   var animate=false
   var submittedTicket=0L
   try{val canvas=view.holder.lockHardwareCanvas()
    try{draws++
     val exactBuffer=canvas.width==bufferWidth&&canvas.height==bufferHeight
     // A buffer resize can finish between preflight and lock. Fill that acquired
     // canvas with app content, but never acknowledge its mismatched geometry.
     if(!exactBuffer){invalidateCommit();rejection="resize buffer filled; awaiting exact geometry"}
     run{
      held=heldPanels[identity]?:outgoing
      val live=GlassFrames.frame
      if(pauseReturnAt==0L&&!snapshots&&panelId==0&&!pauseCapture&&liveRevision==revision&&livePhysical==identity&&live!=null&&live.stamp>=liveMinStamp&&live.physicalId==identity&&live.displayId==0&&live.rotation==d.rotation&&live.width==size.x&&live.height==size.y&&now-live.stamp in 0..250){held=live;heldPanels[identity]=live}
      val raw=LiveAngles.currentAngle();val inner=minOf(w,h).toFloat()/maxOf(w,h)>.7f
      animate=animationStart(identity).forFrame(raw)
      // A new panel starts at the current hinge pose, never at an old endpoint.
      // There is no speed or elapsed-time veto on starting the animation.
      if(!revealed||!animate)visual=raw
      visual=FrameSmoothing.start(visual,raw,inner,prefs.getBoolean("window_reveal_v2",true),prefs.getBoolean("startup_easing",true))
      val movingGap=B8RenderPolicy.movingGap(now,drawnAt,wasMoving)
      if(movingGap>maxMovingGapMs&&movingGap>=100)RecoveryLog.add("B8 MOVING_GAP session=$token D$panelId from=$lastDrawPhysical to=$identity gapMs=$movingGap priorReason=$rejection; software draw gap")
      maxMovingGapMs=maxOf(maxMovingGapMs,movingGap);lastDrawPhysical=identity
      visual=FrameSmoothing.step(visual,raw,if(drawnAt==0L)8.33f else (now-drawnAt).toFloat().coerceIn(1f,50f),prefs.getFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS));drawnAt=now
      wasMoving=animate&&B8RenderPolicy.needsAnimation(visual,raw)
      canvas.scale(canvas.width.toFloat()/w,canvas.height.toFloat()/h)
      drawScene(canvas,held,identity,w,h,d.rotation,raw){image,fullWidthHome->
       if(!animate)drawScreenshotBackdrop(canvas,image,w,h,image.physicalId!=identity) else {val pose=pausePose(visual,inner);effects().configureStandardSnapshot(image,if(pauseReturnAt>0)pose else raw,pose,inner,d.rotation);check(effects().drawComposite(canvas,image,fullWidthHome)){"Embedded glass draw failed"}}
      }
      // The source image is a temporary cross-panel presentation, never a native capture acknowledgement.
      if(exactBuffer){val frame=held;val rotation=d.rotation;val bw=bufferWidth;val bh=bufferHeight;val renderedAngle=visual;val animatedFrame=animate;val ticket=submissions.begin(now);submittedTicket=ticket;registeredAt=now
       SurfaceControl.Transaction().use{tx->tx.addTransactionCommittedListener(ctx.mainExecutor){
        val completed=submissions.complete(ticket)
        if(completed==B8FrameSubmission.Completion.OBSOLETE||session!=token||!attached)return@addTransactionCommittedListener
        if(completed==B8FrameSubmission.Completion.INVALIDATED){schedule();return@addTransactionCommittedListener}
        run{val current=dm.getDisplay(panelId);val shape=current?.let{B8PanelGeometry.size(it)}?:Point()
         if(current?.state==Display.STATE_ON&&PanelIdentity.identity(current)==identity&&current.rotation==rotation&&shape.x==w&&shape.y==h&&view.width==w&&view.height==h&&bufferWidth==bw&&bufferHeight==bh&&excluded&&childExcluded){
          val acceptedAt=SystemClock.elapsedRealtime()
          if(!animationStart(identity).committed(LiveAngles.currentAngle(),animatedFrame)){
           invalidateCommit();rejection="animation input changed before commit; redrawing current pose";schedule()
          }else{
           committedPhysical=identity;committedWidth=w;committedHeight=h;committedRotation=rotation;committedFrame=frame;committedAngle=renderedAngle;committedAnimated=animatedFrame;committedAt=acceptedAt
           maxCommitMs=maxOf(maxCommitMs,committedAt-registeredAt);commits++;rejection=""
           if(mappingChangedAt>0){RecoveryLog.add("B8 PANEL_MAPPING_COMMIT session=$token D$panelId physical=$identity mappingWaitMs=${acceptedAt-mappingChangedAt} bufferChanges=$bufferChanges; software evidence");mappingChangedAt=0}
           if(!revealed){
            val lp=view.layoutParams as WindowManager.LayoutParams;lp.alpha=1f
            try{wm.updateViewLayout(view,lp);revealed=true}catch(e:Exception){blocked=token;clear("panel reveal failed: ${e.javaClass.simpleName}");return@addTransactionCommittedListener}
            RecoveryLog.add("B8 PANEL_START session=$token physical=$identity mode=${animationStart(identity).status()} elapsedMs=${committedAt-started}; software commit")
           }
           val role=if(frame.physicalId==identity)"native-panel" else "source-placeholder"
           if(role!=committedRole){committedRole=role;RecoveryLog.add("B8 PANEL_IMAGE_COMMITTED session=$token physical=$identity role=$role image=${frame.physicalId} stamp=${frame.stamp}; software evidence")}
           if(commits==1)RecoveryLog.add("B8 FIRST_PANEL_COMMIT session=$token physical=$identity elapsed=${committedAt-started}ms buffer=${bw}x$bh; software commit")
           publish()
           // Consume the newest pose/image, including changes received while
           // this submission was pending. Do not replay queued hinge samples.
           if(!validCommit(acceptedAt)||(animatedFrame&&(!snapshots||B8RenderPolicy.needsAnimation(visual,LiveAngles.currentAngle())||motion.changedSince(motionRevision)))||homePreviewAnimating(identity)||pauseAnimating())schedule()
          }
         }else{committedAt=0;rejection="mapping changed before buffer commit";schedule()}
        }
       };view.applyTransactionToFrame(tx)}
      };rendered=exactBuffer
     }
    }finally{view.holder.unlockCanvasAndPost(canvas);maxDrawMs=maxOf(maxDrawMs,SystemClock.elapsedRealtime()-now)}
   }catch(e:Exception){submissions.complete(submittedTicket);invalidateCommit();rejection="buffer draw failed: ${e.javaClass.simpleName}"}
   if(!rendered)schedule(16)
  }
  override fun probe(){
   val frame=committedFrame?:return
   if(panelId==0&&captureDisplay!=0)return
   if(!CompositionDiagnostic.active(token)||!snapshots||animationEnabled||!probeAllowed||!validCommit(SystemClock.elapsedRealtime())||frame.physicalId!=committedPhysical)return
   val physical=committedPhysical;val epoch=probeEpoch;val rev=revision;val rotation=committedRotation
   CompositionDiagnostic.sample(ctx,token,task,physical,panelId,frame,view,bufferWidth,bufferHeight,if(panelId==1)"DESTINATION_BEFORE_SHIELD" else "PRIMARY_NO_SHIELD"){
    session==token&&probeAllowed&&probeEpoch==epoch&&revision==rev&&attached&&committedFrame===frame&&heldPanels[physical]===frame&&committedRotation==rotation&&validCommit(SystemClock.elapsedRealtime())&&allowed(ctx)
   }
  }
  override fun diagnostic():String{val d=dm.getDisplay(panelId);val size=d?.let{B8PanelGeometry.size(it)}?:Point();return "D$panelId raster=${bufferWidth}x$bufferHeight bufferChanges=$bufferChanges state=${d?.state} physical=${PanelIdentity.identity(d)} view=${view.width}x${view.height} display=${size.x}x${size.y} attached=$attached revealed=$revealed animation=${animationStart(PanelIdentity.identity(d)).status()} excluded=${excluded&&childExcluded} draws=$draws commits=$commits maxDrawMs=$maxDrawMs maxCommitMs=$maxCommitMs maxMovingGapMs=$maxMovingGapMs coalescedDraws=$coalescedDraws commitTimeouts=$commitTimeouts rawAngle=${LiveAngles.currentAngle()} visualAngle=$visual committedAngle=$committedAngle image=${held.physicalId}:${held.width}x${held.height} imageRole=${if(held.physicalId==PanelIdentity.identity(d)) "native-panel" else "source-placeholder"} snapshotRevision=$capturedRevision pending=${submissions.pending()} pendingAgeMs=${submissions.age(SystemClock.elapsedRealtime())} commitAge=${if(committedAt==0L)-1 else SystemClock.elapsedRealtime()-committedAt} reason=$rejection"}
  override fun committedAnimation()=committedAnimated
  override fun nativeImageCommitted():Boolean{val frame=committedFrame?:return false;return B8SnapshotPolicy.matches(committedPhysical,committedWidth,committedHeight,committedRotation,frame.physicalId,frame.width,frame.height,frame.rotation)}
  override fun validCommit(now:Long):Boolean{val d=dm.getDisplay(panelId)?:return false;val size=B8PanelGeometry.size(d);return d.state==Display.STATE_ON&&attached&&excluded&&childExcluded&&view.holder.surface.isValid&&B8RenderPolicy.committedPoseCurrent(committedAnimated,committedAngle,LiveAngles.currentAngle(),committedAt,now)&&committedFrame===(heldPanels[committedPhysical]?:outgoing)&&shapeMatches(committedPhysical,size.x,size.y)&&PanelIdentity.identity(d)==committedPhysical&&committedWidth==size.x&&committedHeight==size.y&&view.width==size.x&&view.height==size.y&&committedRotation==d.rotation}
  private fun displayRefreshRate()=(dm.getDisplay(panelId)?.supportedModes?.maxOfOrNull{it.refreshRate}?:60f).coerceAtMost(60f)
  override fun show(){dm.getDisplay(panelId)?.let{val size=B8PanelGeometry.size(it);resize(size.x,size.y)};val lp=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.OPAQUE)
   lp.alpha=0f;lp.dimAmount=0f;lp.windowAnimations=0;lp.gravity=Gravity.TOP or Gravity.LEFT;lp.setFitInsetsTypes(0);lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;lp.title="Duo B8 coordinated app scene";lp.preferredRefreshRate=displayRefreshRate()
   wm.addView(view,lp);attached=true;MotionGlass.add(motionListener);motionListener();view.post{if(session!=token||!attached)return@post;try{val root=View::class.java.getDeclaredMethod("getViewRootImpl").invoke(view);val sc=root.javaClass.getMethod("getSurfaceControl").invoke(root) as SurfaceControl;check(sc.isValid);GlassFrames.surface(view,sc);excluded=true;schedule()}catch(e:Exception){blocked=token;clear("capture exclusion unavailable")}}
  }
  override fun close(){attached=false;MotionGlass.remove(motionListener);view.removeCallbacks(redraw);queued=false;invalidateCommit();GlassFrames.surface(view,null);GlassFrames.surface(this,null);runCatching{wm.removeViewImmediate(view)};painter?.releaseEmbedded();painter=null}
 }
 private class DirectPanel(private val host:StandaloneService,private val token:Long,row:Bundle):Panel {
  private val physical=row.getString("physical")!!
  private val width=row.getInt("width");private val height=row.getInt("height");private val rotation=row.getInt("rotation")
  private val root=row.getParcelable("root",SurfaceControl::class.java)!!
  private val dm=host.getSystemService(DisplayManager::class.java)
  private val prefs=host.getSharedPreferences("standalone",0)
  private val producer=B8BufferLayer(host,root,GlassEffectSettings.bufferDimension(prefs,width),GlassEffectSettings.bufferDimension(prefs,height),width,height)
  private var painter:FrostSurface?=null
  private fun effects():FrostSurface=painter?:FrostSurface(host,standardAnimation=true,homeScene=homeScene,workspaceScene=workspaceScene).also{it.layout(0,0,width,height);painter=it}
  private var closed=false;private var queued=false;private var pending=false
  private var sequence=0L;private var drawnAt=0L;private var submittedAt=0L
  private var visual=Float.NaN;private var angle=Float.NaN;private var animated=false
  private var committedAngle=Float.NaN
  private var frames=0;private var gap=0L;private var delay=0L
  private var error="awaiting first presented frame"
  override val panelId:Int get()=(0..1).firstOrNull{PanelIdentity.identity(dm.getDisplay(it))==physical}?:-1
  override var committedAt=0L;private set
  override var committedPhysical="";private set
  override var committedFrame:GlassFrame?=null;private set
  private val draw=Runnable{queued=false;render()}
  private val motion=ScreenshotMotionPolicy()
  private val tilt=FloatArray(2)
  private val motionListener:()->Unit={
   if(!closed&&animationEnabled){
    MotionGlass.offset(rotation,tilt,minOf(width,height).toFloat()/maxOf(width,height)>.7f)
    if(motion.update(tilt[0],tilt[1]))schedule()
   }
  }
  override fun show(){GlassFrames.surface(this,root);if(animationEnabled)effects();MotionGlass.add(motionListener);motionListener();schedule()}
  private fun schedule(){if(closed||queued||pending)return;queued=true;main.postDelayed(draw,B8RenderPolicy.frameDelay(SystemClock.elapsedRealtime(),drawnAt))}
  override fun invalidate(){schedule()}
  override fun updateState(){
   if(closed)return
   if(pending&&dm.getDisplay(panelId)?.state==Display.STATE_ON&&SystemClock.elapsedRealtime()-submittedAt>750){blocked=token;clear("independent scene presentation timeout");return}
   val raw=LiveAngles.currentAngle();val now=SystemClock.elapsedRealtime()
   if(raw!=angle||!validCommit(now)||primaryCheck>0&&now-committedAt>=150||homePreviewAnimating(physical)||pauseAnimating())schedule()
  }
  private fun render(){
   if(closed||session!=token||pending)return
   val display=dm.getDisplay(panelId);if(display!=null&&display.state!=Display.STATE_ON)return
   val now=SystemClock.elapsedRealtime();val raw=LiveAngles.currentAngle()
   if(!raw.isFinite())return
   val frame=heldPanels[physical]?:original?:return
   val animate=animationStart(physical).forFrame(raw)
   if(!visual.isFinite()||!animate)visual=raw
   val inner=minOf(width,height).toFloat()/maxOf(width,height)>.7f
   visual=FrameSmoothing.start(visual,raw,inner,prefs.getBoolean("window_reveal_v2",true),prefs.getBoolean("startup_easing",true))
   visual=FrameSmoothing.step(visual,raw,if(drawnAt==0L)8.33f else (now-drawnAt).toFloat().coerceIn(1f,50f),prefs.getFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS))
   gap=maxOf(gap,B8RenderPolicy.movingGap(now,drawnAt,animated&&B8RenderPolicy.needsAnimation(visual,raw)))
   val pose=pausePose(visual,inner);val serial=++sequence;val motionRevision=motion.revision();pending=true;submittedAt=now;drawnAt=now;angle=raw
   val accepted=producer.draw({canvas->
    canvas.scale(producer.width.toFloat()/width,producer.height.toFloat()/height)
    drawScene(canvas,frame,physical,width,height,rotation,raw){image,fullWidthHome->
     if(!animate)drawScreenshotBackdrop(canvas,image,width,height,image.physicalId!=physical)
     else{
      val effect=effects()
      effect.configureStandardSnapshot(image,if(pauseReturnAt>0)pose else raw,pose,inner,rotation)
      check(effect.drawComposite(canvas,image,fullWidthHome)){"Embedded glass draw failed"}
     }
    }
   },{
    if(!closed&&session==token&&serial==sequence){
     pending=false;val at=SystemClock.elapsedRealtime();delay=maxOf(delay,at-now)
     // The first submitted image has already been presented. Preserve the startup
     // decision from submission; never replace an already-visible animation mid-leg.
     animationStart(physical).committed(raw,animate)
     committedAt=at;committedPhysical=physical;committedFrame=frame;committedAngle=visual;animated=animate;frames++;error=""
     if(frames==1)RecoveryLog.add("B8 DIRECT_FIRST_PRESENT session=$token physical=$physical elapsedMs=${at-started}; buffer + root visibility in one transaction; no optical proof")
     publish()
     if(frame!==(heldPanels[physical]?:original)||animate&&(B8RenderPolicy.needsAnimation(pose,LiveAngles.currentAngle())||motion.changedSince(motionRevision))||homePreviewAnimating(physical)||pauseAnimating())schedule()
    }
   },{why->if(!closed&&session==token){error=why;blocked=token;clear(why)}})
   if(accepted)animationStart(physical).committed(raw,animate)
   else if(!closed){pending=false;error="waiting for released GPU buffer";schedule()}
  }
  override fun validCommit(now:Long):Boolean {
   if(closed||!directMapped||committedFrame!==(heldPanels[physical]?:original)||committedAt<=0)return false
   val d=dm.getDisplay(panelId)?:return false;val size=B8PanelGeometry.size(d)
   return d.state==Display.STATE_ON&&d.rotation==rotation&&size.x==width&&size.y==height&&
    B8RenderPolicy.committedPoseCurrent(animated,committedAngle,LiveAngles.currentAngle(),committedAt,now)
  }
  override fun committedAnimation()=animated
  override fun nativeImageCommitted():Boolean {val f=committedFrame?:return false;return B8SnapshotPolicy.matches(physical,width,height,rotation,f.physicalId,f.width,f.height,f.rotation)}
  override fun probe(){
   if(homeScene){
    HomeInnerDiagnostic.observe(token,physical,width,height,directStage.ifEmpty{if(primaryCheck>0)"NATIVE_READINESS" else "MOVING_OR_ENDPOINT"},committedFrame?:heldPanels[physical]?:original,
     "revision=$revision check=$primaryCheck sourcePanel=$source committedAt=$committedAt committedAngle=$committedAngle nativeImage=${nativeImageCommitted()} validCommit=${validCommit(SystemClock.elapsedRealtime())} pending=$pending; ${homePreviews[physical]?.report()?:"Home frosted preview inactive"}")
    return
   }
   val f=committedFrame?:return
   if(directStage.isNotEmpty()&&validCommit(SystemClock.elapsedRealtime()))CompositionDiagnostic.movingSample(host,token,task,physical,panelId,f,directStage,revision,if(workspaceScene)workspaceTasks.clone() else null){session==token&&!closed&&validCommit(SystemClock.elapsedRealtime())}
  }
  override fun diagnostic()="physical=$physical logical=D$panelId backend=independent-moving-buffer framesPresented=$frames raster=${producer.width}x${producer.height} animation=${animationStart(physical).status()} maxPresentMs=$delay maxMovingGapMs=$gap pending=$pending rawAngle=${LiveAngles.currentAngle()} committedAngle=$committedAngle nativeImage=${nativeImageCommitted()} error=$error; software presentation evidence"+(homePreviews[physical]?.let{"; "+it.report()}?:"")
  override fun close(){if(closed)return;closed=true;MotionGlass.remove(motionListener);main.removeCallbacks(draw);sequence++;GlassFrames.surface(this,null);producer.close();painter?.releaseEmbedded();painter=null}
 }

}
