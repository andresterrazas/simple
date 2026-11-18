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

// Apariencia del dock con vista previa.

@Composable
internal fun DockPreview(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    val apps by app.repo.apps.collectAsState()
    val style = LocalStyle.current
    val look = p.tileLook()
    val sample = remember(apps, p.favorites) {
        val byPkg = apps.associateBy { it.pkg }
        (p.favorites.mapNotNull { byPkg[it] } + apps).distinctBy { it.pkg }.take(3)
    }
    // Vista previa a escala: tres apps con la apariencia elegida, la primera "enfocada". Siempre cabe completa.
    val rowW = look.width.value * 3 + look.gap.value * 2 + 32f
    val scale = (300f / rowW).coerceAtMost(0.9f)
    Box(
        Modifier.size(330.dp, 150.dp).clip(Shapes.hero).background(Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                .glass(RoundedCornerShape(p.dockShape.panelRadius.dp), dockTint(p, style), if (p.dockOpacity == com.andresterrazas.simple.data.DockOpacity.NONE) 0f else 1f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(look.gap),
        ) {
            sample.forEachIndexed { i, e ->
                val v by produceState(app.repo.cached(e.pkg, p.iconMode(e.pkg)), e.pkg) { value = app.repo.loadVisual(e.pkg, p.iconMode(e.pkg)) }
                val focused = i == 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(if (focused) 1f else 0f)) {
                    val shape = RoundedCornerShape(look.radius)
                    Box(
                        Modifier.size(look.width, look.height)
                            .graphicsLayer { val z = if (focused) look.zoom else 1f; scaleX = z; scaleY = z }
                            .clip(shape).background(Color(v?.color ?: 0xFF2B2F3A.toInt()))
                    ) {
                        v?.let { Image(it.tile, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        if (focused && look.outline) Box(Modifier.fillMaxSize().border(3.dp, if (v?.light == true) Color(0xFF8B94A3) else Color.White, shape))
                    }
                    val labelAlpha = when (look.labels) {
                        com.andresterrazas.simple.data.LabelMode.FOCUS -> if (focused) 1f else 0f
                        com.andresterrazas.simple.data.LabelMode.ALWAYS -> if (focused) 1f else 0.7f
                        com.andresterrazas.simple.data.LabelMode.NEVER -> 0f
                    }
                    BasicText(
                        e.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = ts(14.sp, FontWeight.SemiBold),
                        modifier = Modifier.width(look.width).padding(top = 8.dp).height((look.labelH - 8.dp).coerceAtLeast(0.dp))
                            .graphicsLayer { alpha = labelAlpha },
                    )
                }
            }
        }
    }
}

@Composable
fun DockScreen(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    val s = "dock"
    SettingsPage(title = "Dock", subtitle = "Así se verá tu fila de apps", left = { DockPreview(app) }) {
        SectionLabel("Fondo del dock")
        com.andresterrazas.simple.data.DockOpacity.entries.forEach { o ->
            RadioRow(o.label, p.dockOpacity == o, Modifier.restorableFocus(s, "op_${o.name}", default = p.dockOpacity == o)) {
                app.store.update { it.copy(dockOpacity = o) }
            }
        }
        ToggleRow(
            "Mostrar la siguiente fila", p.peekRow, Modifier.restorableFocus(s, "peek"),
            subtitle = "Asoma bajo el dock, como pista de que hay más apps",
        ) { app.store.update { it.copy(peekRow = !it.peekRow) } }
        SectionLabel("Ancho del dock")
        com.andresterrazas.simple.data.DockSize.entries.forEach { z ->
            RadioRow(z.label, p.dockSize == z, Modifier.restorableFocus(s, "size_${z.name}"), subtitle = "${(z.widthFraction * 100).toInt()} % del ancho de la pantalla") {
                app.store.update { it.copy(dockSize = z) }
            }
        }
        SectionLabel("Forma")
        com.andresterrazas.simple.data.DockShape.entries.forEach { f ->
            RadioRow(f.label, p.dockShape == f, Modifier.restorableFocus(s, "shape_${f.name}")) {
                app.store.update { it.copy(dockShape = f) }
            }
        }
        SectionLabel("Nombres de las apps")
        com.andresterrazas.simple.data.LabelMode.entries.forEach { l ->
            RadioRow(l.label, p.labelMode == l, Modifier.restorableFocus(s, "label_${l.name}")) {
                app.store.update { it.copy(labelMode = l) }
            }
        }
        SectionLabel("Al enfocar una app")
        com.andresterrazas.simple.data.FocusZoom.entries.forEach { z ->
            RadioRow("Zoom ${z.label.lowercase()}", p.focusZoom == z, Modifier.restorableFocus(s, "zoom_${z.name}")) {
                app.store.update { it.copy(focusZoom = z) }
            }
        }
        ToggleRow(
            "Contorno al enfocar", p.focusOutline, Modifier.restorableFocus(s, "outline"),
            subtitle = "Blanco, o gris en apps claras para que siempre se distinga",
        ) { app.store.update { it.copy(focusOutline = !it.focusOutline) } }
    }
}
