package com.andresterrazas.simple.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Continuar viendo" de Android TV / Google TV: la fila Watch Next del sistema (TvProvider).
 * Se lee con el permiso READ_TV_LISTINGS (se concede con un diálogo una sola vez), igual que
 * hacen launchers abiertos como LTvLauncher/FLauncher. En Fire TV esta fuente suele estar vacía y
 * se usan las recomendaciones por notificación.
 */
object WatchNext {
    const val PERMISSION = "android.permission.READ_TV_LISTINGS"
    private const val PERMISSION_WN = "com.android.providers.tv.permission.READ_WRITE_WATCH_NEXT_PROGRAMS"
    private var observer: ContentObserver? = null

    fun available(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 26 &&
        runCatching { ctx.packageManager.resolveContentProvider(TvContract.AUTHORITY, 0) != null }.getOrDefault(false)

    fun granted(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(PERMISSION_WN) == PackageManager.PERMISSION_GRANTED

    fun adbCommand(ctx: Context) = "adb shell pm grant ${ctx.packageName} $PERMISSION"

    @Volatile var lastCount = 0
    @Volatile var lastError: String? = null

    suspend fun refresh(ctx: Context) = withContext(Dispatchers.IO) {
        if (!available(ctx) || !granted(ctx)) return@withContext
        val list = runCatching { query(ctx) }.onFailure { lastError = it.javaClass.simpleName + ": " + it.message }.getOrDefault(emptyList())
        lastCount = list.size
        Recs.setWatchNext(list)
        watch(ctx)
    }

    private fun watch(ctx: Context) {
        if (observer != null || Build.VERSION.SDK_INT < 26) return
        val app = ctx.applicationContext as com.andresterrazas.simple.SimpleApp
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { app.refreshWatchNext() }
        }
        runCatching { ctx.contentResolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, o) }
            .onSuccess { observer = o }
    }

    private fun query(ctx: Context): List<Rec> {
        if (Build.VERSION.SDK_INT < 26) return emptyList()
        val cols = arrayOf(
            TvContract.WatchNextPrograms._ID,
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
        )
        val out = ArrayList<Rec>()
        ctx.contentResolver.query(
            TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC",
        )?.use { cur ->
            fun str(i: Int) = if (cur.isNull(i)) "" else cur.getString(i).orEmpty()
            fun lng(i: Int) = if (cur.isNull(i)) 0L else cur.getLong(i)
            while (cur.moveToNext() && out.size < 60) {
                if (!cur.isNull(13) && cur.getInt(13) == 0) continue
                val pkg = str(1)
                var title = str(2)
                val episode = str(3)
                if (title.isBlank()) title = episode
                if (pkg.isBlank() || title.isBlank()) continue
                val se = listOf(str(4).takeIf { it.isNotBlank() }?.let { "T$it" }, str(5).takeIf { it.isNotBlank() }?.let { "E$it" })
                    .filterNotNull().joinToString(" ")
                val subtitle = listOf(se, episode.takeIf { it != title }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { str(6) }
                var time = lng(7)
                if (time in 1 until 10_000_000_000L) time *= 1000
                val pos = lng(8); val dur = lng(9)
                val intent = str(10).takeIf { it.isNotBlank() }?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
                val image = str(11).ifBlank { str(12) }.ifBlank { null }
                out += Rec(
                    key = "wn:${cur.getLong(0)}",
                    pkg = pkg,
                    title = title,
                    subtitle = subtitle,
                    image = null,
                    intent = null,
                    time = time,
                    progress = if (dur > 0 && pos > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null,
                    launch = intent,
                    imageUri = image,
                    source = "Watch Next",
                    episode = se,
                    remainingMs = if (dur > 0 && pos in 1 until dur) dur - pos else 0,
                )
            }
        }
        return out
    }
}

/** Carga y guarda en memoria las imágenes de las tarjetas (http o content://). */
object RecImages {
    private val cache = LruCache<String, ImageBitmap>(48)

    fun cached(uri: String?): ImageBitmap? = uri?.let { cache.get(it) }

    suspend fun load(ctx: Context, uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(uri)?.let { return@withContext it }
        val bytes = runCatching {
            val u = Uri.parse(uri)
            when (u.scheme?.lowercase()) {
                "http", "https" -> (URL(uri).openConnection() as HttpURLConnection).run {
                    connectTimeout = 6000; readTimeout = 8000; instanceFollowRedirects = true
                    inputStream.use { it.readBytes() }
                }
                else -> ctx.contentResolver.openInputStream(u)?.use { it.readBytes() }
            }
        }.getOrNull() ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        bmp.asImageBitmap().also { cache.put(uri, it) }
    }
}
