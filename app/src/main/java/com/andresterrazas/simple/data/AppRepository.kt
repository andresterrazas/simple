package com.andresterrazas.simple.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.DisplayMetrics
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    val light: Boolean = false,
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

    private val iconDir: File get() = File(context.filesDir, "icons").apply { mkdirs() }

    fun customIconFile(pkg: String) = File(iconDir, "$pkg.png")

    /** Guarda una imagen propia para la tarjeta de una app (recortada a 16:9). */
    fun saveCustomIcon(pkg: String, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TILE_W && bounds.outHeight / (sample * 2) >= TILE_H) sample *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val out = cropTo(src, TILE_W, TILE_H)
        FileOutputStream(customIconFile(pkg)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        invalidate(pkg)
        return true
    }

    /** Carga un recurso de la app en la densidad más alta disponible (xxxhdpi) para que se vea nítido en TV. */
    private fun hiRes(pkg: String, resId: Int): Drawable? {
        if (resId == 0) return null
        val res = runCatching { pm.getResourcesForApplication(pkg) }.getOrNull() ?: return null
        return runCatching { res.getDrawableForDensity(resId, DisplayMetrics.DENSITY_XXXHIGH, null) }.getOrNull()
    }

    private fun loadBanner(pkg: String): Drawable? {
        val leanback = runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) }.getOrNull()
        val activity = leanback?.let { runCatching { pm.resolveActivity(it, 0)?.activityInfo }.getOrNull() }
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val resId = activity?.banner?.takeIf { it != 0 } ?: app?.banner ?: 0
        return hiRes(pkg, resId)
            ?: activity?.let { runCatching { it.loadBanner(pm) }.getOrNull() }
            ?: app?.let { runCatching { it.loadBanner(pm) }.getOrNull() }
    }

    private fun loadIcon(pkg: String): Drawable? {
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        return hiRes(pkg, app?.icon ?: 0) ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
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
        val iconBmp = loadIcon(pkg)?.let { runCatching { it.toBitmap(ICON, ICON, Bitmap.Config.ARGB_8888) }.getOrNull() }
        val custom = if (mode == IconMode.CUSTOM) customIconFile(pkg).takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }?.let { cropTo(it, TILE_W, TILE_H) } else null
        val preferIcon = mode == IconMode.ICON || (mode == IconMode.AUTO && prefersIcon(pkg))
        val bannerBmp = if (custom == null && !preferIcon) loadBanner(pkg)?.let { runCatching { fitBanner(it) }.getOrNull() } else null
        val color = dominantColor(custom ?: bannerBmp ?: iconBmp)

        val tile = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        when {
            custom != null -> canvas.drawBitmap(custom, 0f, 0f, paint)
            bannerBmp != null -> {
                canvas.drawColor(edgeColor(bannerBmp) ?: shade(color, 0.35f))
                canvas.drawBitmap(bannerBmp, ((TILE_W - bannerBmp.width) / 2).toFloat(), ((TILE_H - bannerBmp.height) / 2).toFloat(), paint)
            }
            iconBmp != null -> {
                // Ícono de fondo completo (cuadrado opaco): se extiende su propio color a todo el ancho
                // para que parezca un banner. Si el ícono es transparente, va sobre un degradado de su color.
                val bleed = edgeColor(iconBmp)
                if (bleed != null) {
                    canvas.drawColor(bleed)
                    val size = TILE_H
                    canvas.drawBitmap(iconBmp, null, Rect((TILE_W - size) / 2, 0, (TILE_W + size) / 2, size), paint)
                } else {
                    val top = shade(color, 0.75f)
                    val bottom = shade(color, 0.45f)
                    canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, TILE_H.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
                    val size = (TILE_H * 0.66f).toInt()
                    canvas.drawBitmap(iconBmp, null, Rect((TILE_W - size) / 2, (TILE_H - size) / 2, (TILE_W + size) / 2, (TILE_H + size) / 2), paint)
                }
            }
            else -> canvas.drawColor(shade(color, 0.55f))
        }
        tile.setHasMipMap(true)
        return AppVisual(
            tile = tile.asImageBitmap(),
            icon = iconBmp?.asImageBitmap(),
            color = color,
            hasBanner = bannerBmp != null,
            light = isLight(tile),
        )
    }

    /** Apps cuyo banner de TV viene recortado o se ve mal; en modo automático usan su ícono. */
    private fun prefersIcon(pkg: String): Boolean {
        val label = _apps.value.firstOrNull { it.pkg == pkg }?.label?.lowercase().orEmpty()
        return pkg in ICON_FIRST || "prime video" in label
    }

    companion object {
        const val TILE_W = 400
        const val TILE_H = 225
        const val ICON = 256
        private val ICON_FIRST = setOf("com.amazon.avod", "com.amazon.avod.thirdpartyclient", "com.amazon.firebat")

        fun cropTo(src: Bitmap, w: Int, h: Int): Bitmap {
            val target = w.toFloat() / h
            val ratio = src.width.toFloat() / src.height
            val crop = if (ratio > target) {
                val cw = (src.height * target).toInt(); Rect((src.width - cw) / 2, 0, (src.width + cw) / 2, src.height)
            } else {
                val ch = (src.width / target).toInt(); Rect(0, (src.height - ch) / 2, src.width, (src.height + ch) / 2)
            }
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(src, crop, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }

        /** Si los bordes de la imagen son opacos y del mismo color, devuelve ese color (para extenderlo). */
        fun edgeColor(b: Bitmap): Int? {
            val pts = listOf(2 to 2, b.width - 3 to 2, 2 to b.height - 3, b.width - 3 to b.height - 3, 2 to b.height / 2, b.width - 3 to b.height / 2)
            val cs = pts.map { (x, y) -> b.getPixel(x.coerceIn(0, b.width - 1), y.coerceIn(0, b.height - 1)) }
            if (cs.any { Color.alpha(it) < 250 }) return null
            val r = cs.map { Color.red(it) }; val g = cs.map { Color.green(it) }; val bl = cs.map { Color.blue(it) }
            val spread = (r.max() - r.min()) + (g.max() - g.min()) + (bl.max() - bl.min())
            return if (spread < 40) Color.rgb(r.average().toInt(), g.average().toInt(), bl.average().toInt()) else null
        }

        /** ¿La tarjeta es clara en sus bordes? (para cambiar el contorno de enfoque y que se distinga). */
        fun isLight(b: Bitmap): Boolean {
            var sum = 0.0; var n = 0
            val step = 8
            for (x in 0 until b.width step step) for (y in listOf(1, b.height - 2)) {
                val p = b.getPixel(x, y); sum += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0; n++
            }
            for (y in 0 until b.height step step) for (x in listOf(1, b.width - 2)) {
                val p = b.getPixel(x, y); sum += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0; n++
            }
            return n > 0 && sum / n > 0.72
        }

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
