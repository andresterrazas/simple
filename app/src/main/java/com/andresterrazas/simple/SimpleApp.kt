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
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        com.andresterrazas.simple.diag.CrashLog.install(this)
        store = PrefsStore(this)
        repo = AppRepository(this)
        wallpapers = WallpaperEngine(this)
        repo.loadCachedList()
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
