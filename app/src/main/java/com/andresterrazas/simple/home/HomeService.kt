package com.andresterrazas.simple.home

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.andresterrazas.simple.MainActivity
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.HomeLongAction
import com.andresterrazas.simple.data.InterceptMode
import com.andresterrazas.simple.data.SystemActions

object DefaultHome {
    const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

    @Volatile var running = false
    @Volatile var pausedUntil = 0L
    @Volatile var foreground = false
    private val events = ArrayDeque<String>()
    private val keys = ArrayDeque<String>()
    @Volatile var homeKeySeen = false

    fun component(ctx: Context) = ComponentName(ctx, HomeService::class.java)

    fun log(pkg: String, cls: String) {
        synchronized(events) {
            events.addFirst("$pkg  ·  $cls")
            while (events.size > 12) events.removeLast()
        }
    }

    fun recentEvents(): List<String> = synchronized(events) { events.toList() }

    fun logKey(e: android.view.KeyEvent) {
        if (e.keyCode == android.view.KeyEvent.KEYCODE_HOME) homeKeySeen = true
        if (e.action != android.view.KeyEvent.ACTION_DOWN || e.repeatCount > 0) return
        synchronized(keys) {
            keys.addFirst(android.view.KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_"))
            while (keys.size > 10) keys.removeLast()
        }
    }

    fun recentKeys(): List<String> = synchronized(keys) { keys.toList() }

    fun isEnabled(ctx: Context): Boolean {
        val raw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = component(ctx)
        return raw.split(':').any { ComponentName.unflattenFromString(it.trim()) == me }
    }

    fun canWriteSecure(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Con WRITE_SECURE_SETTINGS (concedido una vez por ADB) simple se reactiva solo, incluso tras actualizaciones de Fire OS. */
    fun enableViaSecure(ctx: Context): Boolean {
        if (!canWriteSecure(ctx)) return false
        if (isEnabled(ctx)) return true
        return try {
            val cr = ctx.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val me = component(ctx).flattenToString()
            val next = if (current.isBlank()) me else "$current:$me"
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun adbCommands(ctx: Context): List<String> {
        val me = component(ctx).flattenToString()
        return listOf(
            "adb shell settings put secure enabled_accessibility_services $me",
            "adb shell settings put secure accessibility_enabled 1",
            "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS",
        )
    }

    // ── Sesión de sistema ────────────────────────────────────────────────────────────────
    // En Fire OS reciente, los Ajustes y la Tienda de Amazon viven dentro del mismo paquete que su
    // pantalla de inicio. Cuando simple abre una de esas pantallas, no debe "robarla" de regreso.
    @Volatile private var systemSessionAt = 0L
    private val sessionClasses = HashSet<String>()
    private val learnedHome = HashSet<String>()
    @Volatile var homePressedAt = 0L

    fun beginSystemSession() {
        systemSessionAt = SystemClock.uptimeMillis()
        synchronized(sessionClasses) { sessionClasses.clear() }
    }

    fun endSystemSession() {
        systemSessionAt = 0L
    }

    private fun inSystemSession(): Boolean =
        systemSessionAt != 0L && SystemClock.uptimeMillis() - systemSessionAt < 20 * 60_000L

    /** Una app normal o simple pasó al frente: la sesión de ajustes/tienda terminó. */
    fun onForeground(pkg: String, ownPkg: String) {
        if (!inSystemSession()) return
        val amazonOrSystem = pkg.startsWith("com.amazon.") || pkg == "android" || pkg.startsWith("com.android.")
        if (pkg == ownPkg || (!amazonOrSystem && SystemClock.uptimeMillis() - systemSessionAt > 2_000)) endSystemSession()
    }

    fun shouldIntercept(cls: String, mode: InterceptMode): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now < pausedUntil) return false
        val lower = cls.lowercase()
        if (lower.startsWith("android.widget") || lower.startsWith("android.view") || lower.startsWith("android.app.dialog")) return false
        if ("dialog" in lower || "overlay" in lower || "toast" in lower) return false
        // Si Fire OS le entrega el botón Inicio al servicio, la decisión es exacta: solo tras presionar Inicio.
        if (homeKeySeen) return now - homePressedAt < 3_000
        if (inSystemSession()) {
            if (now - systemSessionAt < 8_000) synchronized(sessionClasses) { sessionClasses.add(cls) }
            val isSessionScreen = synchronized(sessionClasses) { cls in sessionClasses }
            return cls in learnedHome && !isSessionScreen
        }
        val ok = when (mode) {
            InterceptMode.STRICT -> "home" in lower
            InterceptMode.BROAD -> true
        }
        if (ok) learnedHome.add(cls)
        return ok
    }

    fun pause(minutes: Int) {
        pausedUntil = SystemClock.uptimeMillis() + minutes * 60_000L
    }
}

class HomeService : AccessibilityService() {
    private var lastLaunch = 0L
    private var homeDownAt = 0L
    private var homeLongFired = false

    override fun onServiceConnected() {
        DefaultHome.running = true
        instance = this
    }

    /**
     * Experimental: si Fire OS entrega el botón Inicio al servicio, simple lo atiende directamente
     * (sin que aparezca el inicio de Amazon) y distingue la pulsación larga.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        DefaultHome.logKey(event)
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        DefaultHome.homePressedAt = SystemClock.uptimeMillis()
        DefaultHome.endSystemSession()
        val p = (application as SimpleApp).store.state.value
        if (!p.directHome) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    homeDownAt = event.eventTime
                    homeLongFired = false
                } else if (!homeLongFired && (event.isLongPress || event.eventTime - homeDownAt >= LONG_PRESS_MS)) {
                    homeLongFired = true
                    onHomeLong(p.homeLong)
                }
            }
            KeyEvent.ACTION_UP -> if (!homeLongFired) {
                if (event.eventTime - homeDownAt >= LONG_PRESS_MS) onHomeLong(p.homeLong) else openSimple(quick = false)
            }
        }
        return true
    }

    private fun onHomeLong(action: HomeLongAction) {
        when (action) {
            HomeLongAction.QUICK -> openSimple(quick = true)
            HomeLongAction.SETTINGS -> SystemActions.openSettings(this)
            HomeLongAction.NONE -> Unit
        }
    }

    private fun openSimple(quick: Boolean) {
        lastLaunch = SystemClock.uptimeMillis()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(MainActivity.EXTRA_FROM_HOME, true)
            .putExtra(MainActivity.EXTRA_QUICK, quick)
        runCatching { startActivity(intent) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        DefaultHome.log(pkg, cls)
        DefaultHome.onForeground(pkg, packageName)
        if (pkg != DefaultHome.AMAZON_LAUNCHER) return
        val mode = (application as SimpleApp).store.state.value.intercept
        if (!DefaultHome.shouldIntercept(cls, mode)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < 700) return
        lastLaunch = now
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(MainActivity.EXTRA_FROM_HOME, true)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        DefaultHome.running = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val LONG_PRESS_MS = 550L
        @Volatile var instance: HomeService? = null

        /** Acciones globales del sistema disponibles para el panel rápido. */
        fun global(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
