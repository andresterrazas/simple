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
import androidx.compose.ui.draw.drawWithContent
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

// Sección "Continuar viendo" que aparece sobre el dock.

@Composable
internal fun ContinueRow(
    title: String,
    recs: List<com.andresterrazas.simple.home.Rec>,
    cardW: Dp,
    cardH: Dp,
    style: com.andresterrazas.simple.data.RecStyle = com.andresterrazas.simple.data.RecStyle.WIDE,
    enabled: Boolean,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    upTarget: () -> FocusRequester?,
    downTarget: () -> FocusRequester?,
) {
    val ctx = LocalContext.current
    val apps = (ctx.applicationContext as SimpleApp).repo.apps.collectAsState().value
    Column {
        BasicText(title, style = ts(15.sp, FontWeight.Bold).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 8f)), modifier = Modifier.padding(start = CARD_BLEED + 4.dp, bottom = 4.dp))
        Row(
            // Margen interno: la tarjeta enfocada crece sin que el borde del desplazamiento la recorte.
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CARD_BLEED, vertical = 8.dp).padding(end = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            recs.forEachIndexed { i, r ->
                key(r.key) {
                    RecCard(
                        style = style,
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

internal val CARD_BLEED = 12.dp

class HoverState {
    var hover by mutableStateOf<String?>(null)
    var shown by mutableStateOf<String?>(null)
}

/**
 * Sección de Continuar viendo. Al cambiar de app el contenido hace un fundido cruzado con un leve
 * desplazamiento; al aparecer sube suavemente y al ocultarse baja y se desvanece.
 */
@Composable
internal fun RecsOverlay(
    hover: HoverState,
    appRecs: androidx.compose.runtime.State<List<com.andresterrazas.simple.home.Rec>>,
    shown: androidx.compose.runtime.State<Boolean>,
    apps: List<AppEntry>,
    cardW: Dp,
    cardH: Dp,
    style: com.andresterrazas.simple.data.RecStyle,
    modifier: Modifier,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    upTarget: () -> FocusRequester?,
    downTarget: () -> FocusRequester?,
) {
    val target = if (shown.value) hover.shown else null
    val list = appRecs.value
    androidx.compose.animation.AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentKey = { it },
        transitionSpec = {
            val enter = fadeIn(tween(240, delayMillis = 60, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 }
            val exit = fadeOut(tween(140)) + androidx.compose.animation.slideOutVertically(tween(200)) { it / 14 }
            (enter togetherWith exit).using(androidx.compose.animation.SizeTransform(clip = false))
        },
        label = "recs",
    ) { pkg ->
        if (pkg == null) {
            Box(Modifier.height(1.dp))
        } else {
            val label = apps.firstOrNull { it.pkg == pkg }?.label.orEmpty()
            ContinueRow(
                title = if (label.isBlank()) "Continuar viendo" else "Continuar viendo en $label",
                recs = list.filter { com.andresterrazas.simple.home.Recs.samePackage(it.pkg, pkg) },
                cardW = cardW,
                cardH = cardH,
                style = style,
                enabled = true,
                requester = requester,
                onFocused = onFocused,
                upTarget = upTarget,
                downTarget = downTarget,
            )
        }
    }
}

@Composable
internal fun RecCard(
    style: com.andresterrazas.simple.data.RecStyle,
    rec: com.andresterrazas.simple.home.Rec,
    appLabel: String,
    width: Dp,
    height: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) = when (style) {
    com.andresterrazas.simple.data.RecStyle.CLASSIC -> ClassicCard(rec, width, height, modifier, onClick)
    com.andresterrazas.simple.data.RecStyle.WIDE -> ContinueCard(rec, appLabel, width, height, modifier, onClick)
    com.andresterrazas.simple.data.RecStyle.POSTER -> PosterCard(rec, width, height, modifier, onClick)
}

/** Línea secundaria corta: "T5 E4 · faltan 23 min", o el subtítulo si la app no da episodio. */
internal fun recMeta(rec: com.andresterrazas.simple.home.Rec): String {
    val mins = (rec.remainingMs / 60_000L).toInt()
    val left = when {
        mins <= 0 -> ""
        mins < 60 -> "faltan $mins min"
        else -> "faltan ${mins / 60} h ${mins % 60} min"
    }
    return when {
        rec.episode.isNotBlank() -> listOf(rec.episode, left).filter { it.isNotBlank() }.joinToString(" · ")
        left.isNotBlank() -> left.replaceFirstChar { it.uppercase() }
        else -> rec.subtitle
    }
}

/**
 * Tarjeta de Continuar viendo: la imagen ocupa el lado derecho y se funde con el vidrio, el texto
 * usa todo el ancho de la izquierda y la barra de progreso corre por la base de la tarjeta, así que
 * siempre se ve aunque la serie traiga más información que una película.
 */
@Composable
internal fun ContinueCard(rec: com.andresterrazas.simple.home.Rec, appLabel: String, width: Dp, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.82f, stiffness = 600f), label = "cardScale")
    val base = Color.Black.copy(alpha = if (style.dark) 0.42f else 0.26f)
    val bg by androidx.compose.animation.animateColorAsState(if (focused) style.focusedFill else base, tween(160), label = "cardBg")
    val fg = if (focused) style.focusedText else Color.White
    val image by produceState(rec.image ?: com.andresterrazas.simple.home.RecImages.cached(rec.imageUri), rec.key) {
        if (value == null && rec.imageUri != null) value = com.andresterrazas.simple.home.RecImages.load(ctx, rec.imageUri)
    }
    val shape = RoundedCornerShape(18.dp)
    val imgW = minOf(height * 16f / 9f, width * 0.58f)
    val meta = remember(rec) { recMeta(rec) }
    val hasProgress = rec.progress != null
    // Si sobra alto y no hay línea secundaria, el título puede usar dos renglones.
    val titleLines = if (meta.isBlank() && height >= 76.dp) 2 else 1
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width, height)
            .glass(shape, bg)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.14f), shape)
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick)
            .focusable(),
    ) {
        // Imagen a la derecha que se desvanece hacia el texto (máscara de alfa, sin recortes duros).
        Box(
            Modifier.align(Alignment.CenterEnd).size(imgW, height)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Transparent, 0.6f to Color.Black),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
        ) {
            image?.let { Image(it, rec.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium) }
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(if (image == null) 0.92f else 0.74f)
                .padding(start = 14.dp, end = 4.dp, bottom = if (hasProgress) 8.dp else 0.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                rec.title, maxLines = titleLines, overflow = TextOverflow.Ellipsis,
                style = ts(15.sp, FontWeight.Bold, fg).copy(lineHeight = 17.sp),
            )
            if (meta.isNotBlank()) {
                BasicText(meta, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(11.5.sp, FontWeight.Medium, fg.copy(alpha = 0.72f)), modifier = Modifier.padding(top = 2.dp))
            }
        }
        rec.progress?.let { p ->
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 9.dp)
                    .fillMaxWidth().height(3.dp).clip(CircleShape).background(fg.copy(alpha = 0.25f)),
            ) {
                Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.03f)).height(3.dp).clip(CircleShape).background(fg))
            }
        }
    }
}

/** Barra de progreso fina compartida por los tres estilos. */
@Composable
private fun RecProgress(p: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(color.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0.03f, 1f)).height(3.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun rememberRecImage(rec: com.andresterrazas.simple.home.Rec): androidx.compose.runtime.State<androidx.compose.ui.graphics.ImageBitmap?> {
    val ctx = LocalContext.current
    return produceState(rec.image ?: com.andresterrazas.simple.home.RecImages.cached(rec.imageUri), rec.key) {
        if (value == null && rec.imageUri != null) value = com.andresterrazas.simple.home.RecImages.load(ctx, rec.imageUri)
    }
}

/**
 * Estilo clásico (el de la v1.0): miniatura a la izquierda y texto a la derecha. Optimizado: sin la
 * etiqueta de la app (ya está en el título de la fila), línea secundaria corta y medidas que garantizan
 * que título, episodio y barra de progreso siempre quepan en el alto de la tarjeta.
 */
@Composable
internal fun ClassicCard(rec: com.andresterrazas.simple.home.Rec, width: Dp, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.82f, stiffness = 600f), label = "cardScale")
    val bg by androidx.compose.animation.animateColorAsState(
        if (focused) style.focusedFill else Color.Black.copy(alpha = if (style.dark) 0.42f else 0.26f), tween(160), label = "cardBg",
    )
    val fg = if (focused) style.focusedText else Color.White
    val image by rememberRecImage(rec)
    val meta = remember(rec) { recMeta(rec) }
    val shape = RoundedCornerShape(18.dp)
    val pad = 8.dp
    val thumbH = height - pad * 2
    val thumbW = minOf(thumbH * 16f / 9f, width * 0.44f)
    Row(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width, height)
            .glass(shape, bg)
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.14f), shape)
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick)
            .focusable()
            .padding(pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(thumbW, thumbH).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.35f))) {
            image?.let { Image(it, rec.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium) }
            if (image == null) {
                BasicText(rec.title.take(1).uppercase(), style = ts(24.sp, FontWeight.Bold, Color.White.copy(alpha = 0.6f)), modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(start = 10.dp, end = 4.dp).weight(1f), verticalArrangement = Arrangement.Center) {
            BasicText(
                rec.title, maxLines = if (meta.isBlank() && rec.progress == null) 2 else 1, overflow = TextOverflow.Ellipsis,
                style = ts(14.sp, FontWeight.Bold, fg).copy(lineHeight = 17.sp),
            )
            if (meta.isNotBlank()) {
                BasicText(meta, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(11.sp, FontWeight.Medium, fg.copy(alpha = 0.72f)), modifier = Modifier.padding(top = 1.dp))
            }
            rec.progress?.let { RecProgress(it, fg, Modifier.padding(top = 6.dp)) }
        }
    }
}

/**
 * Estilo imagen: la miniatura 16:9 ocupa toda la tarjeta, con el título sobre un degradado y la barra
 * de progreso en la base. Al enfocarla crece y gana un contorno blanco, igual que las apps del dock.
 */
@Composable
internal fun PosterCard(rec: com.andresterrazas.simple.home.Rec, width: Dp, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.82f, stiffness = 600f), label = "cardScale")
    val image by rememberRecImage(rec)
    val meta = remember(rec) { recMeta(rec) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width, height)
            .glass(shape, Color.Black.copy(alpha = 0.35f))
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick)
            .focusable(),
    ) {
        image?.let { Image(it, rec.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium) }
        // Degradado inferior para que el texto se lea sobre cualquier imagen.
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.78f),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
            BasicText(
                rec.title, maxLines = if (image == null) 2 else 1, overflow = TextOverflow.Ellipsis,
                style = ts(13.sp, FontWeight.Bold, Color.White).copy(
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 6f),
                ),
            )
            if (meta.isNotBlank()) {
                BasicText(meta, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(10.5.sp, FontWeight.Medium, Color.White.copy(alpha = 0.8f)), modifier = Modifier.padding(top = 1.dp))
            }
            rec.progress?.let { RecProgress(it, Color.White, Modifier.padding(top = 5.dp)) }
        }
        Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 1.dp, Color.White.copy(alpha = if (focused) 1f else 0.16f), shape))
    }
}
