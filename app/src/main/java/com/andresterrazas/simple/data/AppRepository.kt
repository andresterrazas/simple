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
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
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
    /** Origen y resolución de la imagen, p. ej. "Banner 640×360". */
    val source: String = "",
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

    // Solo 2 tarjetas se generan a la vez: evita saturar el CPU del Fire TV al abrir (menos tirones).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val workers = Dispatchers.IO.limitedParallelism(2)
    private val tileDir: File get() = File(context.cacheDir, "tiles").apply { mkdirs() }

    private fun versionOf(pkg: String): Long = runCatching {
        val pi = pm.getPackageInfo(pkg, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L) xor runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)

    private fun cacheBase(pkg: String, mode: IconMode): String {
        val custom = if (mode == IconMode.CUSTOM) customIconFile(pkg).lastModified() else 0L
        return "${pkg}_${mode.name}_${versionOf(pkg)}_${custom}_v$CACHE_VERSION"
    }

    /** Tarjetas ya generadas se guardan en disco: al reabrir simple aparecen al instante. */
    private fun readDisk(base: String): AppVisual? {
        val png = File(tileDir, "$base.png"); val meta = File(tileDir, "$base.txt")
        if (!png.exists() || !meta.exists()) return null
        val parts = meta.readText().split(',')
        if (parts.size < 4) return null
        val bmp = BitmapFactory.decodeFile(png.absolutePath) ?: return null
        bmp.setHasMipMap(true)
        return AppVisual(bmp.asImageBitmap(), null, parts[0].toInt(), parts[2] == "1", parts[1] == "1", parts[3])
    }

    private fun writeDisk(base: String, tile: Bitmap, v: AppVisual) {
        runCatching {
            File(tileDir, "$base.png").outputStream().use { tile.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(tileDir, "$base.txt").writeText("${v.color},${if (v.light) 1 else 0},${if (v.hasBanner) 1 else 0},${v.source}")
        }
    }

    suspend fun loadVisual(pkg: String, mode: IconMode): AppVisual? = withContext(workers) {
        val key = "$pkg:${mode.name}"
        visuals[key]?.let { return@withContext it }
        val base = runCatching { cacheBase(pkg, mode) }.getOrNull()
        base?.let { b -> runCatching { readDisk(b) }.getOrNull() }?.let { visuals[key] = it; return@withContext it }
        val built = runCatching { buildVisual(pkg, mode) }.getOrNull() ?: return@withContext null
        visuals[key] = built.first
        if (base != null) writeDisk(base, built.second, built.first)
        built.first
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

    /**
     * Versión en alta resolución solo si es una imagen simple (BitmapDrawable) con la misma proporción.
     * Los banners compuestos (capas, márgenes) se cargan normal: en alta densidad se descuadran.
     */
    private fun preferHiRes(pkg: String, resId: Int, normal: Drawable?): Drawable? {
        val hi = hiRes(pkg, resId) ?: return normal
        if (normal == null) return if (hi is BitmapDrawable) hi else null
        if (hi !is BitmapDrawable || normal !is BitmapDrawable) return normal
        val nw = normal.intrinsicWidth; val nh = normal.intrinsicHeight
        val hw = hi.bitmap.width; val hh = hi.bitmap.height
        if (nw <= 0 || nh <= 0 || hw <= nw) return normal
        val ratioDiff = kotlin.math.abs(hw.toFloat() / hh - nw.toFloat() / nh)
        return if (ratioDiff < 0.03f) BitmapDrawable(context.resources, hi.bitmap) else normal
    }

    private fun loadBanner(pkg: String): Drawable? {
        val leanback = runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) }.getOrNull()
        val activity = leanback?.let { runCatching { pm.resolveActivity(it, 0)?.activityInfo }.getOrNull() }
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val normal = activity?.let { runCatching { it.loadBanner(pm) }.getOrNull() }
            ?: app?.let { runCatching { it.loadBanner(pm) }.getOrNull() }
        val resId = activity?.banner?.takeIf { it != 0 } ?: app?.banner ?: 0
        return preferHiRes(pkg, resId, normal)
    }

    private fun loadIcon(pkg: String): Drawable? {
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val normal = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        val hi = hiRes(pkg, app?.icon ?: 0)
        // Los íconos adaptativos escalan bien en cualquier densidad; los demás, solo si son imagen simple.
        return when {
            android.os.Build.VERSION.SDK_INT >= 26 && hi is AdaptiveIconDrawable -> hi
            hi is BitmapDrawable && normal is BitmapDrawable && hi.bitmap.width > normal.intrinsicWidth -> hi
            else -> normal ?: hi
        }
    }

    private fun srcSize(d: Drawable): String {
        val w = (d as? BitmapDrawable)?.bitmap?.width ?: d.intrinsicWidth
        val h = (d as? BitmapDrawable)?.bitmap?.height ?: d.intrinsicHeight
        return if (w > 0 && h > 0) "${w}×${h}" else "vectorial"
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

    private fun buildVisual(pkg: String, mode: IconMode): Pair<AppVisual, Bitmap> {
        val iconBmp = loadIcon(pkg)?.let { runCatching { it.toBitmap(ICON, ICON, Bitmap.Config.ARGB_8888) }.getOrNull() }
        val custom = if (mode == IconMode.CUSTOM) customIconFile(pkg).takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }?.let { cropTo(it, TILE_W, TILE_H) } else null
        val preferIcon = mode == IconMode.ICON || (mode == IconMode.AUTO && prefersIcon(pkg))
        val bannerDrawable = if (custom == null && !preferIcon) loadBanner(pkg) else null
        val bannerBmp = bannerDrawable?.let { runCatching { fitBanner(it) }.getOrNull() }
        val iconRaw = if (custom == null && bannerBmp == null) loadIcon(pkg) else null
        val source = when {
            custom != null -> "Imagen propia"
            bannerDrawable != null && bannerBmp != null -> "Banner ${srcSize(bannerDrawable)}"
            iconRaw != null -> "Ícono ${srcSize(iconRaw)}"
            else -> "Sin imagen"
        }
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
                    // Ícono de fondo sólido: se recorta al contenido (logo) y se agranda sobre ese mismo color,
                    // así un ícono con logo pequeño (Netflix, Prime Video) se ve como un banner limpio.
                    canvas.drawColor(bleed)
                    val box = contentBounds(iconBmp, bleed)
                    val maxW = TILE_W * 0.78f
                    val maxH = TILE_H * 0.62f
                    val sc = minOf(maxW / box.width(), maxH / box.height())
                    val w = box.width() * sc; val h = box.height() * sc
                    val l = (TILE_W - w) / 2f; val t = (TILE_H - h) / 2f
                    canvas.drawBitmap(iconBmp, box, android.graphics.RectF(l, t, l + w, t + h), paint)
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
            icon = null,
            color = color,
            hasBanner = bannerBmp != null,
            light = isLight(tile),
            source = source,
        ) to tile
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
        private const val CACHE_VERSION = 3
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

        /** Rectángulo que contiene todo lo que no es el color de fondo (con un pequeño margen). */
        fun contentBounds(b: Bitmap, bg: Int): Rect {
            var l = b.width; var t = b.height; var r = -1; var btm = -1
            val br = Color.red(bg); val bgG = Color.green(bg); val bb = Color.blue(bg)
            for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) < 40) continue
                val d = kotlin.math.abs(Color.red(p) - br) + kotlin.math.abs(Color.green(p) - bgG) + kotlin.math.abs(Color.blue(p) - bb)
                if (d > 60) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > btm) btm = y }
            }
            if (r < 0) return Rect(0, 0, b.width, b.height)
            val pad = (b.width * 0.02f).toInt()
            return Rect((l - pad).coerceAtLeast(0), (t - pad).coerceAtLeast(0), (r + pad + 2).coerceAtMost(b.width), (btm + pad + 2).coerceAtMost(b.height))
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
