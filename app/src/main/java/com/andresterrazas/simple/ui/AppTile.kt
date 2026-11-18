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

// Tarjeta de una app en el dock y la cuadrícula.

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
