package com.andresterrazas.simple

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.andresterrazas.simple.home.DefaultHome
import com.andresterrazas.simple.ui.FocusMemory
import com.andresterrazas.simple.ui.Nav
import com.andresterrazas.simple.ui.SimpleRoot
import com.andresterrazas.simple.ui.UiState

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private val ui = UiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SimpleApp
        setContent { SimpleRoot(app, ui, nav) }
        askWatchNextOnce()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Botón Inicio (o el servicio) con simple ya abierto: volver a la pantalla principal.
        nav.reset()
        FocusMemory.map.clear()
        ui.reset()
    }

    override fun onResume() {
        super.onResume()
        DefaultHome.foreground = true
        (application as SimpleApp).refreshApps(250)
        (application as SimpleApp).refreshWatchNext()
    }

    override fun onPause() {
        DefaultHome.foreground = false
        super.onPause()
    }

    /** En Android TV pide una sola vez el permiso para leer "Continuar viendo". */
    private fun askWatchNextOnce() {
        val wn = com.andresterrazas.simple.home.WatchNext
        if (!wn.available(this) || wn.granted(this)) return
        val sp = getSharedPreferences("simple", MODE_PRIVATE)
        if (sp.getBoolean("askedTvListings", false)) return
        sp.edit().putBoolean("askedTvListings", true).apply()
        requestWatchNextPermission()
    }

    fun requestWatchNextPermission() {
        runCatching { requestPermissions(arrayOf(com.andresterrazas.simple.home.WatchNext.PERMISSION), 77) }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) (application as SimpleApp).refreshWatchNext()
    }

    companion object {
        const val EXTRA_FROM_HOME = "from_home"
    }
}
