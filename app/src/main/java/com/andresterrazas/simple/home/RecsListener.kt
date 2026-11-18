package com.andresterrazas.simple.home

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Una tarjeta de "Continuar viendo / Sugerencias" publicada por otra app. */
data class Rec(
    val key: String,
    val pkg: String,
    val title: String,
    val subtitle: String,
    val image: ImageBitmap?,
    val intent: PendingIntent?,
    val time: Long,
    val progress: Float?,
    /** Intent para abrir directo el contenido (Watch Next de Android TV). */
    val launch: android.content.Intent? = null,
    /** Imagen remota o local que se carga bajo demanda. */
    val imageUri: String? = null,
    val source: String = "notificación",
)

/**
 * Muchas apps de TV publican sus recomendaciones y "seguir viendo" como notificaciones especiales
 * (categoría "recommendation" o con extras de Amazon). El inicio de Amazon las lee así; simple también.
 */
object Recs {
    private val _items = MutableStateFlow<List<Rec>>(emptyList())
    val items: StateFlow<List<Rec>> = _items
    @Volatile private var fromNotifications: List<Rec> = emptyList()
    @Volatile private var fromWatchNext: List<Rec> = emptyList()

    private fun merge() {
        _items.value = (fromWatchNext + fromNotifications)
            .sortedByDescending { it.time }
            .distinctBy { it.pkg + "|" + it.title.lowercase() }
            .take(40)
    }

    internal fun setWatchNext(list: List<Rec>) { fromWatchNext = list; merge() }

    /**
     * Algunas apps publican "Continuar viendo" con un paquete distinto al del ícono que se lanza
     * (p. ej. Prime Video en Fire TV). Se agrupan para que la sección aparezca en la app correcta.
     */
    private val aliasGroups = listOf(
        setOf("com.amazon.avod", "com.amazon.avod.thirdpartyclient", "com.amazon.firebat", "com.amazon.cloud9.kids"),
        setOf("com.disney.disneyplus", "com.disney.disneyplus.prod"),
        setOf("com.wbd.stream", "com.hbo.hbonow", "com.hbo.max.android.tv"),
        setOf("com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv"),
    )

    fun samePackage(a: String, b: String): Boolean =
        a == b || aliasGroups.any { a in it && b in it }

    fun forApp(pkg: String): List<Rec> = _items.value.filter { samePackage(it.pkg, pkg) }
    private val seen = LinkedHashMap<String, String>()

    fun component(ctx: Context) = ComponentName(ctx, RecsListener::class.java)

    fun isEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Con el permiso de ADB (WRITE_SECURE_SETTINGS) simple activa solo el acceso a recomendaciones. */
    fun enableViaSecure(ctx: Context): Boolean {
        if (!DefaultHome.canWriteSecure(ctx)) return false
        if (isEnabled(ctx)) return true
        return runCatching {
            val key = "enabled_notification_listeners"
            val cr = ctx.contentResolver
            val current = Settings.Secure.getString(cr, key).orEmpty()
            val me = component(ctx).flattenToString()
            Settings.Secure.putString(cr, key, if (current.isBlank()) me else "$current:$me")
            true
        }.getOrDefault(false)
    }

    fun adbCommand(ctx: Context) =
        "adb shell cmd notification allow_listener ${component(ctx).flattenToString()}"

    /** Resumen por app de lo que se ha visto (para Diagnóstico). */
    fun seenSummary(): List<String> = synchronized(seen) { seen.entries.map { "${it.key} · ${it.value}" } }

    internal fun note(pkg: String, what: String) = synchronized(seen) {
        seen[pkg] = what
        while (seen.size > 14) seen.remove(seen.keys.first())
    }

    internal fun set(list: List<Rec>) { fromNotifications = list; merge() }

    /** Abre el contenido: primero el intent de Watch Next, luego el de la notificación y al final la app. */
    fun open(ctx: Context, r: Rec) {
        val ok = r.launch?.let { runCatching { ctx.startActivity(android.content.Intent(it).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) } == true ||
            r.intent?.let { runCatching { it.send(); true }.getOrDefault(false) } == true
        if (!ok) com.andresterrazas.simple.data.SystemActions.launchApp(ctx, r.pkg)
    }
}

class RecsListener : NotificationListenerService() {
    private val current = LinkedHashMap<String, Rec>()

    override fun onListenerConnected() {
        current.clear()
        runCatching { activeNotifications }.getOrNull()?.forEach { consider(it) }
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (consider(sbn)) publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (current.remove(sbn.key) != null) publish()
    }

    private fun publish() = Recs.set(current.values.toList())

    private fun isRecommendation(n: Notification): Boolean {
        if (n.category == Notification.CATEGORY_RECOMMENDATION) return true
        val extras = n.extras ?: return false
        return extras.keySet().any { it.startsWith("com.amazon.extra.") || it == "android.contentType" }
    }

    private fun consider(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        val n = sbn.notification ?: return false
        val rec = isRecommendation(n)
        Recs.note(sbn.packageName, if (rec) "recomendación" else "notificación (${n.category ?: "sin categoría"})")
        if (!rec) return false
        val e = n.extras
        val title = (e?.getCharSequence(Notification.EXTRA_TITLE) ?: e?.getCharSequence("com.amazon.extra.DISPLAY_NAME"))?.toString()?.trim().orEmpty()
        if (title.isBlank()) return false
        val subtitle = (e?.getCharSequence(Notification.EXTRA_TEXT) ?: e?.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim().orEmpty()
        val progressMax = e?.getInt(Notification.EXTRA_PROGRESS_MAX) ?: 0
        val progress = if (progressMax > 0) (e?.getInt(Notification.EXTRA_PROGRESS) ?: 0) / progressMax.toFloat() else null
        current[sbn.key] = Rec(
            key = sbn.key,
            pkg = sbn.packageName,
            title = title,
            subtitle = subtitle,
            image = image(n),
            intent = n.contentIntent,
            time = sbn.postTime,
            progress = progress?.coerceIn(0f, 1f),
        )
        return true
    }

    @Suppress("DEPRECATION")
    private fun image(n: Notification): ImageBitmap? = runCatching {
        val e = n.extras
        val picture = e?.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        val bmp: Bitmap? = picture
            ?: (if (Build.VERSION.SDK_INT >= 23) n.getLargeIcon()?.let { iconBitmap(it) } else null)
            ?: n.largeIcon
        bmp?.let { scaleTo(it) }?.asImageBitmap()
    }.getOrNull()

    private fun iconBitmap(icon: Icon): Bitmap? =
        if (Build.VERSION.SDK_INT >= 23) runCatching { icon.loadDrawable(this)?.toBitmap() }.getOrNull() else null

    /** Las imágenes se reducen a 480 px de ancho como máximo para no gastar memoria. */
    private fun scaleTo(b: Bitmap): Bitmap {
        val max = 480
        if (b.width <= max) return b
        val h = (b.height * (max / b.width.toFloat())).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(b, max, h, true)
    }
}
