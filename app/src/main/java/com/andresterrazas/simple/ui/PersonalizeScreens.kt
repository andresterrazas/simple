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

// Orden, reloj, controles del encabezado, aplicaciones y accesos a ajustes del sistema.

@Composable
fun OrderScreen(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    SettingsPage(title = "Orden", left = { BigIcon(Icons.Sort) }) {
        SectionLabel("Ordenar aplicaciones")
        OrderMode.entries.forEach { m ->
            val sub = when (m) {
                OrderMode.ALPHA -> "De la A a la Z"
                OrderMode.CUSTOM -> "Tu propio orden (usa Mover o Aplicaciones)"
                OrderMode.RECENT -> "Las instaladas más recientemente primero"
            }
            RadioRow(m.label, p.orderMode == m, Modifier.restorableFocus("order", m.name, default = p.orderMode == m), subtitle = sub) {
                app.store.update { it.copy(orderMode = m) }
            }
        }
    }
}

@Composable
fun ClockScreen(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    val now = rememberNow()
    SettingsPage(title = "Reloj", left = { BigIcon(Icons.Clock) }) {
        SectionLabel("Pantalla")
        ClockMode.entries.forEach { m ->
            RadioRow(m.label, p.clockMode == m, Modifier.restorableFocus("clock", m.name, default = p.clockMode == m)) {
                app.store.update { it.copy(clockMode = m) }
            }
        }
        SectionLabel("Formato de fecha")
        DateStyle.entries.forEach { d ->
            RadioRow(d.label(now), p.dateStyle == d, Modifier.restorableFocus("clock", "d_${d.name}")) {
                app.store.update { it.copy(dateStyle = d) }
            }
        }
    }
}

internal fun controlInfo(id: String): Pair<String, ImageVector> = when (id) {
    CONTROL_CUSTOM -> "Personalizaciones" to Icons.Sliders
    CONTROL_SETTINGS -> "Configuración del Fire TV" to Icons.Gear
    CONTROL_WIFI -> "Internet" to Icons.Wifi
    CONTROL_STORE -> "Tienda de aplicaciones" to Icons.Store
    else -> id to Icons.Info
}

@Composable
internal fun ManageRow(
    label: String,
    leading: @Composable () -> Unit,
    dimmed: Boolean,
    actions: @Composable () -> Unit,
) {
    val style = LocalStyle.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 50.dp).glass(Shapes.row, style.panel).padding(horizontal = 22.dp, vertical = 8.dp)
                .graphicsLayer { alpha = if (dimmed) 0.55f else 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(14.dp))
            BasicText(label, style = ts(17.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun ControlsScreen(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    SettingsPage(title = "Controles", left = { BigIcon(Icons.Sliders) }) {
        SectionLabel("Controles del encabezado")
        p.controls.forEachIndexed { i, id ->
            key(id) {
                val (label, icon) = controlInfo(id)
                val hidden = id != CONTROL_CUSTOM && id in p.hiddenControls
                ManageRow(label, leading = { Icon(icon, Color.White, 22.dp) }, dimmed = hidden) {
                    if (id != CONTROL_CUSTOM) {
                        CircleButton(if (hidden) Icons.EyeOff else Icons.Eye, Modifier.restorableFocus("controls", "eye_$id", default = i == 0),
                            iconTint = if (hidden) Color(0xFFFF8A7A) else null) {
                            app.store.update { it.copy(hiddenControls = if (hidden) it.hiddenControls - id else it.hiddenControls + id) }
                        }
                    }
                    if (i > 0) CircleButton(Icons.Up, Modifier.restorableFocus("controls", "up_$id", default = i == 0 && id == CONTROL_CUSTOM)) { app.store.update { it.moveControl(id, -1) } }
                    if (i < p.controls.lastIndex) CircleButton(Icons.Down, Modifier.restorableFocus("controls", "down_$id", default = i == 0 && id == CONTROL_CUSTOM)) { app.store.update { it.moveControl(id, 1) } }
                }
            }
        }
        InfoBlock("Personalizaciones siempre está visible para que puedas volver a esta configuración.")
    }
}

@Composable
internal fun MiniTile(app: SimpleApp, e: AppEntry, mode: IconMode) {
    val v by produceState(app.repo.cached(e.pkg, mode), e.pkg, mode) { value = app.repo.loadVisual(e.pkg, mode) }
    Box(Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(8.dp)).background(Color(v?.color ?: 0xFF2B2F3A.toInt()))) {
        v?.let { Image(it.tile, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun AppsScreen(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    val apps by app.repo.apps.collectAsState()
    val all = remember(apps, p.orderMode, p.customOrder) { orderApps(apps, p) }
    SettingsPage(title = "Aplicaciones", subtitle = "${apps.size} instaladas · ${p.hidden.size} ocultas", left = { BigIcon(Icons.Grid) }) {
        SectionLabel("Tus aplicaciones")
        all.forEachIndexed { i, e ->
            key(e.pkg) {
                val hidden = e.pkg in p.hidden
                ManageRow(e.label, leading = { MiniTile(app, e, p.iconMode(e.pkg)) }, dimmed = hidden) {
                    CircleButton(if (hidden) Icons.EyeOff else Icons.Eye, Modifier.restorableFocus("apps", "eye_${e.pkg}", default = i == 0),
                        iconTint = if (hidden) Color(0xFFFF8A7A) else null) {
                        app.store.update { it.toggleHidden(e.pkg) }
                    }
                    CircleButton(Icons.Up, Modifier.restorableFocus("apps", "up_${e.pkg}").graphicsLayer { alpha = if (i > 0) 1f else 0.35f }) {
                        if (i > 0) app.store.update { it.moveNextTo(apps, e.pkg, all[i - 1].pkg, after = false) }
                    }
                    CircleButton(Icons.Down, Modifier.restorableFocus("apps", "down_${e.pkg}").graphicsLayer { alpha = if (i < all.lastIndex) 1f else 0.35f }) {
                        if (i < all.lastIndex) app.store.update { it.moveNextTo(apps, e.pkg, all[i + 1].pkg, after = true) }
                    }
                }
            }
        }
        if (p.orderMode != OrderMode.CUSTOM) {
            InfoBlock("Al mover una app con las flechas, el orden cambia a Personalizado.")
        }
    }
}

@Composable
fun SystemScreen() {
    val ctx = LocalContext.current
    SettingsPage(title = "Fire TV", subtitle = "Ajustes del sistema", left = { BigIcon(Icons.Tv) }) {
        SectionLabel("Accesos directos")
        SystemActions.shortcuts.forEachIndexed { i, sc ->
            val icon = when (sc.id) {
                "network" -> Icons.Wifi; "display" -> Icons.Display; "apps" -> Icons.Grid
                "remotes" -> Icons.Bluetooth; "accessibility" -> Icons.Person; "device" -> Icons.Info
                "date" -> Icons.Calendar; else -> Icons.Gear
            }
            SettingRow(sc.title, Modifier.restorableFocus("system", sc.id, default = i == 0), icon = icon, subtitle = sc.subtitle) {
                SystemActions.openShortcut(ctx, sc.id)
            }
        }
    }
}
