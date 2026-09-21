package com.mccal.folio

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Locale

internal data class FoldMotionCsvRow(
    val sensorElapsedNs: Long,
    val receivedElapsedNs: Long,
    val wallTimeMs: Long,
    val trial: String,
    val sensor: String,
    val sensorType: Int,
    val x: Float?,
    val y: Float?,
    val z: Float?,
    val accuracy: Int,
)

internal class FoldMotionCsvWriter(
    private val file: File,
    initialRows: Long,
    private val maximumRows: Long = MAXIMUM_ROWS,
) : Closeable {
    var rows: Long = initialRows
        private set
    val full: Boolean get() = rows >= maximumRows
    private val writer: BufferedWriter

    init {
        file.parentFile?.mkdirs()
        val needsHeader = !file.exists() || file.length() == 0L
        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8), 16 * 1024)
        if (needsHeader) {
            writer.appendLine("sensor_elapsed_ns,received_elapsed_ns,wall_time_ms,trial,sensor,sensor_type,x,y,z,accuracy")
            writer.flush()
        }
    }

    fun append(row: FoldMotionCsvRow): Boolean {
        if (full) return false
        writer.append(row.sensorElapsedNs.toString()).append(',')
            .append(row.receivedElapsedNs.toString()).append(',')
            .append(row.wallTimeMs.toString()).append(',')
            .append(csv(row.trial)).append(',')
            .append(csv(row.sensor)).append(',')
            .append(row.sensorType.toString()).append(',')
            .append(number(row.x)).append(',')
            .append(number(row.y)).append(',')
            .append(number(row.z)).append(',')
            .append(row.accuracy.toString()).append('\n')
        rows++
        return true
    }

    fun flush() = writer.flush()
    override fun close() = writer.close()

    private fun number(value: Float?): String = value?.let { String.format(Locale.US, "%.8f", it) } ?: ""
    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        const val MAXIMUM_ROWS = 90_000L
    }
}
