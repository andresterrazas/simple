package com.andresterrazas.simple

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.andresterrazas.simple.data.AppRepository
import com.andresterrazas.simple.data.PrefsStore
import com.andresterrazas.simple.home.DefaultHome
import com.andresterrazas.simple.wallpaper.WallpaperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SimpleApp : Application() {
    lateinit var store: PrefsStore
    lateinit var repo: AppRepository
    lateinit var wallpapers: WallpaperEngine
    lateinit var weather: com.andresterrazas.simple.data.WeatherRepo
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        com.andresterrazas.simple.diag.CrashLog.install(this)
        store = PrefsStore(this)
        repo = AppRepository(this)
        wallpapers = WallpaperEngine(this)
        repo.loadCachedList()
        weather = com.andresterrazas.simple.data.WeatherRepo(this)
        store.state.value.let { wallpapers.loadCached(it.wallpaper, it.blur) }

        scope.launch {
            repo.refresh()
            seedFavorites()
        }
        scope.launch {
            store.state.map { it.wallpaper to it.blur }.distinctUntilChanged().collectLatest { (id, blur) ->
                runCatching { wallpapers.render(id, blur) }
            }
        }
        // Clima: se actualiza al cambiar la configuración y cada 30 minutos mientras esté visible.
        scope.launch {
            store.state.map { Triple(it.weatherStyle, it.weatherPlace, it.tempUnit) }.distinctUntilChanged().collectLatest { (style, place, unit) ->
                if (style == com.andresterrazas.simple.data.WeatherStyle.OFF) return@collectLatest
                while (true) {
                    val ok = weather.refresh(place, unit)
                    delay(if (ok) 30 * 60_000L else 5 * 60_000L)
                }
            }
        }
        // Cambio automático de fondo entre las fotos subidas.
        scope.launch {
            while (true) {
                runCatching { checkRotation() }
                delay(30_000)
            }
        }
        registerPackageReceiver()
        DefaultHome.enableViaSecure(this)
        com.andresterrazas.simple.home.Recs.enableViaSecure(this)
    }

    private var wnJob: Job? = null

    /** Relee la fila Watch Next del sistema (Android TV); agrupa cambios seguidos. */
    fun refreshWatchNext() {
        wnJob?.cancel()
        wnJob = scope.launch {
            delay(300)
            com.andresterrazas.simple.home.WatchNext.refresh(this@SimpleApp)
        }
    }

    private fun rotationKey(p: com.andresterrazas.simple.data.Prefs, now: Long): String? {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val y = cal.get(java.util.Calendar.YEAR)
        return when (p.rotateMode) {
            com.andresterrazas.simple.data.RotateMode.OFF -> null
            com.andresterrazas.simple.data.RotateMode.HOURLY -> "h$y-${cal.get(java.util.Calendar.DAY_OF_YEAR)}-${cal.get(java.util.Calendar.HOUR_OF_DAY)}"
            com.andresterrazas.simple.data.RotateMode.DAILY -> "d$y-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
            com.andresterrazas.simple.data.RotateMode.WEEKLY -> "w${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.WEEK_OF_YEAR)}"
            com.andresterrazas.simple.data.RotateMode.MONTHLY -> "m$y-${cal.get(java.util.Calendar.MONTH)}"
            com.andresterrazas.simple.data.RotateMode.CUSTOM -> {
                val ms = p.rotateValue.coerceAtLeast(1) * p.rotateUnit.minutes * 60_000L
                // Alineado a la hora local para que "cada 6 horas" cambie a horas redondas.
                val offset = java.util.TimeZone.getDefault().getOffset(now)
                "c${p.rotateUnit.name}${p.rotateValue}-${(now + offset) / ms}"
            }
        }
    }

    /** Fotos subidas, en el orden en que se subieron. */
    fun rotationPool(): List<String> = wallpapers.userWallpapers().sorted()

    private fun checkRotation() {
        val p = store.state.value
        val key = rotationKey(p, System.currentTimeMillis()) ?: return
        if (p.rotateKey.isEmpty()) { store.update { it.copy(rotateKey = key) }; return }
        if (key != p.rotateKey) rotateNow(key)
    }

    /** Cambia al siguiente fondo de la rotación (o a uno al azar). */
    fun rotateNow(newKey: String? = null) {
        val pool = rotationPool()
        store.update { p ->
            if (pool.size < 2) return@update p.copy(rotateKey = newKey ?: p.rotateKey)
            val idx = pool.indexOf(p.wallpaper)
            val next = if (p.rotateShuffle) pool.filter { it != p.wallpaper }.random()
            else pool[(idx + 1).mod(pool.size)]
            p.copy(wallpaper = next, rotateKey = newKey ?: p.rotateKey)
        }
    }

    fun refreshApps(delayMs: Long = 0) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            repo.refresh()
        }
    }

    private fun seedFavorites() {
        if (store.state.value.seeded) return
        val apps = repo.apps.value
        if (apps.isEmpty()) return
        val preferred = listOf(
            "com.netflix.ninja", "com.amazon.firetv.youtube", "com.google.android.youtube.tv",
            "com.wbd.stream", "com.hbo.hbonow", "com.amazon.avod", "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus", "com.apple.atve.amazon.appletv", "com.spotify.tv.android",
            "com.cbs.ott", "com.plexapp.android",
        )
        val installed = apps.map { it.pkg }.toSet()
        val favs = preferred.filter { it in installed }.take(6).toMutableList()
        if (favs.size < 6) {
            apps.sortedBy { it.label.lowercase() }.map { it.pkg }
                .filter { it !in favs }.take(6 - favs.size).forEach { favs.add(it) }
        }
        store.update { it.copy(favorites = favs, seeded = true) }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.data?.schemeSpecificPart?.let { repo.invalidate(it) }
                refreshApps(400)
            }
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
