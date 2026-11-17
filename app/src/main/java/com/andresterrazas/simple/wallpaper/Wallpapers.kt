package com.andresterrazas.simple.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.andresterrazas.simple.data.DEFAULT_WALLPAPER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/** Desenfoque de caja en 3 pasadas (se aproxima a un gaussiano). Rápido a baja resolución. */
object Blur {
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        if (radius >= 1) {
            val tmp = IntArray(w * h)
            repeat(3) {
                boxH(pix, tmp, w, h, radius)
                boxV(tmp, pix, w, h, radius)
            }
        }
        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                sa += p ushr 24; sr += (p shr 16) and 0xff; sg += (p shr 8) and 0xff; sb += p and 0xff
            }
            for (x in 0 until w) {
                dst[row + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[row + (x - r).coerceIn(0, w - 1)]
                val pIn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xff) - ((pOut shr 16) and 0xff)
                sg += ((pIn shr 8) and 0xff) - ((pOut shr 8) and 0xff)
                sb += (pIn and 0xff) - (pOut and 0xff)
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                sa += p ushr 24; sr += (p shr 16) and 0xff; sg += (p shr 8) and 0xff; sb += p and 0xff
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[(y - r).coerceIn(0, h - 1) * w + x]
                val pIn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xff) - ((pOut shr 16) and 0xff)
                sg += ((pIn shr 8) and 0xff) - ((pOut shr 8) and 0xff)
                sb += (pIn and 0xff) - (pOut and 0xff)
            }
        }
    }
}

data class Blob(val x: Float, val y: Float, val r: Float, val color: Int)
data class BuiltinWallpaper(val key: String, val name: String, val group: String, val base: Int, val blobs: List<Blob>) {
    val id get() = "builtin:$key"
}

private fun c(hex: Long) = hex.toInt()

object Builtins {
    val all = listOf(
        BuiltinWallpaper("amanecer", "Amanecer", "Cálidos", c(0xFF6E8FB8), listOf(
            Blob(0.18f, 0.12f, 0.55f, c(0xFFC4D5EC)), Blob(0.85f, 0.18f, 0.45f, c(0xFF5D7FAE)),
            Blob(0.72f, 0.78f, 0.45f, c(0xFFF0A65A)), Blob(0.42f, 0.95f, 0.42f, c(0xFFE58A3C)),
            Blob(0.05f, 0.85f, 0.40f, c(0xFF2E4567)))),
        BuiltinWallpaper("coral", "Coral", "Cálidos", c(0xFFB84A5A), listOf(
            Blob(0.2f, 0.2f, 0.5f, c(0xFFF7B2B7)), Blob(0.8f, 0.3f, 0.5f, c(0xFFF08A5D)),
            Blob(0.5f, 0.95f, 0.5f, c(0xFF6B1F3A)), Blob(0.95f, 0.9f, 0.35f, c(0xFFF2C078)))),
        BuiltinWallpaper("lavanda", "Lavanda", "Cálidos", c(0xFF6B5CA5), listOf(
            Blob(0.15f, 0.25f, 0.5f, c(0xFFF2A7D8)), Blob(0.8f, 0.2f, 0.45f, c(0xFF7FA8F0)),
            Blob(0.6f, 0.9f, 0.5f, c(0xFF9B7BE0)), Blob(0.05f, 0.95f, 0.35f, c(0xFF3C2E6E)))),
        BuiltinWallpaper("oceano", "Océano", "Fríos", c(0xFF0F3B5A), listOf(
            Blob(0.2f, 0.2f, 0.5f, c(0xFF6FD3E0)), Blob(0.75f, 0.35f, 0.5f, c(0xFF1FA6A0)),
            Blob(0.4f, 0.95f, 0.5f, c(0xFF0A2540)), Blob(0.95f, 0.9f, 0.4f, c(0xFFA8E6F0)))),
        BuiltinWallpaper("bosque", "Bosque", "Fríos", c(0xFF1F3B2D), listOf(
            Blob(0.2f, 0.25f, 0.5f, c(0xFFA9D8B8)), Blob(0.8f, 0.3f, 0.45f, c(0xFF4E8C5E)),
            Blob(0.55f, 0.95f, 0.5f, c(0xFF6B7A3A)), Blob(0.05f, 0.9f, 0.35f, c(0xFF12261C)))),
        BuiltinWallpaper("aurora", "Aurora", "Fríos", c(0xFF0A1A2A), listOf(
            Blob(0.25f, 0.3f, 0.45f, c(0xFF2FE0A0)), Blob(0.7f, 0.2f, 0.45f, c(0xFF1B8FA8)),
            Blob(0.8f, 0.85f, 0.5f, c(0xFF6A3FB5)), Blob(0.1f, 0.95f, 0.35f, c(0xFF0A1A2A)))),
        BuiltinWallpaper("medianoche", "Medianoche", "Oscuros", c(0xFF0B0E1A), listOf(
            Blob(0.5f, 1.05f, 0.55f, c(0xFF2B4C9A)), Blob(0.15f, 0.2f, 0.4f, c(0xFF1A1F3A)),
            Blob(0.85f, 0.3f, 0.4f, c(0xFF3A2A6A)))),
        BuiltinWallpaper("grafito", "Grafito", "Oscuros", c(0xFF1E1F22), listOf(
            Blob(0.2f, 0.2f, 0.5f, c(0xFF4A4D55)), Blob(0.8f, 0.8f, 0.5f, c(0xFF2C2E33)),
            Blob(0.7f, 0.15f, 0.35f, c(0xFF8A8F99)))),
        BuiltinWallpaper("carbon", "Carbón", "Oscuros", c(0xFF0D0D0F), listOf(
            Blob(0.5f, 0.95f, 0.45f, c(0xFF3A3A40)), Blob(0.1f, 0.1f, 0.35f, c(0xFF1C1C21)))),
    )

    fun byId(id: String): BuiltinWallpaper? = all.firstOrNull { it.id == id }

    fun render(wp: BuiltinWallpaper, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(wp.base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val unit = max(w, h).toFloat()
        for (b in wp.blobs) {
            val cx = b.x * w
            val cy = b.y * h
            val r = b.r * unit
            paint.shader = RadialGradient(cx, cy, r, intArrayOf(b.color, b.color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
        }
        return bmp
    }
}

data class WallpaperImages(val background: ImageBitmap, val backdrop: ImageBitmap)

class WallpaperEngine(private val context: Context) {
    private val _images = MutableStateFlow<WallpaperImages?>(null)
    val images: StateFlow<WallpaperImages?> = _images
    private val thumbs = ConcurrentHashMap<String, ImageBitmap>()
    private val dir: File get() = File(context.filesDir, "wallpapers").apply { mkdirs() }
    @Volatile private var lastKey: String? = null

    suspend fun render(id: String, blur: Int) = withContext(Dispatchers.Default) {
        val key = "$id#$blur"
        if (key == lastKey && _images.value != null) return@withContext
        val src = runCatching { source(id, 1280, 720) }.getOrNull()
            ?: source(DEFAULT_WALLPAPER, 1280, 720)
        val background = if (blur <= 0) src else {
            val small = scaleCrop(src, 480, 270)
            Blur.blur(small, max(1, blur / 4))
        }
        // El "vidrio" de los paneles usa una versión mucho más desenfocada del mismo fondo.
        val backdrop = Blur.blur(scaleCrop(src, 240, 135), min(60, max(14, blur / 8 + 12)))
        _images.value = WallpaperImages(background.asImageBitmap(), backdrop.asImageBitmap())
        lastKey = key
        saveCache(key, background, backdrop)
    }

    // Caché en disco del fondo ya procesado: al abrir simple se muestra al instante, sin pantalla negra.
    private val cacheBg get() = File(context.cacheDir, "wp_bg.png")
    private val cacheBd get() = File(context.cacheDir, "wp_bd.png")
    private val cacheKey get() = File(context.cacheDir, "wp_key.txt")

    private fun saveCache(key: String, bg: Bitmap, bd: Bitmap) {
        runCatching {
            FileOutputStream(cacheBg).use { bg.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileOutputStream(cacheBd).use { bd.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cacheKey.writeText(key)
        }
    }

    /** Carga sincrónica (pocos milisegundos) del último fondo procesado, si coincide con la configuración. */
    fun loadCached(id: String, blur: Int) {
        runCatching {
            val key = "$id#$blur"
            if (!cacheKey.exists() || cacheKey.readText() != key) return
            val bg = BitmapFactory.decodeFile(cacheBg.absolutePath) ?: return
            val bd = BitmapFactory.decodeFile(cacheBd.absolutePath) ?: return
            _images.value = WallpaperImages(bg.asImageBitmap(), bd.asImageBitmap())
            lastKey = key
        }
    }

    suspend fun thumbnail(id: String): ImageBitmap? = withContext(Dispatchers.Default) {
        thumbs[id] ?: runCatching { scaleCrop(source(id, 320, 180), 256, 144).asImageBitmap() }
            .getOrNull()?.also { thumbs[id] = it }
    }

    fun userWallpapers(): List<String> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".jpg") }
            .sortedByDescending { it.lastModified() }.map { "file:${it.name}" }

    fun delete(id: String) {
        if (!id.startsWith("file:")) return
        File(dir, id.removePrefix("file:")).delete()
        thumbs.remove(id)
    }

    /** Guarda una foto recibida desde el celular. Devuelve el id del fondo o null si no es una imagen válida. */
    fun import(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1920, 1080) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val scale = min(1f, 1920f / max(decoded.width, decoded.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
        val name = "u_${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(dir, name)).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return "file:$name"
    }

    private fun source(id: String, w: Int, h: Int): Bitmap {
        Builtins.byId(id)?.let { return Builtins.render(it, w, h) }
        if (id.startsWith("file:")) {
            val f = File(dir, id.removePrefix("file:"))
            require(f.exists())
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, w, h) }
            val bmp = BitmapFactory.decodeFile(f.absolutePath, opts) ?: error("decode")
            return scaleCrop(bmp, w, h)
        }
        error("unknown wallpaper")
    }

    companion object {
        fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
            var s = 1
            while (srcW / (s * 2) >= reqW && srcH / (s * 2) >= reqH) s *= 2
            return s
        }

        /** Recorta al centro con la proporción destino y escala. */
        fun scaleCrop(src: Bitmap, w: Int, h: Int): Bitmap {
            val targetRatio = w.toFloat() / h
            val srcRatio = src.width.toFloat() / src.height
            val crop = if (srcRatio > targetRatio) {
                val cw = (src.height * targetRatio).toInt()
                Rect((src.width - cw) / 2, 0, (src.width - cw) / 2 + cw, src.height)
            } else {
                val ch = (src.width / targetRatio).toInt()
                Rect(0, (src.height - ch) / 2, src.width, (src.height - ch) / 2 + ch)
            }
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(src, crop, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            return out
        }
    }
}
