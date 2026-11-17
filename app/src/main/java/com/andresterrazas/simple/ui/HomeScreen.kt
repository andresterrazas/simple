package com.andresterrazas.simple.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.AppEntry
import com.andresterrazas.simple.data.CONTROL_CUSTOM
import com.andresterrazas.simple.data.CONTROL_SETTINGS
import com.andresterrazas.simple.data.CONTROL_STORE
import com.andresterrazas.simple.data.CONTROL_WIFI
import com.andresterrazas.simple.data.ClockMode
import com.andresterrazas.simple.data.Prefs
import com.andresterrazas.simple.data.SystemActions
import com.andresterrazas.simple.data.orderApps
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val COLS = 6
private val TILE_W = 124.dp
private val TILE_GAP = 18.dp
private val PANEL_PAD = 18.dp
private val LABEL_H = 30.dp

@Composable
fun HomeScreen(app: SimpleApp, ui: UiState, nav: Nav) {
    val prefs by app.store.state.collectAsState()
    val apps by app.repo.apps.collectAsState()
    val ordered = remember(apps, prefs.orderMode, prefs.customOrder, prefs.hidden) {
        orderApps(apps, prefs).filter { it.pkg !in prefs.hidden }
    }
    val favs = remember(ordered, prefs.favorites) {
        val byPkg = ordered.associateBy { it.pkg }
        prefs.favorites.mapNotNull { byPkg[it] }
    }
    val others = remember(ordered, prefs.favorites) { ordered.filter { it.pkg !in prefs.favorites } }

    val hasFavs = favs.isNotEmpty()
    val expandedVisual = ui.expanded && hasFavs
    val gridVisible = ui.expanded || !hasFavs

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun fr(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }

    // Foco inicial y al regresar a esta pantalla.
    LaunchedEffect(ui.focusTick, hasFavs, others.isNotEmpty()) {
        val last = ui.lastFocused
        val target = when {
            last != null && favs.any { it.pkg == last } -> last
            last != null && gridVisible && others.any { it.pkg == last } -> last
            hasFavs -> favs.first().pkg
            else -> others.firstOrNull()?.pkg
        } ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { fr(target).requestFocus() }
    }
    // Al mover una app entre filas, mantener el foco sobre ella.
    LaunchedEffect(ui.moving, favs, others) {
        val m = ui.moving ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { fr(m).requestFocus() }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(expandedVisual) { if (!expandedVisual) scroll.animateScrollTo(0) }

    val ctx = LocalContext.current
    fun openMenu(pkg: String) {
        ui.lastFocused = pkg
        FocusMemory.map.remove("menu:$pkg")
        nav.push(Screen.AppMenu(pkg))
    }

    fun moveKey(pkg: String, inFavorites: Boolean, delta: Int): Boolean {
        if (inFavorites) {
            if (delta == 1 || delta == -1) app.store.update { it.moveInFavorites(pkg, delta) }
            return true
        }
        val i = others.indexOfFirst { it.pkg == pkg }
        if (i < 0) return true
        val j = (i + delta).coerceIn(0, others.lastIndex)
        if (j != i) app.store.update { it.moveNextTo(apps, pkg, others[j].pkg, after = delta > 0) }
        return true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tileH = TILE_W * 9f / 16f
        val panelH = PANEL_PAD * 2 + tileH + LABEL_H
        val headerH = 64.dp
        val collapsedGap = (maxHeight - 26.dp - headerH - panelH - 22.dp).coerceAtLeast(16.dp)
        val gap by animateDpAsState(
            targetValue = if (!hasFavs || expandedVisual) 18.dp else collapsedGap,
            animationSpec = tween(460, easing = FastOutSlowInEasing), label = "gap",
        )
        val panelAlpha by animateFloatAsState(if (expandedVisual) 1f else 0f, tween(420), label = "panel")
        val gridAlpha by animateFloatAsState(if (gridVisible) 1f else 0f, tween(380), label = "grid")
        val rowWidth = TILE_W * COLS + TILE_GAP * (COLS - 1) + PANEL_PAD * 2

        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = !expandedVisual,
                enter = fadeIn(tween(300)) + expandVertically(tween(420)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(420)),
            ) {
                HeaderBar(prefs, Modifier.width(rowWidth).height(headerH), onCustom = { nav.push(Screen.Settings) })
            }
            Spacer(Modifier.height(gap))

            if (hasFavs) {
                Box(
                    Modifier.width(rowWidth)
                        .glass(RoundedCornerShape(32.dp), LocalStyle.current.panel, panelAlpha)
                        .padding(start = PANEL_PAD, end = PANEL_PAD, top = PANEL_PAD)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
                        modifier = Modifier.onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown || ui.moving != null) return@onPreviewKeyEvent false
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> if (!ui.expanded && others.isNotEmpty()) { ui.expanded = true; true } else false
                                KeyEvent.KEYCODE_DPAD_UP -> if (ui.expanded) { ui.collapse(); true } else false
                                else -> false
                            }
                        },
                    ) {
                        favs.forEach { e ->
                            key(e.pkg) {
                                AppTile(
                                    app = app, entry = e, prefs = prefs, width = TILE_W,
                                    canFocus = true, moving = ui.moving == e.pkg,
                                    focusRequester = fr(e.pkg),
                                    onFocused = { ui.lastFocused = e.pkg },
                                    onClick = { SystemActions.launchApp(ctx, e.pkg) },
                                    onLongClick = { openMenu(e.pkg) },
                                    onMoveKey = { d -> moveKey(e.pkg, true, d) },
                                    onMoveDone = { ui.moving = null },
                                )
                            }
                        }
                    }
                }
            }

            if (others.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.width(rowWidth).padding(horizontal = PANEL_PAD).graphicsLayer { alpha = gridAlpha },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    others.chunked(COLS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                            row.forEach { e ->
                                key(e.pkg) {
                                    AppTile(
                                        app = app, entry = e, prefs = prefs, width = TILE_W,
                                        canFocus = gridVisible, moving = ui.moving == e.pkg,
                                        focusRequester = fr(e.pkg),
                                        onFocused = { ui.lastFocused = e.pkg },
                                        onClick = { SystemActions.launchApp(ctx, e.pkg) },
                                        onLongClick = { openMenu(e.pkg) },
                                        onMoveKey = { d -> moveKey(e.pkg, false, d) },
                                        onMoveDone = { ui.moving = null },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }

        if (apps.isEmpty()) {
            BasicText(
                "Cargando aplicaciones…",
                style = ts(18.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f)),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
fun AppTile(
    app: SimpleApp,
    entry: AppEntry,
    prefs: Prefs,
    width: Dp,
    canFocus: Boolean,
    moving: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveKey: (Int) -> Boolean,
    onMoveDone: () -> Unit,
) {
    val mode = prefs.iconMode(entry.pkg)
    var focused by remember { mutableStateOf(false) }
    val visual by produceState(app.repo.cached(entry.pkg, mode), entry.pkg, mode) {
        value = app.repo.loadVisual(entry.pkg, mode)
    }
    val scale by animateFloatAsState(
        if (focused) (if (moving) 1.14f else 1.1f) else 1f,
        spring(dampingRatio = 0.72f, stiffness = 520f), label = "tileScale",
    )
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "label")
    val height = width * 9f / 16f
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .zIndex(if (focused) 1f else 0f)
            .width(width)
            .focusRequester(focusRequester)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { ev ->
                if (!moving) return@onPreviewKeyEvent false
                val code = ev.nativeKeyEvent.keyCode
                if (isSelectKey(code)) {
                    if (ev.type == KeyEventType.KeyUp) onMoveDone()
                    return@onPreviewKeyEvent true
                }
                if (ev.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
                }
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> onMoveKey(-1)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> onMoveKey(1)
                    KeyEvent.KEYCODE_DPAD_UP -> onMoveKey(-COLS)
                    KeyEvent.KEYCODE_DPAD_DOWN -> onMoveKey(COLS)
                    else -> false
                }
            }
            .dpadActions(onClick, onLongClick)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width, height)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(Color(visual?.color ?: 0xFF2B2F3A.toInt()).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            visual?.let {
                Image(it.tile, contentDescription = entry.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (focused) Box(Modifier.fillMaxSize().border(3.dp, Color.White, shape))
            if (moving) {
                Row(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Left, Color.White, 22.dp, Modifier.padding(start = 6.dp))
                    Icon(Icons.Move, Color.White, 22.dp)
                    Icon(Icons.Right, Color.White, 22.dp, Modifier.padding(end = 6.dp))
                }
            }
        }
        BasicText(
            if (moving) "Mueve con las flechas · OK para soltar" else entry.label,
            style = ts(if (moving) 11.sp else 14.sp, FontWeight.SemiBold).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(width + 40.dp)
                .padding(top = 10.dp)
                .height(LABEL_H - 10.dp)
                .graphicsLayer { alpha = if (moving) 1f else labelAlpha },
        )
    }
}

@Composable
private fun HeaderBar(prefs: Prefs, modifier: Modifier, onCustom: () -> Unit) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val connected = rememberConnected()
    val now = rememberNow()
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        val visible = prefs.controls.filter { it == CONTROL_CUSTOM || it !in prefs.hiddenControls }
        Row(
            Modifier.glass(CircleShape, style.panel).padding(start = if (prefs.clockMode == ClockMode.HIDDEN) 6.dp else 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (prefs.clockMode != ClockMode.HIDDEN) {
                val time = SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
                val text = if (prefs.clockMode == ClockMode.TIME_DATE) "$time  •  ${prefs.dateStyle.format(now)}" else time
                BasicText(text, style = ts(18.sp, FontWeight.Bold), modifier = Modifier.padding(end = 8.dp))
            }
            visible.forEach { id ->
                key(id) {
                    when (id) {
                        CONTROL_CUSTOM -> CircleButton(Icons.Sliders, size = 42, onClick = onCustom)
                        CONTROL_SETTINGS -> CircleButton(Icons.Gear, size = 42) { SystemActions.openSettings(ctx) }
                        CONTROL_WIFI -> CircleButton(if (connected) Icons.Wifi else Icons.WifiOff, size = 42,
                            iconTint = if (connected) null else Color(0xFFFFB4A8)) { SystemActions.openWifi(ctx) }
                        CONTROL_STORE -> CircleButton(Icons.Store, size = 42) { SystemActions.openStoreHome(ctx) }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberNow(): Date {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            val ms = 60_000 - (System.currentTimeMillis() % 60_000)
            delay(ms + 30)
        }
    }
    return now
}

@Composable
fun rememberConnected(): Boolean {
    val ctx = LocalContext.current
    var connected by remember { mutableStateOf(isConnected(ctx)) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { connected = true }
            override fun onLost(network: Network) { connected = isConnected(ctx) }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
    return connected
}

private fun isConnected(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
