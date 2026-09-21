package com.mccal.folio

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference

/**
 * Explicit debug lab for copying and animating Duo Launcher's own window.
 *
 * Enable with `adb shell am start ... --ez duo_fold_render_probe true`. Frames never leave
 * memory. The effect is timed and the gyroscope supplies only a small visual trim; neither is
 * presented as a continuous measurement of the physical hinge.
 */
internal object FoldRenderExperiment {
    private const val EXTRA = "duo_fold_render_probe"
    private const val FRAME_TTL_MS = 2_000L
    private const val EXPANDED_WIDTH_DP = 650
    private const val TAG = "DuoFoldRender"
    private var current: WeakReference<Controller>? = null
    private var enabled = false
    private val frames = LinkedHashMap<String, Frame>(2, .75f, true)

    fun attach(activity: MainActivity) {
        if (activity.intent.getBooleanExtra(EXTRA, false)) {
            enabled = true
            Log.i(TAG, "enabled from create intent")
        }
        Controller(activity).also {
            current = WeakReference(it)
            activity.lifecycle.addObserver(it)
        }
    }

    fun onNewIntent(activity: MainActivity, intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA, false)) return
        enabled = true
        Log.i(TAG, "enabled from intent")
        current?.get()?.takeIf { it.activity === activity }?.enable()
    }

    private data class Frame(val bitmap: Bitmap, val capturedAt: Long, val expanded: Boolean) {
        val key get() = "${bitmap.width}x${bitmap.height}"
        fun fresh(now: Long = SystemClock.uptimeMillis()) = now - capturedAt <= FRAME_TTL_MS
    }

    private fun remember(frame: Frame) {
        frames.remove(frame.key)?.bitmap?.takeIf { it !== frame.bitmap }?.recycle()
        frames[frame.key] = frame
        while (frames.size > 2) frames.entries.iterator().run {
            next().value.bitmap.recycle(); remove()
        }
    }

    private fun sourceFor(width: Int, height: Int): Frame? = frames.values
        .filter { it.fresh() && (it.bitmap.width != width || it.bitmap.height != height) }
        .maxByOrNull { it.capturedAt }

    private fun clearFrames() {
        frames.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        frames.clear()
    }

    private fun disable(activity: Activity) {
        enabled = false
        activity.intent.removeExtra(EXTRA)
        clearFrames()
        Log.i(TAG, "stopped; memory cache and listeners cleared")
    }

    private class Controller(val activity: MainActivity) : DefaultLifecycleObserver,
        SensorEventListener, ViewTreeObserver.OnWindowFocusChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private val sensors = activity.getSystemService(SensorManager::class.java)
        private val gyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        private val wm = activity.getSystemService(WindowManager::class.java)
        private var foreground = false
        private var focused = false
        private var captureGeneration = 0
        private var sampling = false
        private var sampleRunnable: Runnable? = null
        private var animation: SnapshotView? = null
        private var controls: LinearLayout? = null
        private var status: TextView? = null
        private var animator: ValueAnimator? = null
        private var resumedAtNs = Long.MAX_VALUE
        private var gyroSeen = false
        private var sensorRegistered = false
        private var captureSuccesses = 0
        private var captureFailures = 0
        private var observedViewportKey: String? = null

        init {
            activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(this)
            focused = activity.hasWindowFocus()
        }

        override fun onStart(owner: LifecycleOwner) {
            foreground = true
            resumedAtNs = SystemClock.elapsedRealtimeNanos()
            if (enabled) activate()
        }

        override fun onResume(owner: LifecycleOwner) {
            resumedAtNs = SystemClock.elapsedRealtimeNanos()
            if (enabled) activate()
        }

        override fun onStop(owner: LifecycleOwner) {
            foreground = false
            suspendProbe(removeControls = true)
            if (!activity.isChangingConfigurations) clearFrames()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            captureGeneration++
            handler.removeCallbacksAndMessages(null)
            suspendProbe(removeControls = true)
            activity.window.decorView.viewTreeObserver.takeIf { it.isAlive }
                ?.removeOnWindowFocusChangeListener(this)
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            focused = hasFocus
            if (hasFocus && foreground && enabled) activate()
            else if (!hasFocus) suspendProbe(removeControls = true)
        }

        fun enable() {
            if (foreground && focused) activate()
        }

        private fun activate() {
            if (!foreground || !focused || !enabled || activity.isFinishing) return
            addControls()
            if (gyro != null && !sensorRegistered) sensorRegistered =
                sensors.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
            val decor = activity.window.decorView
            decor.postOnAnimation {
                if (!active()) return@postOnAnimation
                observedViewportKey = viewportKey(decor.width, decor.height)
                val source = sourceFor(decor.width, decor.height)
                if (source != null) beginTransition(source, "configuration handoff")
                else scheduleSample(80L)
            }
        }

        private fun active() = enabled && foreground && focused && !activity.isFinishing &&
            !activity.isDestroyed && current?.get() === this

        private fun scheduleSample(delay: Long = 400L) {
            if (!active() || sampling || sampleRunnable != null || animation != null) return
            val task = Runnable {
                sampleRunnable = null
                if (!active() || sampling || animation != null) return@Runnable
                val decor = activity.window.decorView
                val viewport = viewportKey(decor.width, decor.height)
                if (observedViewportKey != null && observedViewportKey != viewport) {
                    observedViewportKey = viewport
                    val source = sourceFor(decor.width, decor.height)
                    if (source != null) {
                        beginTransition(source, "live resize handoff")
                        return@Runnable
                    }
                } else observedViewportKey = viewport
                sampling = true
                copyWindow(300L) { bitmap ->
                    sampling = false
                    if (bitmap != null && active()) {
                        remember(Frame(bitmap, SystemClock.uptimeMillis(), expanded()))
                        captureSuccesses++
                        if (captureSuccesses == 1 || captureSuccesses % 10 == 0)
                            Log.i(TAG, "capture success #$captureSuccesses ${bitmap.width}x${bitmap.height}")
                    }
                    else bitmap?.recycle()
                    scheduleSample()
                }
            }
            sampleRunnable = task
            handler.postDelayed(task, delay)
        }

        private fun replay() {
            if (!active() || animation != null) return
            val decor = activity.window.decorView
            val frame = frames["${decor.width}x${decor.height}"]?.takeIf { it.fresh() }
            if (frame != null) beginTransition(frame, "replay")
            else copyWindow(500L) { bitmap ->
                if (bitmap != null && active()) {
                    val captured = Frame(bitmap, SystemClock.uptimeMillis(), expanded())
                    remember(captured)
                    beginTransition(captured, "replay")
                } else bitmap?.recycle()
            }
        }

        private fun beginTransition(source: Frame, reason: String) {
            if (!active() || animation != null) return
            cancelCapture()
            val view = SnapshotView(activity, source.bitmap, source.expanded, expanded())
            animation = view
            addWindow(view, animationParams())
            Log.i(TAG, "transition start reason=$reason source=${source.bitmap.width}x${source.bitmap.height} expanded=${source.expanded}")
            status?.text = "TIMED HOME SNAPSHOT LAB\n$reason • waiting for live Home"
            copyWindow(1_200L, retryNoData = true) { destination ->
                if (!active() || animation !== view || destination == null) {
                    destination?.recycle()
                    removeAnimation(view)
                    Log.i(TAG, "transition unavailable reason=$reason")
                    status?.text = "TIMED HOME SNAPSHOT LAB\nlimited proof: live frame unavailable"
                    scheduleSample()
                    return@copyWindow
                }
                status?.text = "TIMED HOME SNAPSHOT LAB\n$reason • timed plane"
                view.destination = destination
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 780L
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { view.progress = it.animatedValue as Float; view.invalidate() }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        private var cancelled = false
                        override fun onAnimationCancel(animation: android.animation.Animator) {
                            cancelled = true
                        }
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (cancelled || this@Controller.animation !== view) return
                            animator = null
                            view.progress = 1f
                            view.invalidate()
                            view.postOnAnimation {
                                removeAnimation(view)
                                Log.i(TAG, "transition end reason=$reason")
                                scheduleSample(250L)
                            }
                        }
                    })
                    start()
                }
            }
        }

        private fun copyWindow(timeoutMs: Long, retryNoData: Boolean = false,
            done: (Bitmap?) -> Unit) {
            val decor = activity.window.decorView
            if (!active() || decor.width <= 0 || decor.height <= 0) { done(null); return }
            val generation = ++captureGeneration
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            var completed = false
            fun complete(bitmap: Bitmap?) {
                if (completed) { bitmap?.recycle(); return }
                completed = true
                done(bitmap)
            }
            fun attempt() {
                if (!active() || generation != captureGeneration) { complete(null); return }
                val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(activity.window, bitmap, { result ->
                        if (generation != captureGeneration || !active()) {
                            bitmap.recycle(); return@request
                        }
                        if (result == PixelCopy.SUCCESS) complete(bitmap)
                        else {
                            bitmap.recycle()
                            if (retryNoData && SystemClock.uptimeMillis() + 80L < deadline)
                                handler.postDelayed({ attempt() }, 80L)
                            else {
                                logCaptureFailure("result=$result", decor.width, decor.height)
                                complete(null)
                            }
                        }
                    }, handler)
                } catch (_: IllegalArgumentException) {
                    bitmap.recycle(); logCaptureFailure("invalid source", decor.width, decor.height)
                    complete(null)
                }
            }
            attempt()
            handler.postDelayed({
                if (!completed && generation == captureGeneration) {
                    captureGeneration++
                    logCaptureFailure("timeout", decor.width, decor.height)
                    complete(null)
                }
            }, timeoutMs)
        }

        private fun logCaptureFailure(reason: String, width: Int, height: Int) {
            captureFailures++
            if (captureFailures == 1 || captureFailures % 10 == 0)
                Log.i(TAG, "capture failed #$captureFailures $reason ${width}x$height")
        }

        private fun expanded() = activity.resources.configuration.screenWidthDp >= EXPANDED_WIDTH_DP

        private fun viewportKey(width: Int, height: Int) = "${width}x$height"

        private fun addControls() {
            if (controls != null) return
            val pad = (10 * activity.resources.displayMetrics.density).toInt()
            val label = TextView(activity).apply {
                setTextColor(Color.WHITE); textSize = 11f
                text = "TIMED HOME SNAPSHOT LAB\ntimed only • gyro observer idle"
                setPadding(pad, pad / 2, pad, pad / 2)
            }
            status = label
            controls = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xdd20242b.toInt())
                addView(label)
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(Button(activity).apply { text = "Replay"; setOnClickListener { replay() } })
                    addView(Button(activity).apply { text = "Stop"; setOnClickListener {
                        suspendProbe(removeControls = true); disable(activity)
                    } })
                })
            }
            addWindow(controls!!, controlParams())
        }

        private fun addWindow(view: View, params: WindowManager.LayoutParams) {
            try { wm.addView(view, params) } catch (_: WindowManager.BadTokenException) { }
        }

        private fun removeAnimation(view: SnapshotView? = animation) {
            if (view == null) return
            if (animation === view) animation = null
            try { wm.removeViewImmediate(view) } catch (_: IllegalArgumentException) { }
            view.dispose()
        }

        private fun cancelCapture() {
            sampleRunnable?.let(handler::removeCallbacks)
            sampleRunnable = null
            captureGeneration++
            sampling = false
        }

        private fun suspendProbe(removeControls: Boolean) {
            cancelCapture()
            sensors.unregisterListener(this)
            sensorRegistered = false
            if (animator != null) Log.i(TAG, "transition cancelled lifecycle")
            animator?.removeAllListeners(); animator?.cancel(); animator = null
            removeAnimation()
            if (removeControls) controls?.let {
                try { wm.removeViewImmediate(it) } catch (_: IllegalArgumentException) { }
                controls = null; status = null
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_GYROSCOPE || event.timestamp < resumedAtNs ||
                event.values.size < 2) return
            val firstSample = !gyroSeen
            gyroSeen = true
            if (firstSample) status?.text = "TIMED HOME SNAPSHOT LAB\ntimed only • gyro observed, not render input"
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun animationParams() = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            token = activity.window.decorView.windowToken
            gravity = Gravity.FILL
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        private fun controlParams() = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { token = activity.window.decorView.windowToken; gravity = Gravity.TOP or Gravity.END }
    }

    private class SnapshotView(context: Context, private val source: Bitmap,
        private val sourceExpanded: Boolean, private val destinationExpanded: Boolean) : View(context) {
        var destination: Bitmap? = null
        var progress = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val matrix = Matrix()
        private val src = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        private val dst = FloatArray(8)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f || source.isRecycled) return
            destination?.takeUnless { it.isRecycled }?.let {
                paint.alpha = 255; canvas.drawBitmap(it, null, RectF(0f, 0f, w, h), paint)
            }
            val p = progress.coerceIn(0f, 1f)
            val destinationWide = destinationExpanded
            val sourceWide = sourceExpanded
            val destinationAspect = w / h
            val sourceLeft: Float
            val sourceRight: Float
            val baseLeft: Float
            val baseRight: Float
            if (!sourceWide && destinationWide) {
                // Cover -> inner: preserve the cover aspect and place it in the right pane.
                sourceLeft = 0f; sourceRight = source.width.toFloat()
                val shownWidth = h * source.width / source.height
                baseLeft = w - shownWidth; baseRight = w
            } else if (sourceWide && !destinationWide) {
                // Inner -> cover: use the right pane (Screen 1 + rail) at cover aspect.
                val cropWidth = source.height * destinationAspect
                sourceLeft = source.width - cropWidth; sourceRight = source.width.toFloat()
                baseLeft = 0f; baseRight = w
            } else {
                sourceLeft = 0f; sourceRight = source.width.toFloat()
                baseLeft = 0f; baseRight = w
            }
            val inset = (baseRight - baseLeft) * .14f * p
            val top = h * .045f * p
            if (destinationWide) {
                // Inner proof keeps its left/hinge edge anchored and blurs that edge.
                dst[0] = baseLeft; dst[1] = 0f; dst[2] = baseRight - inset; dst[3] = top
                dst[4] = baseRight - inset; dst[5] = h - top; dst[6] = baseLeft; dst[7] = h
            } else {
                // Cover proof keeps its right/hinge edge anchored and blurs that edge.
                dst[0] = baseLeft + inset; dst[1] = top; dst[2] = baseRight; dst[3] = 0f
                dst[4] = baseRight; dst[5] = h; dst[6] = baseLeft + inset; dst[7] = h - top
            }
            src[0] = sourceLeft; src[2] = sourceRight; src[4] = sourceRight; src[6] = sourceLeft
            src[5] = source.height.toFloat(); src[7] = source.height.toFloat()
            matrix.reset(); matrix.setPolyToPoly(src, 0, dst, 0, 4)
            paint.alpha = if (p < .78f) 255 else (255f * (1f - p) / .22f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(source, matrix, paint)
            if (p > 0f && paint.alpha > 0) {
                val edge = w * .075f
                val save = canvas.save()
                if (destinationWide) canvas.clipRect(baseLeft, 0f, baseLeft + edge, h)
                else canvas.clipRect(baseRight - edge, 0f, baseRight, h)
                // A clipped set of translucent offsets produces a bounded edge-local motion smear.
                for (step in 1..5) {
                    blurPaint.alpha = (paint.alpha * (6 - step) / 18f).toInt()
                    val smear = Matrix(matrix).apply {
                        postTranslate((if (destinationWide) -1 else 1) * step * 2.5f * p, 0f)
                    }
                    canvas.drawBitmap(source, smear, blurPaint)
                }
                canvas.restoreToCount(save)
            }
        }

        fun dispose() {
            destination?.takeUnless { it.isRecycled }?.recycle()
            destination = null
        }
    }
}
