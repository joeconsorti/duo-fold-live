package com.mccal.folio

import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.pm.PackageManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Debug-only passive probe for measuring the public hinge sensor and display handoff.
 *
 * This deliberately uses only public, read-only APIs. It does not request a presentation,
 * concurrent display state, or any change to the device's display configuration.
 */
class FoldProbeActivity : ComponentActivity(), SensorEventListener, DisplayManager.DisplayListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var displayManager: DisplayManager
    private var hingeSensor: Sensor? = null
    private var vendorFoldingSensor: Sensor? = null
    private var sensorRegistered = false
    private var vendorSensorRegistered = false
    private var displayListenerRegistered = false

    private lateinit var angleView: TextView
    private lateinit var clockView: TextView
    private lateinit var detailView: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionId = ""
    private var instanceId = ""
    private var instanceNumber = 0
    private var eventCount = 0L
    private var lastAngle = Float.NaN
    private var lastAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var lastSensorTimestampNs = 0L
    private var lastReceivedElapsedNs = 0L
    private var lastReceivedWallMs = 0L
    private var vendorEventCount = 0L
    private var lastVendorAngle = Float.NaN
    private var lifecycleState = "created"

    private val vendorSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != SAMSUNG_FOLDING_ANGLE_TYPE || event.values.isEmpty()) return
            vendorEventCount++
            lastVendorAngle = event.values[0]
            Log.i(
                TAG,
                "event=vendorHinge session=$sessionId instance=$instanceId count=$vendorEventCount " +
                    "timestampNs=${event.timestamp} receivedElapsedNs=${SystemClock.elapsedRealtimeNanos()} " +
                    "wallMs=${System.currentTimeMillis()} degrees=${formatAngle(lastVendorAngle)} " +
                    "accuracy=${event.accuracy} source=SAMSUNG_FOLDING_ANGLE_65686 sensorName=${event.sensor.name}",
            )
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.i(
                TAG,
                "event=vendorAccuracy session=$sessionId instance=$instanceId accuracy=$accuracy " +
                    "source=SAMSUNG_FOLDING_ANGLE_65686 sensorName=${sensor.name}",
            )
        }
    }

    private val uiTicker = object : Runnable {
        override fun run() {
            render()
            mainHandler.postDelayed(this, UI_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = savedInstanceState?.getString(STATE_SESSION_ID)
            ?: UUID.randomUUID().toString().take(8)
        instanceNumber = (savedInstanceState?.getInt(STATE_INSTANCE_NUMBER) ?: 0) + 1
        instanceId = "$sessionId.$instanceNumber"
        eventCount = savedInstanceState?.getLong(STATE_EVENT_COUNT) ?: 0L
        lastAngle = savedInstanceState?.getFloat(STATE_LAST_ANGLE) ?: Float.NaN
        lastAccuracy = savedInstanceState?.getInt(STATE_LAST_ACCURACY)
            ?: SensorManager.SENSOR_STATUS_UNRELIABLE
        lastSensorTimestampNs = savedInstanceState?.getLong(STATE_SENSOR_TIMESTAMP) ?: 0L
        lastReceivedElapsedNs = savedInstanceState?.getLong(STATE_RECEIVED_ELAPSED) ?: 0L
        lastReceivedWallMs = savedInstanceState?.getLong(STATE_RECEIVED_WALL) ?: 0L

        sensorManager = getSystemService(SensorManager::class.java)
        displayManager = getSystemService(DisplayManager::class.java)
        hingeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        vendorFoldingSensor = sensorManager.getDefaultSensor(SAMSUNG_FOLDING_ANGLE_TYPE, false)
            ?: sensorManager.getSensorList(Sensor.TYPE_ALL).firstOrNull {
                it.type == SAMSUNG_FOLDING_ANGLE_TYPE || it.name.equals("Folding Angle", ignoreCase = true)
            }
        buildContent()
        lifecycleState = "created"
        logLifecycle("onCreate", "restored=${savedInstanceState != null}")
        logSensorCapability()
        logDisplaySnapshot("onCreate")
        render()
    }

    override fun onStart() {
        super.onStart()
        lifecycleState = "started"
        if (!displayListenerRegistered) {
            displayManager.registerDisplayListener(this, mainHandler)
            displayListenerRegistered = true
        }
        val sensor = hingeSensor
        sensorRegistered = sensor != null && sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST,
            0,
            mainHandler,
        )
        vendorSensorRegistered = registerVendorSensor()
        logLifecycle(
            "onStart",
            "hingeRegistered=$sensorRegistered vendorHingeRegistered=$vendorSensorRegistered",
        )
        logDisplaySnapshot("onStart")
    }

    override fun onResume() {
        super.onResume()
        lifecycleState = "resumed"
        mainHandler.removeCallbacks(uiTicker)
        mainHandler.post(uiTicker)
        logLifecycle("onResume")
    }

    override fun onPause() {
        lifecycleState = "paused"
        mainHandler.removeCallbacks(uiTicker)
        logLifecycle("onPause")
        super.onPause()
    }

    override fun onStop() {
        if (sensorRegistered) sensorManager.unregisterListener(this)
        sensorRegistered = false
        if (vendorSensorRegistered) sensorManager.unregisterListener(vendorSensorListener)
        vendorSensorRegistered = false
        if (displayListenerRegistered) displayManager.unregisterDisplayListener(this)
        displayListenerRegistered = false
        lifecycleState = "stopped"
        logLifecycle("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(uiTicker)
        logLifecycle("onDestroy", "changingConfigurations=$isChangingConfigurations")
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SESSION_ID, sessionId)
        outState.putInt(STATE_INSTANCE_NUMBER, instanceNumber)
        outState.putLong(STATE_EVENT_COUNT, eventCount)
        outState.putFloat(STATE_LAST_ANGLE, lastAngle)
        outState.putInt(STATE_LAST_ACCURACY, lastAccuracy)
        outState.putLong(STATE_SENSOR_TIMESTAMP, lastSensorTimestampNs)
        outState.putLong(STATE_RECEIVED_ELAPSED, lastReceivedElapsedNs)
        outState.putLong(STATE_RECEIVED_WALL, lastReceivedWallMs)
        logLifecycle("onSaveInstanceState")
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        logLifecycle("onWindowFocusChanged", "hasFocus=$hasFocus")
        render()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
        lastAngle = event.values[0]
        lastAccuracy = event.accuracy
        lastSensorTimestampNs = event.timestamp
        lastReceivedElapsedNs = SystemClock.elapsedRealtimeNanos()
        lastReceivedWallMs = System.currentTimeMillis()
        eventCount++
        Log.i(
            TAG,
            "event=hinge session=$sessionId instance=$instanceId count=$eventCount " +
                "timestampNs=$lastSensorTimestampNs receivedElapsedNs=$lastReceivedElapsedNs " +
                "wallMs=$lastReceivedWallMs degrees=${formatAngle(lastAngle)} accuracy=$lastAccuracy " +
                "source=TYPE_HINGE_ANGLE sensorName=${event.sensor.name}",
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type != Sensor.TYPE_HINGE_ANGLE) return
        lastAccuracy = accuracy
        Log.i(
            TAG,
            "event=accuracy session=$sessionId instance=$instanceId accuracy=$accuracy " +
                "source=TYPE_HINGE_ANGLE sensorName=${sensor.name}",
        )
        render()
    }

    override fun onDisplayAdded(displayId: Int) {
        Log.i(TAG, "event=displayAdded session=$sessionId instance=$instanceId displayId=$displayId")
        logDisplaySnapshot("displayAdded:$displayId")
        render()
    }

    override fun onDisplayRemoved(displayId: Int) {
        Log.i(TAG, "event=displayRemoved session=$sessionId instance=$instanceId displayId=$displayId")
        logDisplaySnapshot("displayRemoved:$displayId")
        render()
    }

    override fun onDisplayChanged(displayId: Int) {
        Log.i(TAG, "event=displayChanged session=$sessionId instance=$instanceId displayId=$displayId")
        logDisplaySnapshot("displayChanged:$displayId")
        render()
    }

    private fun buildContent() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        angleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 52f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, dp(4))
        }
        clockView = TextView(this).apply {
            setTextColor(0xffd6ecf5.toInt())
            textSize = 20f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(16))
        }
        detailView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff102833.toInt())
            setPadding(dp(18), dp(18), dp(18), dp(32))
            addView(angleView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(clockView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(detailView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun render() {
        if (!::detailView.isInitialized) return
        clockView.text = wallClock(System.currentTimeMillis())
        angleView.text = if (lastAngle.isFinite()) "${formatAngle(lastAngle)}°" else "—°"

        val sensor = hingeSensor
        val receivedAgeMs = if (lastReceivedElapsedNs == 0L) null else
            (SystemClock.elapsedRealtimeNanos() - lastReceivedElapsedNs) / 1_000_000L
        detailView.text = buildString {
            appendLine("DUO FOLD PROBE  •  debug only")
            appendLine("session: $sessionId  instance: $instanceId")
            appendLine("lifecycle: $lifecycleState  focus: ${hasWindowFocus()}")
            appendLine("events: $eventCount  registered: $sensorRegistered")
            appendLine("last sample age: ${receivedAgeMs?.let { "${it}ms" } ?: "none"}")
            appendLine("sensor timestamp: ${lastSensorTimestampNs.takeIf { it != 0L } ?: "none"}")
            appendLine("accuracy: $lastAccuracy")
            appendLine()
            appendLine("PUBLIC HINGE SENSOR")
            if (sensor == null) appendLine("TYPE_HINGE_ANGLE unavailable") else {
                appendLine("name: ${sensor.name}")
                appendLine("vendor: ${sensor.vendor}")
                appendLine("type: ${sensor.type}  reportingMode: ${sensor.reportingMode}")
                appendLine("minDelay: ${sensor.minDelay}µs  maxDelay: ${sensor.maxDelay}µs")
                appendLine("resolution: ${sensor.resolution}  range: ${sensor.maximumRange}")
            }
            appendLine()
            appendLine("SAMSUNG VENDOR REGISTRATION TEST")
            appendLine("type: $SAMSUNG_FOLDING_ANGLE_TYPE  permission: ${permissionStatus(SAMSUNG_SENSOR_PERMISSION)}")
            appendLine("visible: ${vendorFoldingSensor != null}  registered: $vendorSensorRegistered")
            appendLine("events: $vendorEventCount  last: ${if (lastVendorAngle.isFinite()) "${formatAngle(lastVendorAngle)}°" else "none"}")
            vendorFoldingSensor?.let { appendLine("name: ${it.name}  vendor: ${it.vendor}  resolution: ${it.resolution}") }
            appendLine()
            append(metricsText())
            appendLine()
            append(displayText())
        }
    }

    private fun metricsText(): String {
        val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val metrics = resources.displayMetrics
        val config = resources.configuration
        val currentDisplay = window.decorView.display
        return buildString {
            appendLine("CURRENT WINDOW")
            appendLine("bounds: ${bounds.width()}×${bounds.height()}  $bounds")
            appendLine("density: ${metrics.density} (${metrics.densityDpi}dpi)  scaled: ${metrics.scaledDensity}")
            appendLine("config dp: ${config.screenWidthDp}×${config.screenHeightDp}  smallest: ${config.smallestScreenWidthDp}")
            appendLine("orientation: ${config.orientation}  rotation: ${currentDisplay?.rotation ?: "none"}")
            appendLine("activity display: ${currentDisplay?.displayId ?: "none"}")
        }
    }

    private fun displayText(): String {
        val presentationIds = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .map { it.displayId }
            .toSet()
        val displays = displayManager.displays.sortedBy { it.displayId }
        return buildString {
            appendLine("PUBLIC DISPLAYS (${displays.size})")
            appendLine("presentation category: ${presentationIds.sorted()}")
            for (display in displays) {
                val mode = display.mode
                appendLine("id ${display.displayId}: ${display.name}")
                appendLine("  state=${displayState(display.state)} valid=${display.isValid}")
                appendLine("  flags=0x${display.flags.toString(16)} [${displayFlags(display.flags)}]")
                appendLine("  presentationCategory=${display.displayId in presentationIds}")
                appendLine("  mode=${mode.modeId} ${mode.physicalWidth}×${mode.physicalHeight} @ ${"%.2f".format(Locale.US, mode.refreshRate)}Hz rotation=${display.rotation}")
                appendLine("  supportedModes=${display.supportedModes.joinToString { it.modeId.toString() }}")
            }
        }
    }

    private fun logSensorCapability() {
        val sensor = hingeSensor
        if (sensor == null) {
            Log.w(TAG, "event=sensorCapability session=$sessionId instance=$instanceId source=TYPE_HINGE_ANGLE available=false")
            return
        }
        Log.i(
            TAG,
            "event=sensorCapability session=$sessionId instance=$instanceId source=TYPE_HINGE_ANGLE " +
                "available=true name=${sensor.name} vendor=${sensor.vendor} type=${sensor.type} " +
                "reportingMode=${sensor.reportingMode} minDelayUs=${sensor.minDelay} " +
                "maxDelayUs=${sensor.maxDelay} resolution=${sensor.resolution} range=${sensor.maximumRange}",
        )
        val vendor = vendorFoldingSensor
        Log.i(
            TAG,
            "event=vendorSensorCapability session=$sessionId instance=$instanceId " +
                "source=SAMSUNG_FOLDING_ANGLE_65686 visible=${vendor != null} " +
                "permission=${permissionStatus(SAMSUNG_SENSOR_PERMISSION)} " +
                if (vendor == null) "sensorListMatch=false" else
                    "sensorListMatch=true name=${vendor.name} vendor=${vendor.vendor} type=${vendor.type} " +
                        "reportingMode=${vendor.reportingMode} minDelayUs=${vendor.minDelay} " +
                        "maxDelayUs=${vendor.maxDelay} resolution=${vendor.resolution} range=${vendor.maximumRange}",
        )
    }

    private fun registerVendorSensor(): Boolean {
        val sensor = vendorFoldingSensor
        if (sensor == null) {
            Log.i(
                TAG,
                "event=vendorRegistration session=$sessionId instance=$instanceId " +
                    "source=SAMSUNG_FOLDING_ANGLE_65686 result=unavailable " +
                    "permission=${permissionStatus(SAMSUNG_SENSOR_PERMISSION)}",
            )
            return false
        }
        return try {
            val registered = sensorManager.registerListener(
                vendorSensorListener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                0,
                mainHandler,
            )
            Log.i(
                TAG,
                "event=vendorRegistration session=$sessionId instance=$instanceId " +
                    "source=SAMSUNG_FOLDING_ANGLE_65686 result=$registered " +
                    "permission=${permissionStatus(SAMSUNG_SENSOR_PERMISSION)} sensorName=${sensor.name}",
            )
            registered
        } catch (error: Exception) {
            Log.w(
                TAG,
                "event=vendorRegistration session=$sessionId instance=$instanceId " +
                    "source=SAMSUNG_FOLDING_ANGLE_65686 result=exception " +
                    "permission=${permissionStatus(SAMSUNG_SENSOR_PERMISSION)} " +
                    "exception=${error.javaClass.name} message=${error.message}",
            )
            false
        }
    }

    private fun permissionStatus(permission: String): String =
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"

    private fun logLifecycle(event: String, extra: String = "") {
        val suffix = if (extra.isEmpty()) "" else " $extra"
        Log.i(
            TAG,
            "event=lifecycle callback=$event session=$sessionId instance=$instanceId " +
                "elapsedNs=${SystemClock.elapsedRealtimeNanos()} wallMs=${System.currentTimeMillis()}$suffix",
        )
    }

    private fun logDisplaySnapshot(reason: String) {
        val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val metrics = resources.displayMetrics
        val config = resources.configuration
        val activityDisplayId = window.decorView.display?.displayId
        val presentationIds = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .map { it.displayId }
            .toSet()
        Log.i(
            TAG,
            "event=displaySnapshot reason=$reason session=$sessionId instance=$instanceId " +
                "wallMs=${System.currentTimeMillis()} window=${bounds.width()}x${bounds.height()} " +
                "configDp=${config.screenWidthDp}x${config.screenHeightDp} densityDpi=${metrics.densityDpi} " +
                "orientation=${config.orientation} activityDisplayId=$activityDisplayId " +
                "displayCount=${displayManager.displays.size} presentationIds=${presentationIds.sorted()}",
        )
        for (display in displayManager.displays.sortedBy { it.displayId }) {
            val mode = display.mode
            Log.i(
                TAG,
                "event=displayInfo reason=$reason session=$sessionId instance=$instanceId " +
                    "displayId=${display.displayId} name=${display.name} state=${displayState(display.state)} " +
                    "valid=${display.isValid} flags=0x${display.flags.toString(16)} " +
                    "flagNames=${displayFlags(display.flags)} presentationCategory=${display.displayId in presentationIds} " +
                    "modeId=${mode.modeId} size=${mode.physicalWidth}x${mode.physicalHeight} " +
                    "refreshHz=${"%.2f".format(Locale.US, mode.refreshRate)} rotation=${display.rotation} " +
                    "supportedModeIds=${display.supportedModes.joinToString(",") { it.modeId.toString() }}",
            )
        }
    }

    private fun displayFlags(flags: Int): String {
        val names = buildList {
            if (flags and Display.FLAG_PRESENTATION != 0) add("PRESENTATION")
            if (flags and Display.FLAG_PRIVATE != 0) add("PRIVATE")
            if (flags and Display.FLAG_SECURE != 0) add("SECURE")
            if (flags and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS != 0) add("PROTECTED_BUFFERS")
        }
        return names.ifEmpty { listOf("none") }.joinToString(",")
    }

    private fun displayState(state: Int) = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_VR -> "VR"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN($state)"
    }

    private fun wallClock(wallMs: Long): String = WALL_CLOCK.get().format(Date(wallMs))

    private fun formatAngle(angle: Float): String = String.format(Locale.US, "%.3f", angle)

    companion object {
        private const val TAG = "DuoFoldProbe"
        private const val UI_REFRESH_MS = 100L
        private const val SAMSUNG_FOLDING_ANGLE_TYPE = 65_686
        private const val SAMSUNG_SENSOR_PERMISSION = "com.samsung.permission.SSENSOR"
        private const val STATE_SESSION_ID = "foldProbe.sessionId"
        private const val STATE_INSTANCE_NUMBER = "foldProbe.instanceNumber"
        private const val STATE_EVENT_COUNT = "foldProbe.eventCount"
        private const val STATE_LAST_ANGLE = "foldProbe.lastAngle"
        private const val STATE_LAST_ACCURACY = "foldProbe.lastAccuracy"
        private const val STATE_SENSOR_TIMESTAMP = "foldProbe.sensorTimestamp"
        private const val STATE_RECEIVED_ELAPSED = "foldProbe.receivedElapsed"
        private const val STATE_RECEIVED_WALL = "foldProbe.receivedWall"
        private val WALL_CLOCK = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
        }
    }
}
