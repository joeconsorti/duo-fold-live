package com.mccal.folio

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** Debug-only public-sensor motion recorder. It does not estimate physical hinge angle. */
class FoldMotionActivity : ComponentActivity(), SensorEventListener {
    private val model: FoldMotionViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false
    private var sensors = emptyList<FoldMotionSensor>()
    private var primaryGyroscope: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SensorManager::class.java)
        sensors = discoverPublicSensors(sensorManager)
        primaryGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        model.setSensorSummary(
            "Default public SDK sensor candidates (vendor/secondary channels skipped):\n" +
                sensors.joinToString("\n") { "candidate ${it.label}: ${it.sensor.name}" },
        )
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    FoldMotionLab(
                        state = model.uiState,
                        onTrial = { trial ->
                            model.startTrial(trial)
                            if (!registered) startSensors()
                        },
                        onReset = model::resetRotation,
                        onPause = model::togglePaused,
                        onMarker = { model.addMarker("manual") },
                        onFinish = {
                            stopSensors()
                            model.finishRecording()
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.foregroundStarted(SystemClock.elapsedRealtimeNanos())
        if (!model.uiState.finished) startSensors()
    }

    override fun onStop() {
        stopSensors()
        model.foregroundStopped()
        super.onStop()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val registration = sensors.firstOrNull { it.sensor === event.sensor } ?: return
        model.onSensorSample(
            sensorElapsedNs = event.timestamp,
            receivedElapsedNs = SystemClock.elapsedRealtimeNanos(),
            receivedWallMs = System.currentTimeMillis(),
            label = registration.label,
            sensorType = event.sensor.type,
            values = event.values,
            accuracy = event.accuracy,
            integrate = event.sensor === primaryGyroscope,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startSensors() {
        if (registered || model.uiState.finished) return
        val outcomes = sensors.map { registration ->
            val accepted = try {
                sensorManager.registerListener(
                    this,
                    registration.sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                    0,
                    mainHandler,
                )
            } catch (_: SecurityException) {
                false
            }
            registered = registered || accepted
            "${if (accepted) "registered" else "unavailable/denied"} ${registration.label}: ${registration.sensor.name}"
        }
        model.setSensorSummary(
            "Default public SDK sensors; vendor/secondary channels are not probed because their permission status cannot be verified through the public SDK:\n" +
                outcomes.joinToString("\n"),
        )
    }

    private fun stopSensors() {
        if (registered) sensorManager.unregisterListener(this)
        registered = false
    }
}

internal data class FoldMotionSensor(val sensor: Sensor, val label: String)

private fun discoverPublicSensors(manager: SensorManager): List<FoldMotionSensor> {
    val result = mutableListOf<FoldMotionSensor>()
    fun addType(type: Int, name: String) {
        manager.getDefaultSensor(type)?.let { result += FoldMotionSensor(it, "$name-primary") }
    }
    addType(Sensor.TYPE_GYROSCOPE, "gyroscope")
    addType(Sensor.TYPE_ACCELEROMETER, "accelerometer")
    addType(Sensor.TYPE_GRAVITY, "gravity")
    addType(Sensor.TYPE_HINGE_ANGLE, "hinge-type36-coarse-raw")
    return result.distinctBy { it.sensor }
}

internal data class FoldMotionTrial(val id: String, val title: String, val instruction: String)

private val FOLD_MOTION_TRIALS = listOf(
    FoldMotionTrial(
        "01_whole_device_control",
        "1. Whole-device control",
        "Keep the phone folded and rigid. Rotate the entire phone through the same comfortable motion you will use in the hinge trials.",
    ),
    FoldMotionTrial(
        "02_rear_half_still",
        "2. Rear/camera half still",
        "Hold the camera/rear half as still as possible and move only the other half. Mark immediately before the motion.",
    ),
    FoldMotionTrial(
        "03_other_half_still",
        "3. Other half still",
        "Reverse your grip: hold the other half as still as possible and move the camera/rear half. Mark immediately before the motion.",
    ),
    FoldMotionTrial(
        "04_normal_open_close",
        "4. Normal open/close",
        "Open and close the phone normally without trying to isolate either half. Mark before each direction change.",
    ),
)

internal data class FoldMotionUiState(
    val sessionId: String,
    val activeTrial: String? = null,
    val instruction: String = "Choose a trial. Use one session for all four trials.",
    val paused: Boolean = true,
    val finished: Boolean = false,
    val rotation: FoldMotionRotation = FoldMotionRotation(),
    val coarsePosture: String = "No fresh type-36 sample",
    val accelerometer: String = "—",
    val gravity: String = "—",
    val sensorSummary: String = "Discovering public sensors…",
    val rows: Long = 0L,
    val staleRejected: Long = 0L,
    val gapsSkipped: Long = 0L,
    val markerCount: Int = 0,
    val filePath: String = "",
    val status: String = "Ready",
)

internal class FoldMotionViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val sessionId = savedState.get<String>(KEY_SESSION)
        ?: UUID.randomUUID().toString().also { savedState[KEY_SESSION] = it }
    private val outputFile = File(File(application.filesDir, "fold-motion"), "fold-motion-$sessionId.csv")
    private val integrator = FoldMotionIntegrator()
    private var writer: FoldMotionCsvWriter? = null
    private var foregroundBoundaryNs = Long.MAX_VALUE
    private var foreground = false
    private var lastUiPublishNs = 0L
    private var activeTrial = savedState.get<String>(KEY_TRIAL)
    private var rows = savedState.get<Long>(KEY_ROWS) ?: 0L
    private var paused = savedState.get<Boolean>(KEY_PAUSED) ?: true
    private var finished = savedState.get<Boolean>(KEY_FINISHED) ?: false
    private var staleRejected = savedState.get<Long>(KEY_STALE) ?: 0L
    private var gapsSkipped = savedState.get<Long>(KEY_GAPS) ?: 0L
    private var markerCount = savedState.get<Int>(KEY_MARKERS) ?: 0

    var uiState by mutableStateOf(
        FoldMotionUiState(
            sessionId = sessionId,
            activeTrial = activeTrial,
            instruction = instructionFor(activeTrial),
            paused = paused,
            finished = finished,
            rotation = FoldMotionRotation(
                savedState.get<Double>(KEY_X) ?: 0.0,
                savedState.get<Double>(KEY_Y) ?: 0.0,
                savedState.get<Double>(KEY_Z) ?: 0.0,
            ),
            rows = rows,
            staleRejected = staleRejected,
            gapsSkipped = gapsSkipped,
            markerCount = markerCount,
            filePath = outputFile.absolutePath,
        ),
    )
        private set

    init {
        integrator.restore(uiState.rotation)
        integrator.setPaused(paused)
    }

    fun setSensorSummary(summary: String) {
        uiState = uiState.copy(sensorSummary = summary.ifBlank { "No permission-free requested sensors found" })
    }

    fun foregroundStarted(boundaryNs: Long) {
        foreground = true
        foregroundBoundaryNs = boundaryNs
        integrator.beginForeground(boundaryNs)
        integrator.setPaused(paused || activeTrial == null || finished)
        if (activeTrial != null && !finished) openWriter()
        if (activeTrial != null && !finished) addMarker("foreground-start")
    }

    fun foregroundStopped() {
        if (foreground && activeTrial != null && !finished) addMarker("foreground-stop")
        foreground = false
        integrator.setPaused(true)
        closeWriter()
        persist()
    }

    fun startTrial(trial: FoldMotionTrial) {
        if (finished) return
        activeTrial = trial.id
        paused = false
        integrator.reset()
        savedState[KEY_TRIAL] = trial.id
        savedState[KEY_PAUSED] = false
        integrator.setPaused(false)
        if (foreground) openWriter()
        persistRotation()
        uiState = uiState.copy(activeTrial = trial.id, instruction = trial.instruction, paused = false, rotation = integrator.rotation, status = "Recording ${trial.title}")
        addMarker("trial-selected:${trial.id}")
    }

    fun togglePaused() {
        if (finished || activeTrial == null) return
        paused = !paused
        savedState[KEY_PAUSED] = paused
        integrator.setPaused(paused)
        uiState = uiState.copy(paused = paused, status = if (paused) "Paused" else "Recording")
        addMarker(if (paused) "paused" else "resumed")
    }

    fun resetRotation() {
        integrator.reset()
        uiState = uiState.copy(rotation = integrator.rotation, status = "Integrated axes reset")
        persistRotation()
        addMarker("rotation-reset")
    }

    fun addMarker(label: String) {
        if (finished || activeTrial == null) return
        markerCount++
        val nowElapsed = SystemClock.elapsedRealtimeNanos()
        append(
            FoldMotionCsvRow(
                nowElapsed, nowElapsed, System.currentTimeMillis(), activeTrial!!,
                "marker:$markerCount:$label", -1, null, null, null, -1,
            ),
            flush = true,
        )
        savedState[KEY_MARKERS] = markerCount
        uiState = uiState.copy(markerCount = markerCount, status = "Marker $markerCount saved")
    }

    fun finishRecording() {
        if (finished) return
        addMarker("session-finished")
        finished = true
        paused = true
        integrator.setPaused(true)
        closeWriter()
        persist()
        uiState = uiState.copy(finished = true, paused = true, status = "Finished; CSV closed")
    }

    fun onSensorSample(
        sensorElapsedNs: Long,
        receivedElapsedNs: Long,
        receivedWallMs: Long,
        label: String,
        sensorType: Int,
        values: FloatArray,
        accuracy: Int,
        integrate: Boolean,
    ) {
        if (!foreground || finished || sensorElapsedNs < foregroundBoundaryNs) {
            if (foreground && sensorElapsedNs < foregroundBoundaryNs) {
                staleRejected++
                uiState = uiState.copy(staleRejected = staleRejected)
            }
            return
        }
        val x = values.getOrNull(0) ?: return
        val y = values.getOrNull(1)
        val z = values.getOrNull(2)
        if (integrate) {
            when (integrator.add(sensorElapsedNs, x, y ?: 0f, z ?: 0f)) {
                FoldMotionIntegrationResult.STALE -> staleRejected++
                FoldMotionIntegrationResult.GAP_SKIPPED -> gapsSkipped++
                else -> Unit
            }
        }
        if (activeTrial != null && !paused) {
            val eventWallMs = receivedWallMs - ((receivedElapsedNs - sensorElapsedNs).coerceAtLeast(0L) / 1_000_000L)
            append(
                FoldMotionCsvRow(sensorElapsedNs, receivedElapsedNs, eventWallMs, activeTrial!!, label, sensorType, x, y, z, accuracy),
            )
        }

        val publishNow = receivedElapsedNs - lastUiPublishNs >= UI_INTERVAL_NS || sensorType == Sensor.TYPE_HINGE_ANGLE
        if (publishNow) {
            lastUiPublishNs = receivedElapsedNs
            uiState = uiState.copy(
                rotation = integrator.rotation,
                coarsePosture = if (sensorType == Sensor.TYPE_HINGE_ANGLE) "raw type-36 = ${format(x)} (coarse posture only)" else uiState.coarsePosture,
                accelerometer = if (sensorType == Sensor.TYPE_ACCELEROMETER) vector(x, y, z) else uiState.accelerometer,
                gravity = if (sensorType == Sensor.TYPE_GRAVITY) vector(x, y, z) else uiState.gravity,
                rows = rows,
                staleRejected = staleRejected,
                gapsSkipped = gapsSkipped,
            )
            persistRotation()
        }
    }

    override fun onCleared() {
        closeWriter()
        super.onCleared()
    }

    private fun openWriter() {
        if (writer != null || rows >= FoldMotionCsvWriter.MAXIMUM_ROWS) return
        try {
            writer = FoldMotionCsvWriter(outputFile, rows)
        } catch (error: IOException) {
            uiState = uiState.copy(status = "CSV error: ${error.message}")
        }
    }

    private fun append(row: FoldMotionCsvRow, flush: Boolean = false) {
        val target = writer ?: return
        try {
            if (target.append(row)) {
                rows = target.rows
                savedState[KEY_ROWS] = rows
                if (flush) target.flush()
            } else {
                paused = true
                integrator.setPaused(true)
                uiState = uiState.copy(paused = true, status = "CSV row cap reached (${FoldMotionCsvWriter.MAXIMUM_ROWS})")
            }
        } catch (error: IOException) {
            closeWriter()
            paused = true
            integrator.setPaused(true)
            uiState = uiState.copy(paused = true, status = "CSV error: ${error.message}")
        }
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (_: IOException) {
            // The visible status from the first write failure is more useful than a close failure.
        }
        writer = null
    }

    private fun persist() {
        savedState[KEY_TRIAL] = activeTrial
        savedState[KEY_ROWS] = rows
        savedState[KEY_PAUSED] = paused
        savedState[KEY_FINISHED] = finished
        savedState[KEY_STALE] = staleRejected
        savedState[KEY_GAPS] = gapsSkipped
        savedState[KEY_MARKERS] = markerCount
        persistRotation()
    }

    private fun persistRotation() {
        savedState[KEY_X] = integrator.rotation.xDegrees
        savedState[KEY_Y] = integrator.rotation.yDegrees
        savedState[KEY_Z] = integrator.rotation.zDegrees
    }

    companion object {
        private const val UI_INTERVAL_NS = 50_000_000L
        private const val KEY_SESSION = "foldMotion.session"
        private const val KEY_TRIAL = "foldMotion.trial"
        private const val KEY_ROWS = "foldMotion.rows"
        private const val KEY_PAUSED = "foldMotion.paused"
        private const val KEY_FINISHED = "foldMotion.finished"
        private const val KEY_STALE = "foldMotion.stale"
        private const val KEY_GAPS = "foldMotion.gaps"
        private const val KEY_MARKERS = "foldMotion.markers"
        private const val KEY_X = "foldMotion.x"
        private const val KEY_Y = "foldMotion.y"
        private const val KEY_Z = "foldMotion.z"
    }
}

@androidx.compose.runtime.Composable
private fun FoldMotionLab(
    state: FoldMotionUiState,
    onTrial: (FoldMotionTrial) -> Unit,
    onReset: () -> Unit,
    onPause: () -> Unit,
    onMarker: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Fold motion calibration lab", style = MaterialTheme.typography.headlineSmall)
        Text("Debug measurement only. Accumulated gyro sensor-axis rotation is a drifting estimate, NOT hinge angle or full device orientation. A raw type-36 value of 90 does not prove a physical 90° hinge angle.")
        Text("Session ${state.sessionId.take(8)} • rows ${state.rows}/${FoldMotionCsvWriter.MAXIMUM_ROWS}")
        FOLD_MOTION_TRIALS.forEach { trial ->
            OutlinedButton(onClick = { onTrial(trial) }, enabled = !state.finished, modifier = Modifier.fillMaxWidth()) {
                Text(trial.title)
            }
        }
        Text(state.instruction, style = MaterialTheme.typography.bodyLarge)
        Text("Selected: ${state.activeTrial ?: "none"} • ${state.status}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onMarker, enabled = state.activeTrial != null && !state.finished) { Text("Add marker") }
            Button(onClick = onPause, enabled = state.activeTrial != null && !state.finished) { Text(if (state.paused) "Resume" else "Pause") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReset, enabled = !state.finished) { Text("Reset axes") }
            OutlinedButton(onClick = onFinish, enabled = !state.finished) { Text("Finish + close") }
        }
        Spacer(Modifier.height(4.dp))
        Text("Live public sensor values", style = MaterialTheme.typography.titleMedium)
        Text(state.coarsePosture, fontFamily = FontFamily.Monospace)
        Text("accelerometer xyz: ${state.accelerometer}", fontFamily = FontFamily.Monospace)
        Text("gravity xyz:       ${state.gravity}", fontFamily = FontFamily.Monospace)
        Text("Accumulated gyro sensor-axis rotation (NOT hinge angle)", style = MaterialTheme.typography.titleMedium)
        Text("x ${format(state.rotation.xDegrees)}°   y ${format(state.rotation.yDegrees)}°   z ${format(state.rotation.zDegrees)}°", fontFamily = FontFamily.Monospace)
        Text("Rejected cached events: ${state.staleRejected} • skipped gyro gaps: ${state.gapsSkipped} • markers: ${state.markerCount}")
        Text("Permission-free sensors", style = MaterialTheme.typography.titleMedium)
        Text(state.sensorSummary, fontFamily = FontFamily.Monospace)
        Text("CSV (app-private)\n${state.filePath}", fontFamily = FontFamily.Monospace)
    }
}

private fun instructionFor(id: String?): String = FOLD_MOTION_TRIALS.firstOrNull { it.id == id }?.instruction
    ?: "Choose a trial. Use one session for all four trials."

private fun vector(x: Float, y: Float?, z: Float?): String = "${format(x)}, ${format(y)}, ${format(z)}"
private fun format(value: Number?): String = value?.let { String.format(Locale.US, "%.3f", it.toDouble()) } ?: "—"
