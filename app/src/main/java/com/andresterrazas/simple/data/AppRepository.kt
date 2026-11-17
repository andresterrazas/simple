package com.andresterrazas.simple.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val pkg: String,
    val label: String,
    val installTime: Long,
)

data class AppVisual(
    val tile: ImageBitmap,
    val icon: ImageBitmap?,
    val color: Int,
    val hasBanner: Boolean,
)

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private val visuals = ConcurrentHashMap<String, AppVisual>()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, AppEntry>()
        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val infos = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            for (ri in infos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || result.containsKey(pkg)) continue
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg).trim()
                val time = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
                result[pkg] = AppEntry(pkg, label.ifBlank { pkg }, time)
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        collect(Intent.CATEGORY_LAUNCHER)
        _apps.value = result.values.toList()
    }

    fun invalidate(pkg: String) {
        visuals.keys.filter { it.startsWith("$pkg:") }.forEach { visuals.remove(it) }
    }

    fun cached(pkg: String, mode: IconMode): AppVisual? = visuals["$pkg:${mode.name}"]

    suspend fun loadVisual(pkg: String, mode: IconMode): AppVisual? = withContext(Dispatchers.IO) {
        val key = "$pkg:${mode.name}"
        visuals[key]?.let { return@withContext it }
        val v = runCatching { buildVisual(pkg, mode) }.getOrNull() ?: return@withContext null
        visuals[key] = v
        v
    }

    private fun loadBanner(pkg: String): Drawable? {
        val leanback = runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) }.getOrNull()
        val fromActivity = leanback?.let { intent ->
            runCatching { pm.resolveActivity(intent, 0)?.activityInfo?.loadBanner(pm) }.getOrNull()
        }
        return fromActivity ?: runCatching { pm.getApplicationInfo(pkg, 0).loadBanner(pm) }.getOrNull()
    }

    /** Escala el banner respetando su proporción (sin recortes ni deformaciones) para que quepa en la tarjeta. */
    private fun fitBanner(d: Drawable): Bitmap {
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        if (iw <= 0 || ih <= 0) return d.toBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888)
        val scale = minOf(TILE_W / iw.toFloat(), TILE_H / ih.toFloat())
        val w = (iw * scale).toInt().coerceIn(1, TILE_W)
        val h = (ih * scale).toInt().coerceIn(1, TILE_H)
        return d.toBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun buildVisual(pkg: String, mode: IconMode): AppVisual {
        val banner = if (mode != IconMode.ICON) loadBanner(pkg) else null
        val iconDrawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        val iconBmp = iconDrawable?.let { runCatching { it.toBitmap(ICON, ICON, Bitmap.Config.ARGB_8888) }.getOrNull() }
        val bannerBmp = banner?.let { runCatching { fitBanner(it) }.getOrNull() }
        val color = dominantColor(bannerBmp ?: iconBmp)

        val tile = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        if (bannerBmp != null) {
            canvas.drawColor(shade(color, 0.35f))
            canvas.drawBitmap(bannerBmp, ((TILE_W - bannerBmp.width) / 2).toFloat(), ((TILE_H - bannerBmp.height) / 2).toFloat(), null)
        } else {
            canvas.drawColor(shade(color, 0.55f))
            if (iconBmp != null) {
                val size = (TILE_H * 0.62f).toInt()
                val left = (TILE_W - size) / 2
                val top = (TILE_H - size) / 2
                val scaled = Bitmap.createScaledBitmap(iconBmp, size, size, true)
                canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
            }
        }
        return AppVisual(
            tile = tile.asImageBitmap(),
            icon = iconBmp?.asImageBitmap(),
            color = color,
            hasBanner = bannerBmp != null,
        )
    }

    companion object {
        const val TILE_W = 256
        const val TILE_H = 144
        const val ICON = 128

        /** Elige el color más vivo de la imagen (rojo para Netflix, azul para Prime, etc.). */
        fun dominantColor(b: Bitmap?): Int {
            if (b == null) return 0xFF3A3F4B.toInt()
            val s = Bitmap.createScaledBitmap(b, 24, 24, true)
            val hsv = FloatArray(3)
            var wr = 0.0; var wg = 0.0; var wb = 0.0; var wsum = 0.0
            var ar = 0.0; var ag = 0.0; var ab = 0.0; var count = 0
            for (y in 0 until s.height) for (x in 0 until s.width) {
                val p = s.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                val r = Color.red(p); val g = Color.green(p); val bl = Color.blue(p)
                ar += r; ag += g; ab += bl; count++
                Color.colorToHSV(p, hsv)
                val w = (hsv[1] * hsv[1] * hsv[2]).toDouble()
                wr += r * w; wg += g * w; wb += bl * w; wsum += w
            }
            if (count == 0) return 0xFF3A3F4B.toInt()
            return if (wsum > count * 0.03) {
                Color.rgb((wr / wsum).toInt(), (wg / wsum).toInt(), (wb / wsum).toInt())
            } else {
                Color.rgb((ar / count).toInt(), (ag / count).toInt(), (ab / count).toInt())
            }
        }

        fun shade(color: Int, factor: Float): Int = Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt(),
        )
    }
}
