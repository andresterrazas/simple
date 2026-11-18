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

// Encabezado del inicio: reloj, fecha y botones.

@Composable
internal fun HeaderBar(
    prefs: Prefs,
    controls: List<String>,
    weather: com.andresterrazas.simple.data.WeatherNow?,
    clockFocusable: Boolean,
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
    val showWeather = prefs.weatherStyle != com.andresterrazas.simple.data.WeatherStyle.OFF && weather != null
    val ids = if (clockFocusable) listOf(CLOCK_ID) + controls else controls
    var clockFocused by remember { mutableStateOf(false) }
    fun focusMod(id: String) = Modifier
        .focusRequester(requester(id))
        .focusProperties {
            canFocus = enabled
            up = FocusRequester.Cancel
            val i = ids.indexOf(id)
            if (i == 0) left = FocusRequester.Cancel
            if (i == ids.lastIndex) right = FocusRequester.Cancel
            down = downTarget() ?: FocusRequester.Cancel
        }
        .onFocusChanged { if (it.isFocused) onFocused(id) }

    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(
            Modifier.glass(CircleShape, style.panel).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (prefs.clockMode != ClockMode.HIDDEN || showWeather) {
                val bg by androidx.compose.animation.animateColorAsState(
                    if (clockFocused) style.focusedFill else Color.Transparent, tween(140), label = "clockBg",
                )
                val fg = if (clockFocused) style.focusedText else Color.White
                Row(
                    (if (clockFocusable) focusMod(CLOCK_ID).onFocusChanged { clockFocused = it.isFocused }.focusable() else Modifier)
                        .clip(CircleShape).background(bg).padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showWeather) {
                        Icon(skyIcon(weather!!.sky, weather.day), fg, 20.dp)
                        BasicText("${weather.temp}°", style = ts(18.sp, FontWeight.Bold, fg), modifier = Modifier.padding(start = 6.dp))
                        if (prefs.clockMode != ClockMode.HIDDEN) {
                            BasicText("•", style = ts(14.sp, FontWeight.Bold, fg.copy(alpha = 0.6f)), modifier = Modifier.padding(horizontal = 10.dp))
                        }
                    }
                    if (prefs.clockMode != ClockMode.HIDDEN) {
                        val time = SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
                        val text = if (prefs.clockMode == ClockMode.TIME_DATE) "$time  •  ${prefs.dateStyle.format(now)}" else time
                        BasicText(text, style = ts(18.sp, FontWeight.Bold, fg))
                    }
                }
            }
            controls.forEach { id ->
                key(id) {
                    val m = focusMod(id)
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
        // Pronóstico del día: aparece al posarse en la hora (estilo "Con pronóstico").
        AnimatedVisibility(
            visible = clockFocused && weather != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { -it / 8 },
            exit = fadeOut(tween(140)),
        ) {
            weather?.let { ForecastCard(it, prefs.tempUnit) }
        }
    }
}

const val CLOCK_ID = "clock"

@Composable
private fun ForecastCard(w: com.andresterrazas.simple.data.WeatherNow, unit: com.andresterrazas.simple.data.TempUnit) {
    val style = LocalStyle.current
    Row(
        Modifier.padding(top = 10.dp).glass(RoundedCornerShape(24.dp), style.panelStrong).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(skyIcon(w.sky, w.day), Color.White, 34.dp)
            BasicText("${w.temp}${unit.symbol}", style = ts(26.sp, FontWeight.Bold), modifier = Modifier.padding(top = 4.dp))
            BasicText(com.andresterrazas.simple.data.WeatherRepo.label(w.sky), style = ts(12.sp, FontWeight.Medium, Color.White.copy(alpha = 0.8f)))
        }
        Column {
            BasicText(w.place, style = ts(15.sp, FontWeight.Bold), maxLines = 1)
            BasicText("Máx ${w.max}° · Mín ${w.min}°", style = ts(13.sp, FontWeight.Medium, Color.White.copy(alpha = 0.85f)), modifier = Modifier.padding(top = 4.dp))
            BasicText("Lluvia ${w.rainChance} %", style = ts(13.sp, FontWeight.Medium, Color.White.copy(alpha = 0.85f)), modifier = Modifier.padding(top = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            w.hours.forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("${h.hour} h", style = ts(12.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.75f)))
                    Icon(skyIcon(h.sky, h.day), Color.White, 22.dp, Modifier.padding(vertical = 6.dp))
                    BasicText("${h.temp}°", style = ts(14.sp, FontWeight.Bold))
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

internal fun isConnected(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
