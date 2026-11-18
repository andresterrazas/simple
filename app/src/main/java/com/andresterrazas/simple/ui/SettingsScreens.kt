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
            Modifier.size(232.dp, 130.dp).clip(Shapes.hero),
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
        SettingRow("Dock", Modifier.restorableFocus(s, "dock"), icon = Icons.Dock, subtitle = "Opacidad, tamaño, forma y nombres") { nav.push(Screen.Dock) }
        SettingRow("Continuar viendo", Modifier.restorableFocus(s, "recs"), icon = Icons.Tv,
            value = if (p.showRecs) "Activado" else "Desactivado") { nav.push(Screen.RecsSettings) }
        SettingRow("Controles del encabezado", Modifier.restorableFocus(s, "controls"), icon = Icons.Sliders) { nav.push(Screen.Controls) }
        SettingRow("Tamaño de los menús", Modifier.restorableFocus(s, "scale"), icon = Icons.Display, value = p.menuScale.label) {
            val all = com.andresterrazas.simple.data.MenuScale.entries
            app.store.update { it.copy(menuScale = all[(all.indexOf(it.menuScale) + 1) % all.size]) }
        }
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

        if (!DefaultHome.isFireTv) DefaultHomeSection(s)

        if (canWrite && !enabled) {
            SettingRow("Activar automáticamente", Modifier.restorableFocus(s, "auto"), icon = Icons.Check, subtitle = "simple tiene permiso para activarse solo") {
                val ok = DefaultHome.enableViaSecure(ctx)
                Toast.makeText(ctx, if (ok) "Listo, simple está activo" else "No se pudo activar", Toast.LENGTH_SHORT).show()
            }
        }

        SectionLabel(if (DefaultHome.isFireTv) "Opción 1 · Desde el Fire TV" else "Respaldo · Servicio de accesibilidad")
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
private fun MiniTile(app: SimpleApp, e: AppEntry, mode: IconMode) {
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

@Composable
private fun WallpaperPreview(app: SimpleApp) {
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
private fun Thumb(app: SimpleApp, id: String, selected: Boolean, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
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
        SettingRow("Actualizar lista de apps", Modifier.restorableFocus("diag", "refresh", default = true), icon = Icons.Refresh) {
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

@Composable
private fun DockPreview(app: SimpleApp) {
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

@Composable
fun RecsScreen(app: SimpleApp) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val recs by com.andresterrazas.simple.home.Recs.items.collectAsState()
    val enabled by produceState(com.andresterrazas.simple.home.Recs.isEnabled(ctx)) {
        while (true) { value = com.andresterrazas.simple.home.Recs.isEnabled(ctx); delay(1000) }
    }
    val seen by produceState(com.andresterrazas.simple.home.Recs.seenSummary()) {
        while (true) { value = com.andresterrazas.simple.home.Recs.seenSummary(); delay(1500) }
    }
    val s = "recs"
    SettingsPage(title = "Continuar viendo", subtitle = "Sugerencias de tus apps", left = { BigIcon(Icons.Tv) }) {
        ToggleRow("Mostrar en el inicio", p.showRecs, Modifier.restorableFocus(s, "show", default = true),
            subtitle = "Al posarte en una app de streaming, muestra lo que dejaste a medias") {
            app.store.update { it.copy(showRecs = !it.showRecs) }
        }
        SectionLabel("Android TV / Google TV · fila del sistema")
        val wn = com.andresterrazas.simple.home.WatchNext
        val wnAvail = remember { wn.available(ctx) }
        val wnGranted by produceState(wn.granted(ctx)) { while (true) { value = wn.granted(ctx); delay(1000) } }
        if (!wnAvail) {
            InfoBlock("Este dispositivo no tiene la fila \"Continuar viendo\" del sistema (normal en Fire TV). Se usan las sugerencias por notificación de abajo.")
        } else if (wnGranted) {
            InfoBlock("Permiso concedido · ${wn.lastCount} elementos leídos" + (wn.lastError?.let { "\nError: $it" } ?: ""))
        } else {
            SettingRow("Permitir leer Continuar viendo", Modifier.restorableFocus(s, "wn"), icon = Icons.Check,
                subtitle = "Tu TV pedirá confirmar una sola vez") {
                (ctx as? com.andresterrazas.simple.MainActivity)?.requestWatchNextPermission()
            }
            InfoBlock("Si no aparece el diálogo, por ADB:")
            InfoBlock(wn.adbCommand(ctx), mono = true)
        }
        InfoBlock("La sección aparece al posarte en una app de streaming del dock que tenga algo a medias. YouTube está excluido por defecto; cambia cualquier app desde su menú (mantén OK) › Continuar viendo.")

        SectionLabel("Fire TV · sugerencias por notificación")
        InfoBlock(
            "Acceso a notificaciones: ${if (enabled) "activo" else "no activo"}\n" +
                "Tarjetas disponibles (todas las fuentes): ${recs.size}" +
                if (enabled && recs.isEmpty()) "\nAbre y usa algunas apps (YouTube, Prime Video, Plex…); las que publiquen sugerencias aparecerán aquí." else ""
        )
        if (!enabled) {
            SettingRow("Abrir acceso a notificaciones", Modifier.restorableFocus(s, "access"), icon = Icons.Gear,
                subtitle = "Busca simple y permítelo (en algunas TV está en Apps › Acceso especial)") {
                val ok = runCatching {
                    ctx.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true
                }.getOrDefault(false)
                if (!ok) Toast.makeText(ctx, "Tu TV no tiene esa pantalla; usa el comando de ADB", Toast.LENGTH_LONG).show()
            }
            if (DefaultHome.canWriteSecure(ctx)) {
                SettingRow("Activar ahora", Modifier.restorableFocus(s, "enable"), icon = Icons.Check) {
                    val ok = com.andresterrazas.simple.home.Recs.enableViaSecure(ctx)
                    Toast.makeText(ctx, if (ok) "Listo" else "No se pudo activar", Toast.LENGTH_SHORT).show()
                }
            } else {
                InfoBlock("O una sola vez por ADB:")
                InfoBlock(com.andresterrazas.simple.home.Recs.adbCommand(ctx), mono = true)
            }
        }
        if (!DefaultHome.isFireTv) {
            InfoBlock("En Google TV, Google solo deja publicar en esta fila a apps certificadas (Netflix, Prime Video, Disney+, etc.); otras apps pueden no aparecer.")
        }
        SectionLabel("Contenido detectado")
        val apps by app.repo.apps.collectAsState()
        InfoBlock(
            if (recs.isEmpty()) "Nada todavía."
            else recs.take(12).joinToString("\n") { r ->
                val inDock = p.favorites.any { com.andresterrazas.simple.home.Recs.samePackage(it, r.pkg) }
                val name = apps.firstOrNull { com.andresterrazas.simple.home.Recs.samePackage(it.pkg, r.pkg) }?.label ?: "app no instalada"
                "• ${r.title} — $name (${r.pkg}) · ${r.source}${if (inDock) "" else " · no está en el dock"}"
            },
            mono = true,
        )
        SectionLabel("Qué apps envían información")
        InfoBlock(if (seen.isEmpty()) "Nada todavía." else seen.joinToString("\n"), mono = true)
        InfoBlock("simple solo lee las sugerencias que las apps publican para el inicio de TV; no lee mensajes ni datos personales, y nada sale de tu Fire TV.")
    }
}

/** Android TV / Google TV (Xiaomi, Sony, TCL…): ahí sí existe el "inicio predeterminado" del sistema. */
@Composable
private fun DefaultHomeSection(scope: String) {
    val ctx = LocalContext.current
    val isDefault by produceState(DefaultHome.isDefaultHome(ctx)) {
        while (true) { value = DefaultHome.isDefaultHome(ctx); delay(1000) }
    }
    SectionLabel("Inicio predeterminado del sistema")
    SettingRow(
        if (isDefault) "simple es el inicio predeterminado" else "Elegir simple como inicio predeterminado",
        Modifier.restorableFocus(scope, "role"),
        icon = if (isDefault) Icons.Check else Icons.Home,
        subtitle = if (isDefault) "Tu TV abre simple al presionar Inicio, sin trucos" else "Tu TV te pedirá confirmar; elige simple",
    ) { if (!isDefault) requestDefaultHome(ctx) }
    if (!isDefault) {
        InfoBlock("Si tu TV no muestra la opción, conéctate por ADB y ejecuta:")
        InfoBlock(DefaultHome.setHomeAdbCommand(ctx), mono = true)
        InfoBlock("Mientras tanto, el servicio de accesibilidad de abajo también funciona como respaldo.")
    }
}

private fun requestDefaultHome(ctx: android.content.Context) {
    val activity = ctx as? android.app.Activity
    fun tryStart(i: android.content.Intent): Boolean = runCatching {
        if (activity != null) activity.startActivityForResult(i, 42)
        else ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
    if (Build.VERSION.SDK_INT >= 29) {
        val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
            tryStart(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))) return
    }
    if (tryStart(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS))) return
    // Último recurso: el selector de inicio del sistema (con la opción "Siempre").
    val chooser = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
    if (!tryStart(chooser)) Toast.makeText(ctx, "Tu TV no permite elegirlo desde aquí; usa el comando de ADB", Toast.LENGTH_LONG).show()
}
