package com.andresterrazas.simple.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.andresterrazas.simple.BuildConfig
import com.andresterrazas.simple.R
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.AppEntry
import com.andresterrazas.simple.data.CONTROL_CUSTOM
import com.andresterrazas.simple.data.CONTROL_SETTINGS
import com.andresterrazas.simple.data.CONTROL_STORE
import com.andresterrazas.simple.data.CONTROL_WIFI
import com.andresterrazas.simple.data.ClockMode
import com.andresterrazas.simple.data.DEFAULT_WALLPAPER
import com.andresterrazas.simple.data.DateStyle
import com.andresterrazas.simple.data.IconMode
import com.andresterrazas.simple.data.InterceptMode
import com.andresterrazas.simple.data.OrderMode
import com.andresterrazas.simple.data.OverlayStyle
import com.andresterrazas.simple.data.SystemActions
import com.andresterrazas.simple.data.orderApps
import com.andresterrazas.simple.home.DefaultHome
import com.andresterrazas.simple.wallpaper.Builtins
import com.andresterrazas.simple.wallpaper.UploadServer
import kotlinx.coroutines.delay

// Diagnóstico: dispositivo, fluidez, cierres, servicio de inicio.

@Composable
fun DiagnosticsScreen(app: SimpleApp) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val apps by app.repo.apps.collectAsState()
    val enabled = rememberServiceEnabled()
    val events by produceState(DefaultHome.recentEvents()) {
        while (true) { value = DefaultHome.recentEvents(); delay(1000) }
    }
    SettingsPage(title = "Diagnóstico", left = { BigIcon(Icons.Activity) }) {
        SectionLabel("Dispositivo")
        InfoBlock(
            "Modelo: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}\n" +
                "simple ${BuildConfig.VERSION_NAME} · ${apps.size} apps detectadas"
        )
        SectionLabel("Fluidez")
        val perf by produceState(com.andresterrazas.simple.diag.Perf.summary()) {
            while (true) { value = com.andresterrazas.simple.diag.Perf.summary(); delay(1000) }
        }
        InfoBlock(perf, mono = true)
        SettingRow("Reiniciar medición de cuadros", Modifier.restorableFocus("diag", "perfreset"), icon = Icons.Refresh,
            subtitle = "Luego navega un rato y vuelve a ver los números") {
            com.andresterrazas.simple.diag.Perf.reset()
        }

        SectionLabel("Cierres inesperados")
        var crashTick by remember { mutableIntStateOf(0) }
        val crashes = remember(crashTick) { com.andresterrazas.simple.diag.CrashLog.entries() }
        if (crashes.isEmpty()) {
            InfoBlock("Ninguno registrado. 🎉")
        } else {
            crashes.forEach { InfoBlock(it.lines().take(8).joinToString("\n"), mono = true) }
            SettingRow("Borrar registro", Modifier.restorableFocus("diag", "crashclear"), icon = Icons.Trash) {
                com.andresterrazas.simple.diag.CrashLog.clear(); crashTick++
            }
        }

        SectionLabel("Inicio")
        InfoBlock(
            "Servicio habilitado: ${if (enabled) "sí" else "no"}\n" +
                "Servicio en ejecución: ${if (DefaultHome.running) "sí" else "no"}\n" +
                "Permiso de reactivación: ${if (DefaultHome.canWriteSecure(ctx)) "concedido" else "no concedido"}\n" +
                "Modo de detección: ${p.intercept.label}"
        )
        SettingRow("Actualizar lista de apps", Modifier.restorableFocus("diag", "refresh", default = true), icon = Icons.Package) {
            app.refreshApps()
            Toast.makeText(ctx, "Lista actualizada", Toast.LENGTH_SHORT).show()
        }
        SectionLabel("Últimas ventanas detectadas · mándame foto si Inicio falla")
        InfoBlock(if (events.isEmpty()) "Sin eventos todavía (el servicio debe estar activo)." else events.joinToString("\n"), mono = true)
        SectionLabel("Ajustes del sistema disponibles")
        InfoBlock(SystemActions.shortcuts.joinToString("\n") { sc ->
            "${if (SystemActions.resolvable(ctx, sc)) "✓" else "–"}  ${sc.title}"
        })
        SettingRow("Volver a mostrar la configuración inicial", Modifier.restorableFocus("diag", "setup"), icon = Icons.Home) {
            app.store.update { it.copy(setupShown = false) }
            Toast.makeText(ctx, "Se mostrará al reiniciar simple", Toast.LENGTH_SHORT).show()
        }
    }
}
