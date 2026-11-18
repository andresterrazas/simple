package com.andresterrazas.simple.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

data class SystemShortcut(
    val id: String,
    val title: String,
    val subtitle: String,
    val intents: List<Intent>,
)

object SystemActions {
    const val AMAZON_SETTINGS_PKG = "com.amazon.tv.settings.v2"
    const val AMAZON_STORE_PKG = "com.amazon.venezia"

    private fun action(a: String) = Intent(a)

    val shortcuts: List<SystemShortcut> = listOf(
        SystemShortcut("network", "Red", "Wi‑Fi y conexión", listOf(action(Settings.ACTION_WIFI_SETTINGS), action(Settings.ACTION_WIRELESS_SETTINGS))),
        SystemShortcut("display", "Pantalla y sonido", "Resolución, HDR y audio", listOf(action(Settings.ACTION_DISPLAY_SETTINGS), action(Settings.ACTION_SOUND_SETTINGS))),
        SystemShortcut("apps", "Aplicaciones", "Administrar apps instaladas", listOf(action(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), action(Settings.ACTION_APPLICATION_SETTINGS))),
        SystemShortcut("remotes", "Controles y Bluetooth", "Mandos, controles de juego y dispositivos", listOf(action(Settings.ACTION_BLUETOOTH_SETTINGS))),
        SystemShortcut("accessibility", "Accesibilidad", "Servicios y opciones de accesibilidad", listOf(action(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
        SystemShortcut("device", if (com.andresterrazas.simple.home.DefaultHome.isFireTv) "Mi Fire TV" else "Acerca de la TV", "Información, reinicio y opciones de desarrollador", listOf(action(Settings.ACTION_DEVICE_INFO_SETTINGS))),
        SystemShortcut("date", "Fecha y hora", "Zona horaria y formato", listOf(action(Settings.ACTION_DATE_SETTINGS))),
        SystemShortcut("all", "Todos los ajustes", "Menú completo de configuración", emptyList()),
    )

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun tryStart(ctx: Context, intent: Intent): Boolean = try {
        com.andresterrazas.simple.home.DefaultHome.beginSystemSession()
        ctx.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun launchApp(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val intent = runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) }.getOrNull()
            ?: runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
        val ok = intent != null && try {
            ctx.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            // Las apps de Amazon (Tienda, Silk, etc.) también viven fuera de simple: no interceptar sus pantallas.
            if (pkg.startsWith("com.amazon.")) com.andresterrazas.simple.home.DefaultHome.beginSystemSession()
            true
        } catch (e: Exception) { false }
        if (!ok) toast(ctx, "No se pudo abrir la aplicación")
        return ok
    }

    /** Abre la pantalla de ajustes; si la del sistema no existe, cae al menú general. Nunca se queda sin respuesta. */
    fun openSettings(ctx: Context, intents: List<Intent> = emptyList()): Boolean {
        for (i in intents) if (tryStart(ctx, i)) return true
        if (tryStart(ctx, Intent(Settings.ACTION_SETTINGS))) return true
        val launch = runCatching { ctx.packageManager.getLaunchIntentForPackage(AMAZON_SETTINGS_PKG) }.getOrNull()
        if (launch != null && tryStart(ctx, launch)) return true
        toast(ctx, "No se encontraron los ajustes del sistema")
        return false
    }

    fun openShortcut(ctx: Context, id: String) {
        val s = shortcuts.firstOrNull { it.id == id }
        openSettings(ctx, s?.intents ?: emptyList())
    }

    fun resolvable(ctx: Context, s: SystemShortcut): Boolean {
        val pm = ctx.packageManager
        val list = s.intents.ifEmpty { listOf(Intent(Settings.ACTION_SETTINGS)) }
        return list.any { runCatching { it.resolveActivity(pm) != null }.getOrDefault(false) }
    }

    fun openWifi(ctx: Context) = openSettings(ctx, listOf(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)))

    fun openAccessibility(ctx: Context) = openSettings(ctx, listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))

    fun openStoreHome(ctx: Context) {
        val launch = runCatching { ctx.packageManager.getLaunchIntentForPackage(AMAZON_STORE_PKG) }.getOrNull()
            ?: runCatching { ctx.packageManager.getLeanbackLaunchIntentForPackage(AMAZON_STORE_PKG) }.getOrNull()
        if (launch == null || !tryStart(ctx, launch)) toast(ctx, "No se encontró la tienda de aplicaciones")
    }

    fun openAppInfo(ctx: Context, pkg: String) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        if (!tryStart(ctx, i)) openSettings(ctx, listOf(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)))
    }

    fun openInStore(ctx: Context, pkg: String) {
        val amzn = Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=$pkg"))
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        if (!tryStart(ctx, amzn) && !tryStart(ctx, market)) toast(ctx, "Esta app no está en la tienda")
    }

    fun uninstall(ctx: Context, pkg: String) {
        @Suppress("DEPRECATION")
        val i = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg"))
        if (!tryStart(ctx, i) && !tryStart(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))) {
            openAppInfo(ctx, pkg)
        }
    }

    fun openAmazonHome(ctx: Context) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage("com.amazon.tv.launcher")
        if (tryStart(ctx, home)) return
        val launch = runCatching { ctx.packageManager.getLaunchIntentForPackage("com.amazon.tv.launcher") }.getOrNull()
        if (launch == null || !tryStart(ctx, launch)) toast(ctx, "No se encontró el inicio de Amazon")
    }
}
