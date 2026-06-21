package org.nekosukuriputo.nekuva.core.diagnostics

import org.nekosukuriputo.nekuva.core.AppInfo
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Directory where crash logs are written (Android: app filesDir/crash; Desktop: ~/.nekuva/crash). */
expect fun crashLogDir(): File

/**
 * Lightweight cross-platform crash reporter (KMP replacement for Doki's ACRA). Installs a default
 * uncaught-exception handler that appends the stack trace + app/platform info to a timestamped file under
 * [crashLogDir], then delegates to the previous handler so the OS still shows its dialog / terminates.
 *
 * No network/backend — reports stay on-device so the user can find and share them. Keeps the newest
 * [MAX_LOGS] files. Safe to call once at process start (Android Application / Desktop main).
 */
object CrashReporter {

    private const val MAX_LOGS = 10
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val dir = crashLogDir().apply { mkdirs() }
        val now = System.currentTimeMillis()
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("Nekuva crash report")
            appendLine("timestamp: $now")
            appendLine("app: ${AppInfo.VERSION_NAME}")
            appendLine("os: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
            appendLine("java: ${System.getProperty("java.version")}")
            appendLine("thread: ${thread.name}")
            appendLine("---")
            append(stack)
        }
        File(dir, "crash_$now.txt").writeText(report)
        prune(dir)
    }

    /** Keep only the newest [MAX_LOGS] crash files. */
    private fun prune(dir: File) {
        val logs = dir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".txt") } ?: return
        if (logs.size <= MAX_LOGS) return
        logs.sortedByDescending { it.lastModified() }.drop(MAX_LOGS).forEach { runCatching { it.delete() } }
    }
}
