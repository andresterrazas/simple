@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusProperties
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
import com.andresterrazas.simple.data.DockOpacity
import com.andresterrazas.simple.data.LabelMode
import com.andresterrazas.simple.data.Prefs
import com.andresterrazas.simple.data.SystemActions
import com.andresterrazas.simple.data.orderApps
import com.andresterrazas.simple.home.Recs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val COLS = 6
private val PANEL_PAD = 18.dp

/** Apariencia de las tarjetas de apps, derivada de la configuración del dock. */
data class TileLook(
    val width: Dp,
    val gap: Dp,
    val radius: Dp,
    val zoom: Float,
    val outline: Boolean,
    val labels: LabelMode,
) {
    val height: Dp get() = width * 9f / 16f
    val labelH: Dp get() = if (labels == LabelMode.NEVER) 8.dp else 30.dp
}

fun Prefs.tileLook(width: Dp = dockSize.tile.dp, gap: Dp = dockSize.gap.dp) = TileLook(
    width = width,
    gap = gap,
    radius = dockShape.tileRadius.dp,
    zoom = focusZoom.scale,
    outline = focusOutline,
    labels = labelMode,
)

/** Tinte del panel del dock según la opacidad elegida y el estilo claro u oscuro. */
fun dockTint(p: Prefs, style: UiStyle): Color =
    if (style.dark) Color.Black.copy(alpha = (p.dockOpacity.alpha * 1.5f).coerceAtMost(0.7f))
    else Color.White.copy(alpha = p.dockOpacity.alpha)

/** Evita que el foco desplace la pantalla mientras el dock está en su vista normal. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private object NoAutoScroll : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

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
    val rows = remember(others) { others.chunked(COLS) }

    val hasFavs = favs.isNotEmpty()
    val expandedVisual = ui.expanded && hasFavs
    val gridVisible = ui.expanded || !hasFavs

    val requesters = remember { HashMap<String, FocusRequester>() }
    fun fr(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }
    val headerRequesters = remember { HashMap<String, FocusRequester>() }
    fun hfr(id: String) = headerRequesters.getOrPut(id) { FocusRequester() }
    // Últimos elementos con foco en cada zona, para que subir y bajar siempre regrese al mismo lugar.
    val memory = remember { arrayOfNulls<String>(2) } // [0] = última app del dock, [1] = último botón del encabezado
    var pendingFocus by remember { mutableStateOf<String?>(null) }

    val visibleControls = prefs.controls.filter { it == CONTROL_CUSTOM || it !in prefs.hiddenControls }

    val showRecs = prefs.showRecs
    val recs by Recs.items.collectAsState()
    val recRequesters = remember { HashMap<String, FocusRequester>() }
    fun rfr(key: String) = recRequesters.getOrPut(key) { FocusRequester() }
    val memory2 = remember { arrayOfNulls<String>(1) } // última tarjeta de "Continuar viendo"
    // "Continuar viendo" aparece al posarse en una app de streaming del dock que tenga contenido a medias.
    var hoverPkg by remember { mutableStateOf<String?>(null) }
    val shownPkg by produceState<String?>(null, hoverPkg) {
        if (hoverPkg == null) { value = null; return@produceState }
        delay(260)
        value = hoverPkg
    }
    val appRecs = remember(recs, shownPkg, prefs.recsHidden, showRecs) {
        val pkg = shownPkg
        if (!showRecs || pkg == null || pkg in prefs.recsHidden) emptyList()
        else recs.filter { it.pkg == pkg }.take(10)
    }
    val recsShown = appRecs.isNotEmpty() && hasFavs && !expandedVisual
    fun recsTarget(): FocusRequester? {
        val k = memory2[0]?.takeIf { m -> appRecs.any { it.key == m } } ?: appRecs.firstOrNull()?.key
        return k?.let { rfr(it) }
    }

    fun dockTarget(): FocusRequester? {
        val p = memory[0]?.takeIf { m -> favs.any { it.pkg == m } } ?: favs.firstOrNull()?.pkg
            ?: others.firstOrNull()?.pkg
        return p?.let { fr(it) }
    }
    fun headerTarget(): FocusRequester? {
        val id = memory[1]?.takeIf { it in visibleControls } ?: visibleControls.firstOrNull()
        return id?.let { hfr(it) }
    }

    // Foco inicial y al regresar a esta pantalla.
    LaunchedEffect(ui.focusTick, hasFavs, others.isNotEmpty()) {
        val last = ui.lastFocused
        val target = when {
            last != null && favs.any { it.pkg == last } -> last
            last != null && gridVisible && others.any { it.pkg == last } -> last
            hasFavs -> memory[0]?.takeIf { m -> favs.any { it.pkg == m } } ?: favs.first().pkg
            else -> others.firstOrNull()?.pkg
        } ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { fr(target).requestFocus() }
    }
    // Al expandir, el foco pasa directo a la cuadrícula.
    LaunchedEffect(pendingFocus) {
        val p = pendingFocus ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { fr(p).requestFocus() }
        pendingFocus = null
    }
    // Al mover una app entre filas, mantener el foco sobre ella.
    LaunchedEffect(ui.moving, favs, others) {
        val m = ui.moving ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { fr(m).requestFocus() }
    }

    val scroll = rememberScrollState()
    // Con el dock en su vista normal, la pantalla nunca debe desplazarse: si algo la movió
    // (por ejemplo, el foco al regresar de la cuadrícula), se devuelve al instante a su posición.
    LaunchedEffect(gridVisible) {
        if (!gridVisible) snapshotFlow { scroll.value }.collect { if (it != 0) scroll.scrollTo(0) }
    }
    val defaultBringIntoView = LocalBringIntoViewSpec.current

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

    fun expandFrom(col: Int) {
        if (others.isEmpty()) return
        ui.expanded = true
        pendingFocus = rows.first()[col.coerceAtMost(rows.first().lastIndex)].pkg
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // ── Diseño adaptable ───────────────────────────────────────────────────────────────
        // Margen seguro de TV (5 % por lado): evita que el sobrebarrido del televisor recorte el dock.
        val safeH = maxWidth * 0.05f
        val safeV = maxHeight * 0.05f
        val safeW = maxWidth - safeH * 2
        // El dock ocupa una fracción del ancho seguro y las apps se calculan para llenarlo (como en Apple TV).
        val rowWidth = safeW * prefs.dockSize.widthFraction
        val gap = rowWidth * 0.022f
        val tileW = (rowWidth - PANEL_PAD * 2 - gap * (COLS - 1)) / COLS
        val look = prefs.tileLook(tileW, gap)
        val dockH = PANEL_PAD + look.height + look.labelH + 8.dp
        val headerTop = safeV
        val headerH = 56.dp
        val gridGap = 22.dp
        val showPeek = prefs.peekRow && others.isNotEmpty() && hasFavs
        // Asoma la parte superior de la siguiente fila bajo el dock, como pista de que hay más apps.
        val peek = look.height * 0.34f
        val collapsedTop = if (showPeek) maxHeight - peek - gridGap - dockH else maxHeight - safeV - dockH
        val topTarget = when {
            !hasFavs -> headerTop + headerH + 24.dp
            expandedVisual -> safeV
            else -> collapsedTop.coerceAtLeast(headerTop + headerH + 16.dp)
        }
        val dockTop by animateDpAsState(topTarget, tween(440, easing = FastOutSlowInEasing), label = "dockTop")
        val headerAlpha by animateFloatAsState(if (expandedVisual) 0f else 1f, tween(260), label = "header")
        val gridAlpha by animateFloatAsState(if (gridVisible) 1f else if (showPeek) 0.5f else 0f, tween(360), label = "grid")


        CompositionLocalProvider(LocalBringIntoViewSpec provides if (gridVisible) defaultBringIntoView else NoAutoScroll) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll, enabled = gridVisible),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(dockTop))

            if (hasFavs) {
                Box(
                    Modifier.width(rowWidth)
                        .glass(RoundedCornerShape(prefs.dockShape.panelRadius.dp), dockTint(prefs, LocalStyle.current), if (prefs.dockOpacity == DockOpacity.NONE) 0f else 1f)
                        .padding(start = PANEL_PAD, end = PANEL_PAD, top = PANEL_PAD, bottom = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(look.gap),
                        modifier = Modifier.onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown || ui.moving != null) return@onPreviewKeyEvent false
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> if (!ui.expanded && others.isNotEmpty()) {
                                    val col = favs.indexOfFirst { it.pkg == memory[0] }.coerceAtLeast(0)
                                    expandFrom(col)
                                    true
                                } else false
                                KeyEvent.KEYCODE_DPAD_UP -> if (ui.expanded) { ui.collapse(); true } else false
                                else -> false
                            }
                        },
                    ) {
                        favs.forEachIndexed { i, e ->
                            key(e.pkg) {
                                AppTile(
                                    app = app, entry = e, prefs = prefs, look = look,
                                    canFocus = true, moving = ui.moving == e.pkg,
                                    focusRequester = fr(e.pkg),
                                    routes = {
                                        if (i == 0) left = FocusRequester.Cancel
                                        if (i == favs.lastIndex) right = FocusRequester.Cancel
                                        if (!ui.expanded) up = (if (recsShown && shownPkg == e.pkg) recsTarget() else null) ?: headerTarget() ?: FocusRequester.Cancel
                                        if (ui.expanded && rows.isNotEmpty()) down = fr(rows.first()[i.coerceAtMost(rows.first().lastIndex)].pkg)
                                    },
                                    onFocused = { ui.lastFocused = e.pkg; memory[0] = e.pkg; hoverPkg = e.pkg },
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
                Column(
                    Modifier.width(rowWidth).padding(start = PANEL_PAD, end = PANEL_PAD, top = gridGap, bottom = 48.dp)
                        .graphicsLayer { alpha = gridAlpha },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rows.forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(look.gap)) {
                            row.forEachIndexed { c, e ->
                                key(e.pkg) {
                                    AppTile(
                                        app = app, entry = e, prefs = prefs, look = look,
                                        canFocus = gridVisible, moving = ui.moving == e.pkg,
                                        focusRequester = fr(e.pkg),
                                        routes = {
                                            if (c == 0) left = FocusRequester.Cancel
                                            if (c == row.lastIndex) right = FocusRequester.Cancel
                                            if (r == rows.lastIndex) down = FocusRequester.Cancel
                                            if (r == 0) up = if (hasFavs) fr(favs[c.coerceAtMost(favs.lastIndex)].pkg)
                                            else headerTarget() ?: FocusRequester.Cancel
                                        },
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
        }

        }

        // Continuar viendo de la app enfocada: tarjetas horizontales sobre el dock, alineadas a su borde izquierdo.
        if (hasFavs) {
            val cardH = (look.height * 0.92f).coerceAtLeast(64.dp)
            val cardW = (rowWidth - PANEL_PAD * 2 - 16.dp * 2) / 3
            val panelH = 32.dp + cardH
            val label = apps.firstOrNull { it.pkg == shownPkg }?.label.orEmpty()
            AnimatedVisibility(
                visible = recsShown,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 6 },
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .padding(top = (dockTop - panelH - 18.dp).coerceAtLeast(headerTop + headerH))
                    .padding(start = (maxWidth - rowWidth) / 2 + PANEL_PAD)
                    .width(rowWidth - PANEL_PAD),
            ) {
                ContinueRow(
                    title = if (label.isBlank()) "Continuar viendo" else "Continuar viendo en $label",
                    recs = appRecs,
                    cardW = cardW,
                    cardH = cardH,
                    enabled = recsShown,
                    requester = { rfr(it) },
                    onFocused = { memory2[0] = it },
                    upTarget = { headerTarget() },
                    downTarget = { shownPkg?.let { fr(it) } ?: dockTarget() },
                )
            }
        }

        // El encabezado está fijo arriba; al expandir se desvanece y deja de recibir foco.
        HeaderBar(
            prefs = prefs,
            controls = visibleControls,
            enabled = !expandedVisual,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = headerTop).width(safeW).height(headerH)
                .graphicsLayer {
                    alpha = headerAlpha
                    translationY = -(1f - headerAlpha) * 24.dp.toPx()
                },
            requester = { hfr(it) },
            onFocused = { memory[1] = it; hoverPkg = null },
            downTarget = { dockTarget() },
            onCustom = { nav.push(Screen.Settings) },
        )

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
    look: TileLook,
    canFocus: Boolean,
    moving: Boolean,
    focusRequester: FocusRequester,
    routes: FocusProperties.() -> Unit = {},
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
        if (focused) (if (moving) look.zoom + 0.04f else look.zoom) else 1f,
        spring(dampingRatio = 0.72f, stiffness = 520f), label = "tileScale",
    )
    val labelTarget = when (look.labels) {
        LabelMode.FOCUS -> if (focused) 1f else 0f
        LabelMode.ALWAYS -> if (focused) 1f else 0.7f
        LabelMode.NEVER -> 0f
    }
    val labelAlpha by animateFloatAsState(labelTarget, tween(180), label = "label")
    val width = look.width
    val height = look.height
    val shape = RoundedCornerShape(look.radius)

    Column(
        modifier = Modifier
            .zIndex(if (focused) 1f else 0f)
            .width(width)
            .focusRequester(focusRequester)
            .focusProperties {
                this.canFocus = canFocus
                routes()
            }
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
        // Contorno de enfoque dentro de la tarjeta (estilo original). Su color reacciona a la app:
        // blanco normalmente y gris en tarjetas claras (Netflix, YouTube) para que siempre se distinga.
        val ringColor = if (visual?.light == true) Color(0xFF8B94A3) else Color.White
        Box(
            Modifier
                .size(width, height)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(Color(visual?.color ?: 0xFF2B2F3A.toInt()).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            visual?.let {
                Image(
                    it.tile, contentDescription = entry.label, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium,
                )
            }
            if (focused && look.outline) Box(Modifier.fillMaxSize().border(3.dp, ringColor, shape))
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
                .height((look.labelH - 10.dp).coerceAtLeast(0.dp))
                .graphicsLayer { alpha = if (moving) 1f else labelAlpha },
        )
    }
}

@Composable
private fun HeaderBar(
    prefs: Prefs,
    controls: List<String>,
    enabled: Boolean,
    modifier: Modifier,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    downTarget: () -> FocusRequester?,
    onCustom: () -> Unit,
) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val connected = rememberConnected()
    val now = rememberNow()
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
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
            controls.forEachIndexed { i, id ->
                key(id) {
                    val m = Modifier
                        .focusRequester(requester(id))
                        .focusProperties {
                            canFocus = enabled
                            up = FocusRequester.Cancel
                            if (i == 0) left = FocusRequester.Cancel
                            if (i == controls.lastIndex) right = FocusRequester.Cancel
                            down = downTarget() ?: FocusRequester.Cancel
                        }
                        .onFocusChanged { if (it.isFocused) onFocused(id) }
                    when (id) {
                        CONTROL_CUSTOM -> CircleButton(Icons.Sliders, m, size = 42, onClick = onCustom)
                        CONTROL_SETTINGS -> CircleButton(Icons.Gear, m, size = 42) { SystemActions.openSettings(ctx) }
                        CONTROL_WIFI -> CircleButton(if (connected) Icons.Wifi else Icons.WifiOff, m, size = 42,
                            iconTint = if (connected) null else Color(0xFFFFB4A8)) { SystemActions.openWifi(ctx) }
                        CONTROL_STORE -> CircleButton(Icons.Store, m, size = 42) { SystemActions.openStoreHome(ctx) }
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

@Composable
private fun ContinueRow(
    title: String,
    recs: List<com.andresterrazas.simple.home.Rec>,
    cardW: Dp,
    cardH: Dp,
    enabled: Boolean,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    upTarget: () -> FocusRequester?,
    downTarget: () -> FocusRequester?,
) {
    val ctx = LocalContext.current
    val apps = (ctx.applicationContext as SimpleApp).repo.apps.collectAsState().value
    Column {
        BasicText(title, style = ts(15.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)), modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(end = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            recs.forEachIndexed { i, r ->
                key(r.key) {
                    ContinueCard(
                        rec = r,
                        appLabel = apps.firstOrNull { it.pkg == r.pkg }?.label.orEmpty(),
                        width = cardW,
                        height = cardH,
                        modifier = Modifier
                            .focusRequester(requester(r.key))
                            .focusProperties {
                                canFocus = enabled
                                if (i == 0) left = FocusRequester.Cancel
                                if (i == recs.lastIndex) right = FocusRequester.Cancel
                                up = upTarget() ?: FocusRequester.Cancel
                                down = downTarget() ?: FocusRequester.Cancel
                            }
                            .onFocusChanged { if (it.isFocused) onFocused(r.key) },
                    ) { com.andresterrazas.simple.home.Recs.open(ctx, r) }
                }
            }
        }
    }
}

/** Tarjeta minimalista: miniatura a la izquierda; app, título, subtítulo y progreso a la derecha. */
@Composable
private fun ContinueCard(rec: com.andresterrazas.simple.home.Rec, appLabel: String, width: Dp, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = 0.8f, stiffness = 520f), label = "cardScale")
    val image by produceState(rec.image ?: com.andresterrazas.simple.home.RecImages.cached(rec.imageUri), rec.key) {
        if (value == null && rec.imageUri != null) value = com.andresterrazas.simple.home.RecImages.load(ctx, rec.imageUri)
    }
    val shape = RoundedCornerShape(18.dp)
    val pad = 10.dp
    val thumbH = height - pad * 2
    val thumbW = thumbH * 16f / 9f
    Row(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width, height)
            .glass(shape, if (focused) Color.White.copy(alpha = 0.28f) else style.panel)
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick)
            .focusable()
            .padding(pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(thumbW, thumbH).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.3f))) {
            image?.let { Image(it, rec.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium) }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            BasicText(appLabel.uppercase(), maxLines = 1, style = ts(10.sp, FontWeight.Bold, Color.White.copy(alpha = 0.7f)))
            BasicText(rec.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(15.sp, FontWeight.Bold), modifier = Modifier.padding(top = 2.dp))
            if (rec.subtitle.isNotBlank()) {
                BasicText(rec.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(12.sp, FontWeight.Medium, Color.White.copy(alpha = 0.75f)), modifier = Modifier.padding(top = 1.dp))
            }
            rec.progress?.let { p ->
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(p).height(3.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}
