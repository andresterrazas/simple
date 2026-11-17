@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.andresterrazas.simple.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.OverlayStyle
import com.andresterrazas.simple.home.DefaultHome

@Composable
fun SimpleRoot(app: SimpleApp, ui: UiState, nav: Nav) {
    val ctx = LocalContext.current
    val prefs by app.store.state.collectAsState()
    val images by app.wallpapers.images.collectAsState()
    val style = remember(prefs.overlay) { UiStyle(prefs.overlay == OverlayStyle.DARK) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    BackHandler {
        when {
            ui.moving != null -> ui.moving = null
            nav.pop() -> Unit
            ui.expanded -> ui.collapse()
            else -> Unit
        }
    }

    LaunchedEffect(Unit) {
        if (!prefs.setupShown && !DefaultHome.isEnabled(ctx)) nav.push(Screen.Setup)
    }

    CompositionLocalProvider(
        LocalStyle provides style,
        LocalBackdrop provides images?.backdrop,
        LocalRootSize provides rootSize,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize = it }) {
            images?.let {
                Image(it.background, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(style.scrim))
            // Transición "fundido a través": la pantalla anterior sale antes de que entre la nueva, sin imágenes encimadas.
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = { fadeIn(tween(170, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                label = "nav",
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(app, ui, nav)
                    is Screen.AppMenu -> AppMenuScreen(app, ui, nav, screen.pkg)
                    is Screen.IconPicker -> IconPickerScreen(app, nav, screen.pkg)
                    Screen.Settings -> SettingsMainScreen(app, nav)
                    Screen.Setup -> SetupScreen(app, nav)
                    Screen.Order -> OrderScreen(app)
                    Screen.Clock -> ClockScreen(app)
                    Screen.Controls -> ControlsScreen(app)
                    Screen.Wallpaper -> WallpaperScreen(app, nav)
                    Screen.Dock -> DockScreen(app)
                    Screen.Blur -> BlurScreen(app)
                    Screen.Upload -> UploadScreen(app, nav)
                    Screen.Apps -> AppsScreen(app)
                    Screen.System -> SystemScreen()
                    Screen.Diagnostics -> DiagnosticsScreen(app)
                }
            }
        }
    }
}
