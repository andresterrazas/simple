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

@Composable
fun rememberServiceEnabled(): Boolean {
    val ctx = LocalContext.current
    val v by produceState(DefaultHome.isEnabled(ctx)) {
        while (true) {
            value = DefaultHome.isEnabled(ctx)
            delay(1000)
        }
    }
    return v
}

private fun blurLabel(v: Int) = if (v <= 0) "Ninguno" else "$v"

@Composable
fun SettingsMainScreen(app: SimpleApp, nav: Nav) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val enabled = rememberServiceEnabled()
    val s = "main"
    SettingsPage(title = "simple", subtitle = "Versión ${BuildConfig.VERSION_NAME}", left = {
        Image(
            painterResource(R.drawable.banner), null,
            Modifier.size(232.dp, 130.dp).clip(RoundedCornerShape(26.dp)),
            contentScale = ContentScale.Crop,
        )
    }) {
        SettingRow(
            "Configurar simple como inicio predeterminado",
            Modifier.restorableFocus(s, "setup", default = true),
            icon = Icons.Home,
            subtitle = if (enabled) "Activo · simple se abre al presionar Inicio" else "Sin configurar",
        ) { nav.push(Screen.Setup) }

        SectionLabel("Personalizaciones")
        SettingRow("Orden de aplicaciones", Modifier.restorableFocus(s, "order"), icon = Icons.Sort, value = p.orderMode.label) { nav.push(Screen.Order) }
        SettingRow("Reloj", Modifier.restorableFocus(s, "clock"), icon = Icons.Clock, value = p.clockMode.label) { nav.push(Screen.Clock) }
        SettingRow("Controles del encabezado", Modifier.restorableFocus(s, "controls"), icon = Icons.Sliders) { nav.push(Screen.Controls) }
        SettingRow("Fondo", Modifier.restorableFocus(s, "wallpaper"), icon = Icons.ImageIc) { nav.push(Screen.Wallpaper) }

        SectionLabel("Gestión")
        SettingRow(
            "Aplicaciones", Modifier.restorableFocus(s, "apps"), icon = Icons.Grid,
            subtitle = if (p.hidden.isEmpty()) "Ninguna oculta" else "${p.hidden.size} oculta${if (p.hidden.size == 1) "" else "s"}",
        ) { nav.push(Screen.Apps) }

        SectionLabel("Fire TV")
        SettingRow("Ajustes del Fire TV", Modifier.restorableFocus(s, "system"), icon = Icons.Tv, subtitle = "Red, pantalla, controles, apps y más") { nav.push(Screen.System) }
        SettingRow("Abrir el inicio de Amazon", Modifier.restorableFocus(s, "amazon"), icon = Icons.Home, subtitle = "simple se pausa 5 minutos") {
            DefaultHome.pause(5)
            SystemActions.openAmazonHome(ctx)
        }

        SectionLabel("Acerca de")
        SettingRow("Diagnóstico", Modifier.restorableFocus(s, "diag"), icon = Icons.Alert, subtitle = "Estado del servicio y compatibilidad") { nav.push(Screen.Diagnostics) }
    }
}

@Composable
fun SetupScreen(app: SimpleApp, nav: Nav) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val enabled = rememberServiceEnabled()
    val canWrite = remember { DefaultHome.canWriteSecure(ctx) }
    val s = "setup"
    androidx.compose.runtime.LaunchedEffect(Unit) { app.store.update { it.copy(setupShown = true) } }

    SettingsPage(title = "Inicio", subtitle = if (enabled) "simple está activo" else "Falta un paso", left = { BigIcon(Icons.Home) }) {
        SettingRow(
            if (enabled) "simple ya es tu inicio" else "simple aún no es tu inicio",
            Modifier.restorableFocus(s, "status", default = true),
            icon = if (enabled) Icons.Check else Icons.Alert,
            subtitle = if (enabled) "Al presionar Inicio en el control se abrirá simple" else "Activa el servicio de simple con alguna de estas opciones",
        ) { if (!enabled && canWrite) DefaultHome.enableViaSecure(ctx) }

        if (canWrite && !enabled) {
            SettingRow("Activar automáticamente", Modifier.restorableFocus(s, "auto"), icon = Icons.Check, subtitle = "simple tiene permiso para activarse solo") {
                val ok = DefaultHome.enableViaSecure(ctx)
                Toast.makeText(ctx, if (ok) "Listo, simple está activo" else "No se pudo activar", Toast.LENGTH_SHORT).show()
            }
        }

        SectionLabel("Opción 1 · Desde el Fire TV")
        SettingRow("Abrir Accesibilidad", Modifier.restorableFocus(s, "a11y"), icon = Icons.Person,
            subtitle = "Busca simple y actívalo. Si no aparece en la lista, usa la opción 2.") { SystemActions.openAccessibility(ctx) }

        SectionLabel("Opción 2 · Con ADB, una sola vez")
        InfoBlock("1. En el Fire TV: Mi Fire TV › Opciones para desarrolladores › Depuración ADB: activada.\n" +
            "2. Desde tu computadora o una app de ADB en el celular, conéctate a la IP del Fire TV y ejecuta:")
        InfoBlock(DefaultHome.adbCommands(ctx).joinToString("\n\n"), mono = true)
        InfoBlock("El último comando es opcional pero recomendado: permite que simple se vuelva a activar solo si una actualización de Fire OS lo desactiva.")
        SettingRow("Comprobar de nuevo", Modifier.restorableFocus(s, "check"), icon = Icons.Refresh,
            subtitle = if (enabled) "Activo" else "Inactivo") {
            if (DefaultHome.isEnabled(ctx)) Toast.makeText(ctx, "simple está activo", Toast.LENGTH_SHORT).show()
            else if (DefaultHome.enableViaSecure(ctx)) Toast.makeText(ctx, "Listo, simple está activo", Toast.LENGTH_SHORT).show()
            else Toast.makeText(ctx, "Todavía no está activo", Toast.LENGTH_SHORT).show()
        }

        SectionLabel("Detección del botón Inicio")
        InterceptMode.entries.forEach { m ->
            RadioRow(m.label, p.intercept == m, Modifier.restorableFocus(s, "mode_${m.name}"), subtitle = m.description) {
                app.store.update { it.copy(intercept = m) }
            }
        }
    }
}

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

private fun controlInfo(id: String): Pair<String, ImageVector> = when (id) {
    CONTROL_CUSTOM -> "Personalizaciones" to Icons.Sliders
    CONTROL_SETTINGS -> "Configuración del Fire TV" to Icons.Gear
    CONTROL_WIFI -> "Internet" to Icons.Wifi
    CONTROL_STORE -> "Tienda de aplicaciones" to Icons.Store
    else -> id to Icons.Info
}

@Composable
private fun ManageRow(
    label: String,
    leading: @Composable () -> Unit,
    dimmed: Boolean,
    actions: @Composable () -> Unit,
) {
    val style = LocalStyle.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).glass(RoundedCornerShape(24.dp), style.panel).padding(horizontal = 16.dp, vertical = 10.dp)
                .graphicsLayer { alpha = if (dimmed) 0.55f else 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(14.dp))
            BasicText(label, style = ts(17.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun MiniTile(app: SimpleApp, e: AppEntry, mode: IconMode) {
    val v by produceState(app.repo.cached(e.pkg, mode), e.pkg, mode) { value = app.repo.loadVisual(e.pkg, mode) }
    Box(Modifier.size(52.dp, 29.dp).clip(RoundedCornerShape(7.dp)).background(Color(v?.color ?: 0xFF2B2F3A.toInt()))) {
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

@Composable
private fun WallpaperPreview(app: SimpleApp) {
    val images by app.wallpapers.images.collectAsState()
    Box(
        Modifier.size(300.dp, 169.dp).clip(RoundedCornerShape(26.dp))
            .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.2f))
    ) {
        images?.let { Image(it.background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun Thumb(app: SimpleApp, id: String, selected: Boolean, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bmp by produceState(null as androidx.compose.ui.graphics.ImageBitmap?, id) { value = app.wallpapers.thumbnail(id) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.7f, stiffness = 520f), label = "thumb")
    val shape = RoundedCornerShape(18.dp)
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
private fun ThumbGrid(app: SimpleApp, ids: List<String>, current: String, scope: String, onLong: ((String) -> Unit)? = null, onPick: (String) -> Unit) {
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
        Box(Modifier.size(210.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp)) {
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

@Composable
fun DiagnosticsScreen(app: SimpleApp) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val apps by app.repo.apps.collectAsState()
    val enabled = rememberServiceEnabled()
    val events by produceState(DefaultHome.recentEvents()) {
        while (true) { value = DefaultHome.recentEvents(); delay(1000) }
    }
    SettingsPage(title = "Diagnóstico", left = { BigIcon(Icons.Alert) }) {
        SectionLabel("Dispositivo")
        InfoBlock(
            "Modelo: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}\n" +
                "simple ${BuildConfig.VERSION_NAME} · ${apps.size} apps detectadas"
        )
        SectionLabel("Inicio")
        InfoBlock(
            "Servicio habilitado: ${if (enabled) "sí" else "no"}\n" +
                "Servicio en ejecución: ${if (DefaultHome.running) "sí" else "no"}\n" +
                "Permiso de reactivación: ${if (DefaultHome.canWriteSecure(ctx)) "concedido" else "no concedido"}\n" +
                "Modo de detección: ${p.intercept.label}"
        )
        SettingRow("Actualizar lista de apps", Modifier.restorableFocus("diag", "refresh", default = true), icon = Icons.Refresh) {
            app.refreshApps()
            Toast.makeText(ctx, "Lista actualizada", Toast.LENGTH_SHORT).show()
        }
        SectionLabel("Últimas ventanas detectadas")
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
