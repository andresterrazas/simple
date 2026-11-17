@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.andresterrazas.simple.ui

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Evita que el "soltar OK" de una pulsación larga active el elemento de la pantalla que se acaba de abrir. */
object KeyGate {
    @Volatile var swallowSelectUp = false
}

fun isSelectKey(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
    code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_BUTTON_SELECT

/** OK = clic al soltar; mantener OK o el botón ☰ = pulsación larga. */
fun Modifier.dpadActions(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val longFired = remember { booleanArrayOf(false) }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    this.onPreviewKeyEvent { ev ->
        val native = ev.nativeKeyEvent
        val code = native.keyCode
        if (code == KeyEvent.KEYCODE_MENU) {
            val l = long ?: return@onPreviewKeyEvent false
            if (ev.type == KeyEventType.KeyUp) l()
            return@onPreviewKeyEvent true
        }
        if (!isSelectKey(code)) return@onPreviewKeyEvent false
        when (ev.type) {
            KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) {
                    longFired[0] = false
                    KeyGate.swallowSelectUp = false
                } else if (!longFired[0]) {
                    val l = long
                    if (l != null) {
                        longFired[0] = true
                        KeyGate.swallowSelectUp = true
                        l()
                    }
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (KeyGate.swallowSelectUp) {
                    KeyGate.swallowSelectUp = false
                    longFired[0] = false
                    return@onPreviewKeyEvent true
                }
                if (!longFired[0]) click()
                longFired[0] = false
                true
            }
            else -> false
        }
    }
}

/**
 * Panel de "vidrio": dibuja debajo el recorte correspondiente del fondo muy desenfocado y encima un tinte.
 * Como el fondo es estático, el efecto es idéntico a un desenfoque en vivo pero sin costo al mover el foco.
 */
fun Modifier.glass(shape: Shape, tint: Color, alpha: Float = 1f): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val root = LocalRootSize.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (alpha <= 0.001f) return@drawBehind
            if (backdrop != null && root.width > 0 && root.height > 0) {
                val sx = backdrop.width / root.width.toFloat()
                val sy = backdrop.height / root.height.toFloat()
                val srcX = (pos.x * sx).roundToInt().coerceIn(0, backdrop.width - 1)
                val srcY = (pos.y * sy).roundToInt().coerceIn(0, backdrop.height - 1)
                val srcW = (size.width * sx).roundToInt().coerceIn(1, backdrop.width - srcX)
                val srcH = (size.height * sy).roundToInt().coerceIn(1, backdrop.height - srcY)
                drawImage(
                    image = backdrop,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(srcW, srcH),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    alpha = alpha,
                    filterQuality = FilterQuality.Low,
                )
            }
            drawRect(tint.copy(alpha = tint.alpha * alpha))
        }
}

/** Recuerda qué elemento tenía el foco en cada pantalla para devolverlo al regresar. */
object FocusMemory {
    val map = HashMap<String, String>()
}

@Composable
fun Modifier.restorableFocus(scope: String, id: String, default: Boolean = false): Modifier {
    val fr = remember { FocusRequester() }
    val shouldFocus = remember(scope, id) {
        val saved = FocusMemory.map[scope]
        if (saved != null) saved == id else default
    }
    LaunchedEffect(scope, id) {
        if (shouldFocus) {
            withFrameNanos { }
            runCatching { fr.requestFocus() }
        }
    }
    return this
        .focusRequester(fr)
        .onFocusChanged { if (it.isFocused) FocusMemory.map[scope] = id }
}

@Composable
fun SectionLabel(text: String) {
    BasicText(
        text,
        style = ts(15.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.78f)),
        modifier = Modifier.padding(start = 10.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    subtitle: String? = null,
    danger: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "rowScale")
    val bg by animateColorAsState(if (focused) style.focusedFill else style.panel, tween(160), label = "rowBg")
    val fg = when {
        focused && danger -> Color(0xFFD93A2B)
        focused -> style.focusedText
        danger -> style.danger
        else -> Color.White
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(26.dp), bg)
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick, onLongClick)
            .focusable()
            .heightIn(min = 54.dp)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, fg, 22.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = ts(17.sp, FontWeight.SemiBold, fg), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                BasicText(subtitle, style = ts(13.sp, FontWeight.Medium, fg.copy(alpha = 0.75f)), modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            BasicText(value, style = ts(16.sp, FontWeight.SemiBold, fg.copy(alpha = 0.85f)), maxLines = 1)
        }
        trailing?.invoke(focused)
    }
}

@Composable
fun RadioRow(title: String, selected: Boolean, modifier: Modifier = Modifier, subtitle: String? = null, onClick: () -> Unit) {
    SettingRow(title = title, subtitle = subtitle, modifier = modifier, onClick = onClick, trailing = { focused ->
        val c = if (focused) LocalStyle.current.focusedText else Color.White
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(26.dp).border(2.dp, c.copy(alpha = 0.9f), CircleShape)
                .then(if (selected) Modifier.background(c, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Check, if (focused) Color.White else LocalStyle.current.focusedText, 16.dp)
        }
    })
}

/** Botón circular (ojo, flechas, ajustes del encabezado). */
@Composable
fun CircleButton(icon: ImageVector, modifier: Modifier = Modifier, size: Int = 44, iconTint: Color? = null, onClick: () -> Unit) {
    val style = LocalStyle.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "btnScale")
    val bg by animateColorAsState(if (focused) style.focusedFill else Color.White.copy(alpha = 0.16f), tween(140), label = "btnBg")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .dpadActions(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (focused) style.focusedText else (iconTint ?: Color.White), (size * 0.5f).dp)
    }
}

/** Bloque de texto informativo (no enfocable). */
@Composable
fun InfoBlock(text: String, mono: Boolean = false) {
    val style = LocalStyle.current
    BasicText(
        text,
        style = if (mono) ts(13.sp, FontWeight.Medium).copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        else ts(14.sp, FontWeight.Medium, Color.White.copy(alpha = 0.88f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .glass(RoundedCornerShape(20.dp), style.panel)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

/** Esqueleto de las pantallas de ajustes: título a la izquierda, opciones a la derecha. */
@Composable
fun SettingsPage(
    title: String,
    subtitle: String? = null,
    left: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.42f).fillMaxHeight().padding(start = 40.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                left()
                Spacer(Modifier.size(18.dp))
                BasicText(title, style = ts(38.sp, FontWeight.Bold))
                if (subtitle != null) {
                    BasicText(subtitle, style = ts(16.sp, FontWeight.Medium, Color.White.copy(alpha = 0.8f)), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides PivotScroll) {
            Column(
                Modifier.weight(0.58f).fillMaxHeight()
                    .fadingEdges()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 52.dp, top = 40.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                content()
            }
        }
    }
}

/** Mantiene el elemento enfocado cerca del centro de la lista, como en la interfaz de un TV, en vez de pegado al borde. */
@OptIn(ExperimentalFoundationApi::class)
private val PivotScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = containerSize * 0.38f - size / 2f
        return offset - target
    }
}

/** Desvanece suavemente el contenido en los bordes superior e inferior al desplazarse. */
fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.08f to Color.Black,
                0.92f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun BigIcon(icon: ImageVector) = Icon(icon, Color.White, 76.dp)
