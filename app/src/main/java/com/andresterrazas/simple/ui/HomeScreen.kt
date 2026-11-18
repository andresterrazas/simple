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
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.CompositingStrategy
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
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

// Pantalla de inicio: encabezado, dock, cuadrícula y la sección de Continuar viendo.


const val COLS = 6

internal val PANEL_PAD = 18.dp

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
internal object NoAutoScroll : androidx.compose.foundation.gestures.BringIntoViewSpec {
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
    val weatherNow by app.weather.now.collectAsState()
    // Con el estilo "Con pronóstico" la hora se vuelve enfocable y abre el pronóstico del día.
    val clockFocusable = prefs.weatherStyle == com.andresterrazas.simple.data.WeatherStyle.DETAILED && weatherNow != null
    val headerIds = if (clockFocusable) listOf(CLOCK_ID) + visibleControls else visibleControls

    val showRecs = prefs.showRecs
    val recs by Recs.items.collectAsState()
    val recRequesters = remember { HashMap<String, FocusRequester>() }
    fun rfr(key: String) = recRequesters.getOrPut(key) { FocusRequester() }
    val memory2 = remember { arrayOfNulls<String>(1) } // última tarjeta de "Continuar viendo"
    // "Continuar viendo" aparece al posarse en una app de streaming del dock que tenga contenido a medias.
    // Rendimiento: el estado de "hover" vive fuera de la composición del inicio. Mover el foco entre apps
    // ya no recompone todo el dock y la cuadrícula; solo la sección de Continuar viendo lo lee.
    val hover = remember { HoverState() }
    val recsNow by rememberUpdatedState(recs)
    val prefsNow by rememberUpdatedState(prefs)
    val dockOpen by rememberUpdatedState(hasFavs && !expandedVisual)
    LaunchedEffect(hover) {
        snapshotFlow { hover.hover }.collectLatest { h ->
            if (h == null) hover.shown = null else { delay(200); hover.shown = h }
        }
    }
    val appRecsState = remember {
        derivedStateOf {
            val pkg = hover.shown
            val p = prefsNow
            if (!p.showRecs || pkg == null || pkg in p.recsHidden) emptyList()
            else recsNow.filter { Recs.samePackage(it.pkg, pkg) }.take(10)
        }
    }
    val recsShownState = remember { derivedStateOf { appRecsState.value.isNotEmpty() && dockOpen } }
    LaunchedEffect(ui.focusTick) { hover.hover = null; hover.shown = null; memory2[0] = null }
    fun recsTarget(): FocusRequester? {
        val list = appRecsState.value
        val k = memory2[0]?.takeIf { m -> list.any { it.key == m } } ?: list.firstOrNull()?.key
        return k?.let { rfr(it) }
    }

    fun dockTarget(): FocusRequester? {
        val p = memory[0]?.takeIf { m -> favs.any { it.pkg == m } } ?: favs.firstOrNull()?.pkg
            ?: others.firstOrNull()?.pkg
        return p?.let { fr(it) }
    }
    fun headerTarget(): FocusRequester? {
        val id = memory[1]?.takeIf { it in headerIds } ?: headerIds.firstOrNull { it != CLOCK_ID } ?: headerIds.firstOrNull()
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
                                        if (!ui.expanded) up = (if (recsShownState.value && hover.shown == e.pkg) recsTarget() else null) ?: headerTarget() ?: FocusRequester.Cancel
                                        if (ui.expanded && rows.isNotEmpty()) down = fr(rows.first()[i.coerceAtMost(rows.first().lastIndex)].pkg)
                                    },
                                    onFocused = { ui.lastFocused = e.pkg; memory[0] = e.pkg; hover.hover = e.pkg },
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
                        .graphicsLayer { alpha = gridAlpha; compositingStrategy = CompositingStrategy.ModulateAlpha },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Con el dock en vista normal solo existe la fila que asoma: no se dibujan decenas de apps ocultas.
                    (if (gridVisible) rows else rows.take(1)).forEachIndexed { r, row ->
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
            // Tres tarjetas y un asomo de la cuarta; el alto sigue al ancho para que el texto respire.
            val cardW = ((rowWidth - PANEL_PAD * 2 - 48.dp) / 3.25f).coerceIn(200.dp, 300.dp)
            val cardH = (cardW * 0.34f).coerceIn(68.dp, 92.dp)
            val panelH = 40.dp + cardH
            RecsOverlay(
                hover = hover,
                appRecs = appRecsState,
                shown = recsShownState,
                apps = apps,
                cardW = cardW,
                cardH = cardH,
                modifier = Modifier
                    .padding(top = (dockTop - panelH - 16.dp).coerceAtLeast(headerTop + headerH))
                    .padding(start = (maxWidth - rowWidth) / 2 + PANEL_PAD - CARD_BLEED)
                    .width(rowWidth - PANEL_PAD + CARD_BLEED),
                requester = { rfr(it) },
                onFocused = { memory2[0] = it },
                upTarget = { headerTarget() },
                downTarget = { hover.shown?.let { fr(it) } ?: dockTarget() },
            )
        }

        // El encabezado está fijo arriba; al expandir se desvanece y deja de recibir foco.
        HeaderBar(
            prefs = prefs,
            controls = visibleControls,
            weather = weatherNow,
            clockFocusable = clockFocusable,
            enabled = !expandedVisual,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = headerTop).width(safeW)
                .graphicsLayer {
                    alpha = headerAlpha
                    translationY = -(1f - headerAlpha) * 24.dp.toPx()
                },
            requester = { hfr(it) },
            onFocused = { memory[1] = it; hover.hover = null },
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
