package com.andresterrazas.simple.home

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.andresterrazas.simple.MainActivity
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.InterceptMode

object DefaultHome {
    const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

    @Volatile var running = false
    @Volatile var pausedUntil = 0L
    @Volatile var foreground = false
    private val events = ArrayDeque<String>()

    fun component(ctx: Context) = ComponentName(ctx, HomeService::class.java)

    val isFireTv: Boolean get() = Build.MANUFACTURER.equals("Amazon", ignoreCase = true)

    @Volatile private var stockCache: Set<String>? = null

    /** Launchers del sistema instalados (Amazon, Google TV, Android TV, PatchWall de Xiaomi…), excepto simple. */
    fun stockHomes(ctx: Context): Set<String> {
        stockCache?.let { return it }
        val pm = ctx.packageManager
        val homes = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        val set = (homes + AMAZON_LAUNCHER).filter { it != ctx.packageName && it != "com.android.settings" && it != "com.android.tv.settings" }.toSet()
        stockCache = set
        return set
    }

    /** ¿simple es el inicio predeterminado del sistema (Android TV / Google TV)? */
    fun isDefaultHome(ctx: Context): Boolean = runCatching {
        ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == ctx.packageName
    }.getOrDefault(false)

    fun setHomeAdbCommand(ctx: Context) =
        "adb shell cmd package set-home-activity ${ctx.packageName}/${MainActivity::class.java.name}"

    fun log(line: String) {
        synchronized(events) {
            events.addFirst(line)
            while (events.size > 16) events.removeLast()
        }
    }

    fun recentEvents(): List<String> = synchronized(events) { events.toList() }

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

    fun pause(minutes: Int) {
        pausedUntil = SystemClock.uptimeMillis() + minutes * 60_000L
    }

    // ── Sesión de sistema ────────────────────────────────────────────────────────────────
    // En Fire OS reciente, los Ajustes, la red y la Tienda viven dentro del mismo paquete que el inicio
    // de Amazon. Al abrir una de esas pantallas desde simple se recuerda su "firma" (clase + título de
    // ventana). Mientras esa pantalla esté al frente se respeta; cuando aparece la pantalla de inicio de
    // Amazon (al salir con Atrás), simple vuelve a tomar el control.
    @Volatile private var sessionAt = 0L
    private val sessionScreens = HashSet<String>()
    private val learnedHome = HashSet<String>()
    private const val SETTLE_MS = 2_500L

    fun beginSystemSession() {
        sessionAt = SystemClock.uptimeMillis()
        synchronized(sessionScreens) { sessionScreens.clear() }
    }

    fun endSystemSession() {
        sessionAt = 0L
    }

    private fun inSession(): Boolean =
        sessionAt != 0L && SystemClock.uptimeMillis() - sessionAt < 30 * 60_000L

    fun sessionActive() = inSession()
    fun sessionSettled() = inSession() && SystemClock.uptimeMillis() - sessionAt > 1_200L

    /** Una app normal o simple pasó al frente: la sesión terminó. */
    fun onForeground(pkg: String, ownPkg: String) {
        if (!inSession()) return
        val amazonOrSystem = pkg.startsWith("com.amazon.") || pkg == "android" || pkg.startsWith("com.android.")
        if (pkg == ownPkg || (!amazonOrSystem && SystemClock.uptimeMillis() - sessionAt > SETTLE_MS)) endSystemSession()
    }

    fun shouldIntercept(cls: String, title: String, mode: InterceptMode): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now < pausedUntil) return false
        val lower = cls.lowercase()
        if (lower.startsWith("android.widget") || lower.startsWith("android.view") || lower.startsWith("android.app.dialog")) return false
        if ("dialog" in lower || "overlay" in lower || "toast" in lower) return false
        val signature = "$cls|$title"
        if (inSession()) {
            // Las pantallas que aparecen justo después de abrir algo de Amazon son "suyas": se respetan.
            if (now - sessionAt < SETTLE_MS) {
                synchronized(sessionScreens) { sessionScreens.add(signature) }
                return false
            }
            if (synchronized(sessionScreens) { signature in sessionScreens }) return false
            val back = signature in learnedHome || "home" in lower
            if (back) endSystemSession()
            return back
        }
        val ok = when (mode) {
            InterceptMode.STRICT -> "home" in lower || signature in learnedHome
            InterceptMode.BROAD -> true
        }
        if (ok) learnedHome.add(signature)
        return ok
    }
}

class HomeService : AccessibilityService() {
    private var lastLaunch = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    override fun onServiceConnected() {
        DefaultHome.running = true
    }

    private fun mode() = (application as SimpleApp).store.state.value.intercept

    private fun launchSimple(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < 700) return
        lastLaunch = now
        DefaultHome.log("→ simple ($reason)")
        com.andresterrazas.simple.diag.Perf.markHomeRequested()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(MainActivity.EXTRA_FROM_HOME, true)
        runCatching { startActivity(intent) }
    }

    /**
     * Revisa el contenido de la pantalla de Amazon al frente: si muestra su inicio (filas como
     * "Mis apps y canales" o "Patrocinado"), es el inicio y no los Ajustes ni la red.
     */
    private fun amazonHomeVisible(): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        if (root.packageName?.toString() !in DefaultHome.stockHomes(this)) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 700) {
            val n = queue.removeFirst()
            visited++
            val text = (n.text?.toString() ?: n.contentDescription?.toString())?.lowercase()
            if (text != null && HOME_MARKERS.any { it in text }) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    /** Mientras una pantalla de Amazon abierta desde simple está al frente, se comprueba cada segundo si ya es su inicio. */
    private val poll = object : Runnable {
        override fun run() {
            if (!DefaultHome.sessionActive() || SystemClock.uptimeMillis() < DefaultHome.pausedUntil) { polling = false; return }
            if (DefaultHome.sessionSettled() && amazonHomeVisible()) {
                DefaultHome.endSystemSession()
                polling = false
                launchSimple("inicio de Amazon detectado")
                return
            }
            handler.postDelayed(this, 900)
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(poll, 600)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        val title = event.text?.firstOrNull()?.toString()?.take(40).orEmpty()
        DefaultHome.log("$pkg · ${cls.substringAfterLast('.')}${if (title.isNotBlank()) " · \"$title\"" else ""}")
        DefaultHome.onForeground(pkg, packageName)
        if (DefaultHome.isDefaultHome(this) || pkg !in DefaultHome.stockHomes(this)) return
        if (DefaultHome.sessionActive()) startPolling()
        if (DefaultHome.shouldIntercept(cls, title, mode())) launchSimple("ventana de inicio")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        DefaultHome.running = false
        super.onDestroy()
    }

    companion object {
        private val HOME_MARKERS = listOf(
            "mis apps y canales", "mis aplicaciones", "my apps", "your apps", "tus apps",
            "patrocinado", "sponsored", "continuar viendo", "continue watching",
        )
    }
}
