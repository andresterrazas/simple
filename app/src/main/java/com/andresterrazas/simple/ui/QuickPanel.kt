package com.andresterrazas.simple.ui

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.widget.Toast
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
import com.andresterrazas.simple.data.SystemActions
import com.andresterrazas.simple.home.DefaultHome
import com.andresterrazas.simple.home.HomeService
import com.andresterrazas.simple.wallpaper.UploadServer

private data class QuickAction(val id: String, val label: String, val icon: ImageVector, val run: () -> Unit)

@Composable
private fun QuickButton(action: QuickAction, modifier: Modifier = Modifier) {
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(dampingRatio = 0.7f, stiffness = 520f), label = "qScale")
    val bg by animateColorAsState(if (focused) style.focusedFill else Color.White.copy(alpha = 0.16f), tween(140), label = "qBg")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
        Box(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(78.dp)
                .clip(CircleShape)
                .background(bg)
                .onFocusChanged { focused = it.isFocused }
                .dpadActions(action.run)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, if (focused) style.focusedText else Color.White, 32.dp)
        }
        BasicText(
            action.label,
            style = ts(15.sp, FontWeight.SemiBold).copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 12.dp).graphicsLayer { alpha = if (focused) 1f else 0.8f },
        )
    }
}

/** Panel rápido al mantener presionado Inicio (cuando el modo directo está activo). */
@Composable
fun QuickPanelScreen(app: SimpleApp, nav: Nav) {
    val ctx = LocalContext.current
    val now = rememberNow()
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    val actions = listOf(
        QuickAction("sleep", "Dormir", Icons.Moon) {
            val ok = Build.VERSION.SDK_INT >= 28 && HomeService.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            if (!ok) toast("Tu Fire TV no permitió dormir desde simple")
        },
        QuickAction("power", "Energía", Icons.Power) {
            if (!HomeService.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) toast("El menú de energía no está disponible")
        },
        QuickAction("wifi", "Wi‑Fi", Icons.Wifi) { SystemActions.openWifi(ctx) },
        QuickAction("remotes", "Controles", Icons.Bluetooth) { SystemActions.openShortcut(ctx, "remotes") },
        QuickAction("settings", "Ajustes", Icons.Gear) { SystemActions.openSettings(ctx) },
        QuickAction("amazon", "Inicio Amazon", Icons.Tv) {
            DefaultHome.pause(5)
            SystemActions.openAmazonHome(ctx)
        },
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                java.text.SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(now),
                style = ts(64.sp, FontWeight.Bold),
            )
            BasicText("Panel rápido", style = ts(18.sp, FontWeight.Medium, Color.White.copy(alpha = 0.8f)), modifier = Modifier.padding(bottom = 36.dp))
            Row(
                Modifier.glass(RoundedCornerShape(44.dp), LocalStyle.current.panel).padding(horizontal = 26.dp, vertical = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions.forEachIndexed { i, a ->
                    QuickButton(
                        a,
                        Modifier.restorableFocus("quick", a.id, default = i == 0).focusProperties {
                            if (i == 0) left = FocusRequester.Cancel
                            if (i == actions.lastIndex) right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        },
                    )
                }
            }
            BasicText("Atrás para cerrar", style = ts(14.sp, FontWeight.Medium, Color.White.copy(alpha = 0.6f)), modifier = Modifier.padding(top = 26.dp))
        }
    }
}

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
        Box(Modifier.size(210.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp)) {
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
