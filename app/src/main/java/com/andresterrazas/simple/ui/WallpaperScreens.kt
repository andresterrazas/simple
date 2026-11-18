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

// Fondo de pantalla: galería, desenfoque y subida desde el celular.

internal fun blurLabel(v: Int) = if (v <= 0) "Ninguno" else "$v"

@Composable
internal fun WallpaperPreview(app: SimpleApp) {
    val images by app.wallpapers.images.collectAsState()
    Box(
        Modifier.size(300.dp, 169.dp).clip(Shapes.hero)
            .border(2.dp, Color.White.copy(alpha = 0.35f), Shapes.hero)
            .background(Color.Black.copy(alpha = 0.2f))
    ) {
        images?.let { Image(it.background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
internal fun Thumb(app: SimpleApp, id: String, selected: Boolean, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bmp by produceState(null as androidx.compose.ui.graphics.ImageBitmap?, id) { value = app.wallpapers.thumbnail(id) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.7f, stiffness = 520f), label = "thumb")
    val shape = Shapes.thumb
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(146.dp, 82.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.15f))
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick, onLongClick)
            .focusable(),
    ) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (focused || selected) {
            Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 2.dp, Color.White.copy(alpha = if (focused) 1f else 0.8f), shape))
        }
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Check, Color(0xFF1E2230), 14.dp)
            }
        }
    }
}

@Composable
internal fun ThumbGrid(app: SimpleApp, ids: List<String>, current: String, scope: String, onLong: ((String) -> Unit)? = null, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
        ids.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { id ->
                    key(id) {
                        Thumb(app, id, id == current, Modifier.restorableFocus(scope, id),
                            onLongClick = onLong?.let { f -> { f(id) } }) { onPick(id) }
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperScreen(app: SimpleApp, nav: Nav) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    var userTick by remember { mutableIntStateOf(0) }
    val userIds = remember(userTick, p.wallpaper) { app.wallpapers.userWallpapers() }
    val s = "wallpaper"
    SettingsPage(title = "Fondo", left = { WallpaperPreview(app) }) {
        SectionLabel("Superposición")
        SettingRow("Estilo", Modifier.restorableFocus(s, "style", default = true), icon = Icons.Brush, value = p.overlay.label) {
            app.store.update { it.copy(overlay = if (it.overlay == OverlayStyle.LIGHT) OverlayStyle.DARK else OverlayStyle.LIGHT) }
        }
        SettingRow("Desenfoque", Modifier.restorableFocus(s, "blur"), icon = Icons.ImageIc, value = blurLabel(p.blur)) { nav.push(Screen.Blur) }

        SectionLabel("Origen")
        SettingRow("Subir foto desde el celular", Modifier.restorableFocus(s, "upload"), icon = Icons.Phone,
            subtitle = "Escanea un código QR y elige una foto") { nav.push(Screen.Upload) }

        if (userIds.isNotEmpty()) {
            SectionLabel("Tus fotos · mantén OK para borrar")
            ThumbGrid(app, userIds, p.wallpaper, s, onLong = { id ->
                app.wallpapers.delete(id)
                if (p.wallpaper == id) app.store.update { it.copy(wallpaper = DEFAULT_WALLPAPER) }
                userTick++
                Toast.makeText(ctx, "Foto borrada", Toast.LENGTH_SHORT).show()
            }) { id -> app.store.update { it.copy(wallpaper = id) } }
        }

        Builtins.all.groupBy { it.group }.forEach { (group, list) ->
            SectionLabel(group)
            ThumbGrid(app, list.map { it.id }, p.wallpaper, s) { id -> app.store.update { it.copy(wallpaper = id) } }
        }
    }
}

@Composable
fun BlurScreen(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    val values = listOf(0, 10, 20, 30, 50, 75, 100, 125, 150, 200, 250)
    SettingsPage(title = "Desenfoque", subtitle = "Del fondo de pantalla", left = { WallpaperPreview(app) }) {
        values.forEach { v ->
            RadioRow(blurLabel(v), p.blur == v, Modifier.restorableFocus("blur", "v$v", default = p.blur == v)) {
                app.store.update { it.copy(blur = v) }
            }
        }
    }
}

@Composable
fun UploadScreen(app: SimpleApp, nav: Nav) {
    val ip = remember { UploadServer.localIp() }
    val url = ip?.let { "http://$it:${UploadServer.PORT}" }
    var received by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val qr = remember(url) { url?.let { runCatching { UploadServer.qr(it) }.getOrNull() } }

    DisposableEffect(Unit) {
        val server = UploadServer { bytes ->
            val id = app.wallpapers.import(bytes)
            if (id != null) {
                app.store.update { it.copy(wallpaper = id) }
                received++
                true
            } else false
        }
        try { server.start() } catch (e: Exception) { error = "No se pudo iniciar el servidor (${e.message})" }
        onDispose { server.stop() }
    }

    SettingsPage(title = "Subir foto", subtitle = "Desde tu celular", left = {
        Box(Modifier.size(210.dp).clip(Shapes.hero).background(Color.White).padding(12.dp)) {
            qr?.let { Image(it, null, Modifier.fillMaxSize()) }
        }
    }) {
        SectionLabel("Cómo hacerlo")
        InfoBlock("1. Conecta tu celular a la misma red Wi‑Fi que el Fire TV.\n2. Escanea el código QR con la cámara, o abre esta dirección en el navegador:")
        BasicText(
            url ?: "Sin conexión a la red",
            style = ts(26.sp, FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
        InfoBlock("3. Elige una foto y toca \"Enviar a la TV\". Se aplicará como fondo al instante.")
        if (error != null) InfoBlock(error!!)
        if (received > 0) InfoBlock("¡Foto recibida! Ya es tu nuevo fondo.")
        SettingRow("Listo", Modifier.restorableFocus("upload", "done", default = true), icon = Icons.Check) { nav.pop() }
    }
}
