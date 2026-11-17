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
    }

    override fun onPause() {
        DefaultHome.foreground = false
        super.onPause()
    }

    companion object {
        const val EXTRA_FROM_HOME = "from_home"
    }
}
