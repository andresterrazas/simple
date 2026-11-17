package com.andresterrazas.simple.home

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
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

    fun log(pkg: String, cls: String) {
        synchronized(events) {
            events.addFirst("$pkg  ·  $cls")
            while (events.size > 12) events.removeLast()
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

    fun shouldIntercept(cls: String, mode: InterceptMode): Boolean {
        if (SystemClock.uptimeMillis() < pausedUntil) return false
        val lower = cls.lowercase()
        if (lower.startsWith("android.widget") || lower.startsWith("android.view") || lower.startsWith("android.app.dialog")) return false
        if ("dialog" in lower || "overlay" in lower || "toast" in lower) return false
        return when (mode) {
            InterceptMode.STRICT -> "home" in lower
            InterceptMode.BROAD -> true
        }
    }

    fun pause(minutes: Int) {
        pausedUntil = SystemClock.uptimeMillis() + minutes * 60_000L
    }
}

class HomeService : AccessibilityService() {
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        DefaultHome.running = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        DefaultHome.log(pkg, cls)
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
        super.onDestroy()
    }
}
