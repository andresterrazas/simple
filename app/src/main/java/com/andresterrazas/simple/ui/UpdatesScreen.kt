package com.andresterrazas.simple.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Actualizaciones de simple desde la propia app.

/** Texto corto del estado, para la fila de Configuración. */
internal fun updateSummary(state: Updater.State): String = when (state) {
    Updater.State.Idle -> ""
    Updater.State.Checking -> "Buscando…"
    Updater.State.UpToDate -> "Al día"
    is Updater.State.Available -> "Nueva: ${state.release.version}"
    is Updater.State.Downloading -> "Descargando ${(state.progress * 100).toInt()} %"
    is Updater.State.Installing -> "Instalando…"
    is Updater.State.Error -> if (state.release != null) "Nueva: ${state.release.version}" else "Sin conexión"
}

@Composable
fun UpdatesScreen(app: SimpleApp) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val state by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val s = "updates"
    val current = Updater.currentVersion(ctx)
    // El permiso se concede fuera de simple; se vuelve a revisar cada segundo mientras la pantalla está abierta.
    val canInstall by produceState(Updater.canInstall(ctx)) {
        while (true) { value = Updater.canInstall(ctx); delay(1000) }
    }
    LaunchedEffect(Unit) {
        if (state is Updater.State.Idle) Updater.check(ctx)
    }
    val release = when (val st = state) {
        is Updater.State.Available -> st.release
        is Updater.State.Downloading -> st.release
        is Updater.State.Installing -> st.release
        is Updater.State.Error -> st.release
        else -> null
    }

    SettingsPage(
        title = "Actualizaciones",
        subtitle = "simple $current",
        left = { BigIcon(Icons.Download) },
    ) {
        if (release != null) {
            SectionLabel("simple ${release.version} disponible")
            if (!canInstall) {
                InfoBlock("Para instalar actualizaciones, permite que simple instale apps. Se hace una sola vez.")
                SettingRow(
                    "Permitir instalar actualizaciones", Modifier.restorableFocus(s, "perm", default = true), icon = Icons.ShieldCheck,
                    subtitle = if (com.andresterrazas.simple.home.DefaultHome.isFireTv) "Mi Fire TV › Opciones para desarrolladores › Instalar apps desconocidas › simple"
                    else "Activa simple en la lista de apps que pueden instalar otras apps",
                ) { Updater.openInstallPermission(ctx) }
            } else {
                val (title, value) = when (val st = state) {
                    is Updater.State.Downloading -> "Descargando" to "${(st.progress * 100).toInt()} %"
                    is Updater.State.Installing -> "Instalando" to "Confirma en la pantalla"
                    is Updater.State.Error -> "Reintentar" to ""
                    else -> "Descargar e instalar" to (if (release.size > 0) "%.1f MB".format(release.size / 1_048_576f) else "")
                }
                SettingRow(title, Modifier.restorableFocus(s, "install", default = true), icon = Icons.Download, value = value.ifBlank { null }) {
                    if (state is Updater.State.Downloading || state is Updater.State.Installing) return@SettingRow
                    scope.launch { Updater.downloadAndInstall(ctx, release) }
                }
            }
            (state as? Updater.State.Error)?.let { InfoBlock(it.message) }
            if (release.notes.isNotBlank()) {
                SectionLabel("Novedades")
                NotesBlock(release.notes)
            }
        } else {
            SectionLabel("Estado")
            SettingRow(
                "Buscar actualizaciones", Modifier.restorableFocus(s, "check", default = true), icon = Icons.Refresh,
                value = updateSummary(state).ifBlank { null },
            ) { scope.launch { Updater.check(ctx) } }
            (state as? Updater.State.Error)?.let { InfoBlock(it.message) }
            if (state is Updater.State.UpToDate) InfoBlock("Tienes la versión más reciente de simple.")
        }

        SectionLabel("Automático")
        ToggleRow(
            "Buscar automáticamente", p.autoUpdateCheck, Modifier.restorableFocus(s, "auto"),
            subtitle = "Al encender y cada 6 horas. Si hay una versión nueva, verás un punto en el botón de ajustes de simple.",
        ) { app.store.update { it.copy(autoUpdateCheck = !it.autoUpdateCheck) } }
    }
}

/**
 * Novedades de la release. En TV solo se desplaza hasta lo que recibe el foco, así que cada párrafo
 * es enfocable (con un resaltado discreto) para poder leer notas largas bajando con el control.
 */
@Composable
private fun NotesBlock(notes: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        notes.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            var focused by remember { mutableStateOf(false) }
            val bg by animateColorAsState(if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent, tween(140), label = "noteBg")
            BasicText(
                line,
                style = ts(14.sp, FontWeight.Medium, Color.White.copy(alpha = if (focused) 1f else 0.86f)).copy(lineHeight = 20.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
