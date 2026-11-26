package com.andresterrazas.simple.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones desde la propia app: consulta la última release de GitHub, descarga el APK,
 * comprueba que sea simple y más nuevo, y lo entrega al instalador del sistema.
 * El sistema exige la misma firma, así que solo se puede instalar encima un APK nuestro.
 */
object Updater {
    private const val API = "https://api.github.com/repos/andresterrazas/simple/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60_000L
    private const val PREFS = "updater"

    data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Error(val message: String, val release: Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun currentVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "?"

    @Suppress("DEPRECATION")
    private fun currentCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }.getOrDefault(0L)

    /** "1.1.10" > "1.1.9": compara número por número. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Solo la sección de novedades, en texto plano y legible en la TV. */
    internal fun cleanNotes(body: String): String =
        body.substringBefore("## Instalar")
            .lines()
            .filterNot { it.trim().startsWith("## ") }
            .joinToString("\n") { line ->
                line.replace("**", "").replace("`", "")
                    .replaceFirst(Regex("^(\\s*)- "), "$1• ")
            }
            .trim()

    /** Comprobación automática: como mucho cada 6 horas, salvo que se fuerce. */
    suspend fun checkIfDue(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong("lastCheck", 0L)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS && _state.value !is State.Idle) return
        check(ctx)
    }

    suspend fun check(ctx: Context) {
        val busy = _state.value
        if (busy is State.Checking || busy is State.Downloading || busy is State.Installing) return
        _state.value = State.Checking
        _state.value = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "simple-launcher")
                }
                val json = try {
                    if (conn.responseCode != 200) error("GitHub respondió ${conn.responseCode}")
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally { conn.disconnect() }
                val o = JSONObject(json)
                val tag = o.getString("tag_name").trimStart('v')
                val assets = o.getJSONArray("assets")
                var url = ""; var size = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        url = a.getString("browser_download_url"); size = a.optLong("size"); break
                    }
                }
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                if (url.isNotBlank() && isNewer(tag, currentVersion(ctx))) {
                    State.Available(Release(tag, cleanNotes(o.optString("body")), url, size))
                } else State.UpToDate
            }.getOrElse { State.Error("No se pudo buscar: ${it.message ?: "sin conexión"}") }
        }
    }

    /** ¿El sistema deja que simple instale actualizaciones? (Fuentes desconocidas por app.) */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        com.andresterrazas.simple.home.DefaultHome.beginSystemSession()
        val tries = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        )
        for (i in tries) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    suspend fun downloadAndInstall(ctx: Context, r: Release) {
        val busy = _state.value
        if (busy is State.Downloading || busy is State.Installing) return
        _state.value = State.Downloading(r, 0f)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
                val file = File(dir, "simple-${r.version}.apk")
                val conn = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "simple-launcher")
                }
                try {
                    if (conn.responseCode != 200) error("La descarga respondió ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
                    conn.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L; var lastPct = -1
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; _state.value = State.Downloading(r, pct / 100f) }
                                }
                            }
                        }
                    }
                } finally { conn.disconnect() }
                verify(ctx, file)
                file
            }
        }
        result.fold(
            onSuccess = { install(ctx, r, it) },
            onFailure = { _state.value = State.Error("No se pudo descargar: ${it.message ?: "error"}", r) },
        )
    }

    /** Antes de instalar: que el archivo sea simple y de una versión más nueva que la instalada. */
    @Suppress("DEPRECATION")
    private fun verify(ctx: Context, file: File) {
        val info = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: error("El archivo descargado no es un APK válido")
        if (info.packageName != ctx.packageName) error("El APK no es de simple")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (code <= currentCode(ctx)) error("El APK no es más nuevo que el instalado")
    }

    private fun install(ctx: Context, r: Release, file: File) {
        _state.value = State.Installing(r)
        runCatching {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("simple.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags)
                session.commit(pi.intentSender)
            }
        }.onFailure { _state.value = State.Error("No se pudo instalar: ${it.message ?: "error"}", r) }
    }

    internal fun onInstallResult(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                com.andresterrazas.simple.home.DefaultHome.beginSystemSession()
                runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // El sistema reinicia simple con la versión nueva.
            else -> {
                val r = (_state.value as? State.Installing)?.release
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                _state.value = if (r != null) State.Error("La instalación no se completó" + (msg?.let { ": $it" } ?: ""), r)
                else State.Error("La instalación no se completó")
            }
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallResult(context, intent)
}
