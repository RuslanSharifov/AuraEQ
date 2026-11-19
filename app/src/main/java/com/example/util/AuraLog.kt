package com.example.util

import android.util.Log
import com.example.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Central logger for AuraEQ.
 *
 * - Every line goes to Logcat under the tag "AuraEQ/<area>" (debug builds show
 *   debug lines; release builds keep info and above).
 * - The last [MAX_ENTRIES] lines are also kept in memory so a bug report can
 *   include them via [dump] without needing adb.
 *
 * Never call this from the audio thread per buffer. Log state changes only.
 *
 * Android's android.util.Log methods are not implemented in plain local JVM
 * unit tests. Keep logging best-effort so exercising audio processors in those
 * tests does not fail before the actual PCM assertions run.
 */
object AuraLog {
    private const val ROOT = "AuraEQ"
    private const val MAX_ENTRIES = 400

    private val entries = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(area: String, msg: String) {
        if (BuildConfig.DEBUG) logSafely { Log.d("$ROOT/$area", msg) }
        record('D', area, msg, null)
    }

    fun i(area: String, msg: String) {
        logSafely { Log.i("$ROOT/$area", msg) }
        record('I', area, msg, null)
    }

    fun w(area: String, msg: String, t: Throwable? = null) {
        logSafely { Log.w("$ROOT/$area", msg, t) }
        record('W', area, msg, t)
    }

    fun e(area: String, msg: String, t: Throwable? = null) {
        logSafely { Log.e("$ROOT/$area", msg, t) }
        record('E', area, msg, t)
    }

    /**
     * Android framework logging is unavailable in plain JVM unit tests.
     * Runtime Android logging remains unchanged; only framework stub failures
     * are ignored so diagnostics never break application/test execution.
     */
    private inline fun logSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            // android.util.Log throws "not mocked" from local JVM tests.
        }
    }

    /** Most recent entries, oldest first. */
    @Synchronized
    fun recent(): List<String> = entries.toList()

    /** Entries joined for a bug report or clipboard. */
    fun dump(): String = recent().joinToString("\n")

    @Synchronized
    private fun record(level: Char, area: String, msg: String, t: Throwable?) {
        val suffix = t?.let { " | ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        val line = "${timeFormat.format(Date())} $level/$area: $msg$suffix"
        if (entries.size >= MAX_ENTRIES) entries.removeFirst()
        entries.addLast(line)
    }
}
