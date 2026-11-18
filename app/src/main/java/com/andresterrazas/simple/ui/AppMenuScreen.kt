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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.AppVisual
import com.andresterrazas.simple.data.IconMode
import com.andresterrazas.simple.data.SystemActions

@Composable
private fun rememberVisual(app: SimpleApp, pkg: String, mode: IconMode): AppVisual? {
    val v by produceState(app.repo.cached(pkg, mode), pkg, mode) { value = app.repo.loadVisual(pkg, mode) }
    return v
}

private const val TINT_ALPHA = 0.58f
private const val SHADE_ALPHA = 0.10f

/** Fondo teñido con el color de la app (sobre el fondo de pantalla normal, como en el inicio). */
@Composable
private fun TintedBackdrop(app: SimpleApp, color: Int?) {
    val images by app.wallpapers.images.collectAsState()
    val tint by animateColorAsState(Color(color ?: 0xFF2B2F3A.toInt()), tween(300), label = "tint")
    Box(Modifier.fillMaxSize()) {
        images?.let { Image(it.background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(tint.copy(alpha = TINT_ALPHA)))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SHADE_ALPHA)))
    }
}

/**
 * Versión teñida del fondo muy desenfocado: los paneles del menú la usan como "vidrio", así se ven
 * cristalinos y del mismo tono que la app en lugar de mostrar el azul del fondo original.
 */
@Composable
private fun rememberTintedGlass(color: Int?): ImageBitmap? {
    val backdrop = LocalBackdrop.current ?: return null
    return remember(backdrop, color) {
        val src = backdrop.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val c = android.graphics.Canvas(src)
        val base = color ?: 0xFF2B2F3A.toInt()
        c.drawColor(android.graphics.Color.argb((TINT_ALPHA * 255).toInt(), android.graphics.Color.red(base), android.graphics.Color.green(base), android.graphics.Color.blue(base)))
        c.drawColor(android.graphics.Color.argb((SHADE_ALPHA * 255).toInt(), 0, 0, 0))
        src.asImageBitmap()
    }
}

@Composable
private fun AppHero(app: SimpleApp, pkg: String, mode: IconMode, label: String) {
    val visual = rememberVisual(app, pkg, mode)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(232.dp, 130.dp).clip(Shapes.hero).background(Color.White.copy(alpha = 0.15f))) {
            visual?.let { Image(it.tile, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        BasicText(label, style = ts(30.sp, FontWeight.Bold), modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun PillButton(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "pill")
    val bg by animateColorAsState(if (focused) style.focusedFill else Color.White.copy(alpha = 0.18f), tween(140), label = "pillBg")
    val fg = if (focused) style.focusedText else Color.White
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, fg, 20.dp)
        Spacer(Modifier.width(10.dp))
        BasicText(text, style = ts(17.sp, FontWeight.SemiBold, fg), maxLines = 1)
    }
}

@Composable
fun AppMenuScreen(app: SimpleApp, ui: UiState, nav: Nav, pkg: String) {
    val ctx = LocalContext.current
    val prefs by app.store.state.collectAsState()
    val apps by app.repo.apps.collectAsState()
    val entry = apps.firstOrNull { it.pkg == pkg }
    val mode = prefs.iconMode(pkg)
    val visual = rememberVisual(app, pkg, mode)
    val isFav = pkg in prefs.favorites
    val isHidden = pkg in prefs.hidden
    val scope = "menu:$pkg"

    Box(Modifier.fillMaxSize()) {
        val glass = rememberTintedGlass(visual?.color)
        TintedBackdrop(app, visual?.color)
        CompositionLocalProvider(LocalBackdrop provides glass) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                AppHero(app, pkg, mode, entry?.label ?: pkg)
            }
            Column(
                Modifier.weight(0.58f).fillMaxHeight().padding(end = 52.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    PillButton(Icons.Move, "Mover", Modifier.restorableFocus(scope, "move", default = true)) {
                        ui.moving = pkg
                        if (!isFav) ui.expanded = true
                        ui.lastFocused = pkg
                        ui.focusTick++
                        nav.pop()
                    }
                    PillButton(if (isFav) Icons.Minus else Icons.Star, if (isFav) "Quitar de favoritos" else "Agregar a favoritos", Modifier.restorableFocus(scope, "fav")) {
                        app.store.update { it.toggleFavorite(pkg) }
                        ui.focusTick++
                        nav.pop()
                    }
                    PillButton(if (isHidden) Icons.Eye else Icons.EyeOff, if (isHidden) "Mostrar" else "Ocultar", Modifier.restorableFocus(scope, "hide")) {
                        app.store.update { it.toggleHidden(pkg) }
                        ui.lastFocused = null
                        ui.focusTick++
                        nav.pop()
                    }
                }
                SettingRow("Cambiar ícono", Modifier.restorableFocus(scope, "icon"), icon = Icons.Brush, value = mode.label) { nav.push(Screen.IconPicker(pkg)) }
                val recsOn = pkg !in prefs.recsHidden
                ToggleRow("Continuar viendo", recsOn, Modifier.restorableFocus(scope, "recs"),
                    subtitle = "Mostrar lo que dejaste a medias al posarte en esta app") {
                    app.store.update { it.copy(recsHidden = if (recsOn) it.recsHidden + pkg else it.recsHidden - pkg) }
                }
                SettingRow("Información", Modifier.restorableFocus(scope, "info"), icon = Icons.Info) { SystemActions.openAppInfo(ctx, pkg) }
                SettingRow("Tienda de aplicaciones", Modifier.restorableFocus(scope, "store"), icon = Icons.Store) { SystemActions.openInStore(ctx, pkg) }
                SettingRow("Desinstalar", Modifier.restorableFocus(scope, "uninstall"), icon = Icons.Trash, danger = true) { SystemActions.uninstall(ctx, pkg) }
            }
        }
        }
    }
}

@Composable
fun IconPickerScreen(app: SimpleApp, nav: Nav, pkg: String) {
    val prefs by app.store.state.collectAsState()
    val apps by app.repo.apps.collectAsState()
    val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
    val mode = prefs.iconMode(pkg)
    val visual = rememberVisual(app, pkg, mode)
    val banner = rememberVisual(app, pkg, IconMode.BANNER)
    Box(Modifier.fillMaxSize()) {
        val glass = rememberTintedGlass(visual?.color)
        TintedBackdrop(app, visual?.color)
        CompositionLocalProvider(LocalBackdrop provides glass) {
        SettingsPage(title = "Cambiar ícono", subtitle = listOf(label, visual?.source.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), left = {
            Box(Modifier.size(232.dp, 130.dp).clip(Shapes.hero).background(Color.White.copy(alpha = 0.15f))) {
                visual?.let { Image(it.tile, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High) }
            }
        }) {
            SectionLabel("Estilo del ícono")
            val hasCustom = remember(prefs.iconModes) { app.repo.customIconFile(pkg).exists() }
            IconMode.entries.filter { it != IconMode.CUSTOM || hasCustom }.forEach { m ->
                val sub = when (m) {
                    IconMode.AUTO -> "Banner de TV si existe; si no, ícono sobre su color"
                    IconMode.BANNER -> if (banner?.hasBanner == true) "Imagen horizontal de la app" else "Esta app no tiene banner de TV"
                    IconMode.ICON -> "Ícono de la app en alta resolución"
                    IconMode.CUSTOM -> "La imagen que subiste desde tu celular"
                }
                RadioRow(m.label, selected = mode == m, subtitle = sub,
                    modifier = Modifier.restorableFocus("icon:$pkg", m.name, default = m == mode)) {
                    app.store.update { p ->
                        p.copy(iconModes = if (m == IconMode.AUTO) p.iconModes - pkg else p.iconModes + (pkg to m))
                    }
                }
            }
            SectionLabel("Personalizar")
            SettingRow("Subir imagen desde el celular", Modifier.restorableFocus("icon:$pkg", "upload"), icon = Icons.Phone,
                subtitle = "Usa cualquier logo o póster como tarjeta") { nav.push(Screen.IconUpload(pkg)) }
        }
        }
    }
}
