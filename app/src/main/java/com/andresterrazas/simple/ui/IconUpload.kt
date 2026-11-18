@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.andresterrazas.simple.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.IconMode
import com.andresterrazas.simple.wallpaper.UploadServer

private fun html(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Subir una imagen propia para la tarjeta de una app, desde el celular. */
@Composable
fun IconUploadScreen(app: SimpleApp, nav: Nav, pkg: String) {
    val apps by app.repo.apps.collectAsState()
    val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
    val ip = remember { UploadServer.localIp() }
    val url = ip?.let { "http://$it:${UploadServer.PORT}" }
    var received by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val qr = remember(url) { url?.let { runCatching { UploadServer.qr(it) }.getOrNull() } }

    DisposableEffect(pkg) {
        val server = UploadServer(
            title = "Elige una imagen para la tarjeta de ${html(label)}. Se recorta a formato horizontal (16:9).",
            doneText = "La nueva imagen de ${html(label)} ya está en tu TV.",
        ) { bytes ->
            val ok = app.repo.saveCustomIcon(pkg, bytes)
            if (ok) {
                app.store.update { it.copy(iconModes = it.iconModes + (pkg to IconMode.CUSTOM)) }
                received++
            }
            ok
        }
        try { server.start() } catch (e: Exception) { error = "No se pudo iniciar el servidor (${e.message})" }
        onDispose { server.stop() }
    }

    SettingsPage(title = "Imagen propia", subtitle = label, left = {
        Box(Modifier.size(210.dp).clip(Shapes.hero).background(Color.White).padding(12.dp)) {
            qr?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }) {
        SectionLabel("Cómo hacerlo")
        InfoBlock("1. Conecta tu celular a la misma red Wi‑Fi.\n2. Escanea el código QR o abre esta dirección:")
        BasicText(url ?: "Sin conexión a la red", style = ts(26.sp, FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
        InfoBlock("3. Elige una imagen horizontal (por ejemplo, el logo o póster de la app) y envíala. Se aplica al instante.")
        if (error != null) InfoBlock(error!!)
        if (received > 0) InfoBlock("¡Imagen recibida! Ya se ve en tu inicio.")
        SettingRow("Listo", Modifier.restorableFocus("iconupload", "done", default = true), icon = Icons.Check) { nav.pop() }
    }
}
