package com.andresterrazas.simple.diag

import android.content.Context
import com.andresterrazas.simple.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro local de cierres inesperados. Si simple se cierra solo, guarda qué pasó para verlo en
 * Diagnóstico y corregirlo sin tener que reproducirlo. Nada sale del dispositivo.
 */
object CrashLog {
    private const val SEP = "\n----\n"
    private const val MAX = 5
    private lateinit var file: File

    fun install(ctx: Context) {
        file = File(ctx.filesDir, "crashes.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(threadName: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "$stamp · v${BuildConfig.VERSION_NAME} · hilo $threadName\n${sw.toString().take(3500)}"
        val all = (listOf(entry) + entries()).take(MAX)
        file.writeText(all.joinToString(SEP))
    }

    fun entries(): List<String> =
        if (!::file.isInitialized || !file.exists()) emptyList()
        else file.readText().split(SEP).map { it.trim() }.filter { it.isNotBlank() }

    fun clear() {
        if (::file.isInitialized) file.delete()
    }
}
