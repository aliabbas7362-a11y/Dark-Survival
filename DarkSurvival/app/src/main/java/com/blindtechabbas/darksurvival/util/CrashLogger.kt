package com.blindtechabbas.darksurvival.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * CrashLogger — in-app crash checking (level 4 of the verification system).
 * Writes every uncaught exception to filesDir/logs/crash_<timestamp>.log so
 * crashes can be reported back without a computer.
 */
object CrashLogger {

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(dir, "crash_${System.currentTimeMillis()}.log").writeText(
                    "Thread: ${thread.name}\n" +
                        "Time: ${System.currentTimeMillis()}\n" +
                        "$sw"
                )
            } catch (_: Exception) {
            } finally {
                kotlin.system.exitProcess(1)
            }
        }
    }
}
