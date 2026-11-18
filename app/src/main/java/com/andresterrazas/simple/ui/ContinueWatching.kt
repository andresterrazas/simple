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

// Sección "Continuar viendo" que aparece sobre el dock.

@Composable
internal fun ContinueRow(
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
        BasicText(title, style = ts(15.sp, FontWeight.Bold).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 8f)), modifier = Modifier.padding(start = CARD_BLEED + 4.dp, bottom = 4.dp))
        Row(
            // Margen interno: la tarjeta enfocada crece sin que el borde del desplazamiento la recorte.
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CARD_BLEED, vertical = 8.dp).padding(end = 60.dp),
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
                enabled = true,
                requester = requester,
                onFocused = onFocused,
                upTarget = upTarget,
                downTarget = downTarget,
            )
        }
    }
}

/**
 * Tarjeta minimalista: miniatura a la izquierda; app, título, episodio y progreso a la derecha.
 * Vidrio más oscuro que el resto para que el texto se lea sobre fondos claros; al enfocarla se
 * vuelve blanca con texto oscuro, igual que las filas de los menús.
 */
@Composable
internal fun ContinueCard(rec: com.andresterrazas.simple.home.Rec, appLabel: String, width: Dp, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.82f, stiffness = 600f), label = "cardScale")
    val bg by androidx.compose.animation.animateColorAsState(
        if (focused) style.focusedFill else Color.Black.copy(alpha = if (style.dark) 0.42f else 0.26f), tween(160), label = "cardBg",
    )
    val fg = if (focused) style.focusedText else Color.White
    val image by produceState(rec.image ?: com.andresterrazas.simple.home.RecImages.cached(rec.imageUri), rec.key) {
        if (value == null && rec.imageUri != null) value = com.andresterrazas.simple.home.RecImages.load(ctx, rec.imageUri)
    }
    val shape = RoundedCornerShape(18.dp)
    val pad = 8.dp
    val thumbH = height - pad * 2
    val thumbW = thumbH * 16f / 9f
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
        Box(Modifier.size(thumbW, thumbH).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f))) {
            image?.let { Image(it, rec.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium) }
            if (image == null) {
                BasicText(rec.title.take(1).uppercase(), style = ts(28.sp, FontWeight.Bold, Color.White.copy(alpha = 0.6f)), modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 6.dp).weight(1f), verticalArrangement = Arrangement.Center) {
            BasicText(appLabel.uppercase(), maxLines = 1, style = ts(10.sp, FontWeight.Bold, fg.copy(alpha = 0.6f)).copy(letterSpacing = 1.1.sp))
            BasicText(rec.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(16.sp, FontWeight.Bold, fg), modifier = Modifier.padding(top = 2.dp))
            if (rec.subtitle.isNotBlank()) {
                BasicText(rec.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(12.sp, FontWeight.Medium, fg.copy(alpha = 0.72f)), modifier = Modifier.padding(top = 1.dp))
            }
            rec.progress?.let { p ->
                Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(fg.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.03f)).height(3.dp).clip(CircleShape).background(fg))
                }
            }
        }
    }
}
