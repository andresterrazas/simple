package com.andresterrazas.simple.diag

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window

/**
 * Mediciones de fluidez: tiempo de arranque, tiempo de regreso con el botón Inicio y cuadros lentos.
 * Sirven para saber exactamente dónde se traba simple en cada dispositivo.
 */
object Perf {
    @Volatile var coldStartMs = -1L
    @Volatile var lastHomeMs = -1L
    @Volatile var homeRequestedAt = 0L

    @Volatile var frames = 0L
    @Volatile var slowFrames = 0L
    @Volatile var frozenFrames = 0L
    @Volatile var worstMs = 0L
    @Volatile private var budgetNs = 16_666_667L

    private var thread: HandlerThread? = null
    private var listener: Any? = null

    /** Primer cuadro dibujado tras crear la actividad: el arranque en frío desde que nació el proceso. */
    fun onActivityCreated() {
        val processStart = if (Build.VERSION.SDK_INT >= 24) Process.getStartUptimeMillis() else 0L
        Choreographer.getInstance().postFrameCallback {
            Choreographer.getInstance().postFrameCallback {
                if (coldStartMs < 0 && processStart > 0) coldStartMs = SystemClock.uptimeMillis() - processStart
            }
        }
    }

    /** El servicio pidió abrir simple (botón Inicio): se mide hasta el siguiente cuadro dibujado. */
    fun markHomeRequested() { homeRequestedAt = SystemClock.uptimeMillis() }

    fun onShownFromHome() {
        val start = homeRequestedAt
        if (start == 0L) return
        Choreographer.getInstance().postFrameCallback {
            Choreographer.getInstance().postFrameCallback {
                lastHomeMs = SystemClock.uptimeMillis() - start
                homeRequestedAt = 0L
            }
        }
    }

    fun attach(activity: Activity) {
        if (Build.VERSION.SDK_INT < 24 || listener != null) return
        val rate = runCatching { activity.windowManager.defaultDisplay.refreshRate }.getOrDefault(60f).coerceAtLeast(24f)
        budgetNs = (1_000_000_000f / rate).toLong()
        val t = HandlerThread("simple-frames").apply { start() }
        thread = t
        val l = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            frames++
            if (total > budgetNs * 3 / 2) slowFrames++
            if (total > 700_000_000L) frozenFrames++
            val ms = total / 1_000_000
            if (ms > worstMs) worstMs = ms
        }
        listener = l
        runCatching { activity.window.addOnFrameMetricsAvailableListener(l, Handler(t.looper)) }
    }

    fun detach(activity: Activity) {
        if (Build.VERSION.SDK_INT < 24) return
        (listener as? Window.OnFrameMetricsAvailableListener)?.let { runCatching { activity.window.removeOnFrameMetricsAvailableListener(it) } }
        listener = null
        thread?.quitSafely()
        thread = null
    }

    fun reset() {
        frames = 0; slowFrames = 0; frozenFrames = 0; worstMs = 0
    }

    fun summary(): String {
        val pct = if (frames > 0) slowFrames * 100.0 / frames else 0.0
        return buildString {
            append("Arranque en frío: ").append(if (coldStartMs >= 0) "$coldStartMs ms" else "sin medir").append('\n')
            append("Último regreso con Inicio: ").append(if (lastHomeMs >= 0) "$lastHomeMs ms" else "sin medir").append('\n')
            append("Cuadros: $frames · lentos: $slowFrames (${"%.1f".format(pct)} %) · congelados: $frozenFrames\n")
            append("Cuadro más lento: $worstMs ms")
        }
    }
}
