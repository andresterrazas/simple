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

// Menú principal de Personalizar.


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

        SectionLabel("Personalización")
        SettingRow("Controles del encabezado", Modifier.restorableFocus(s, "controls"), icon = Icons.Sliders,
            subtitle = "Fecha y hora, clima y botones") { nav.push(Screen.Controls) }
        SettingRow("Dock", Modifier.restorableFocus(s, "dock"), icon = Icons.Dock, subtitle = "Opacidad, tamaño, forma y nombres") { nav.push(Screen.Dock) }
        SettingRow("Fondo", Modifier.restorableFocus(s, "wallpaper"), icon = Icons.ImageIc,
            subtitle = if (p.rotateMode == com.andresterrazas.simple.data.RotateMode.OFF) "Galería, desenfoque y tus fotos" else "Cambio automático: ${p.rotateMode.label.lowercase()}") { nav.push(Screen.Wallpaper) }
        SettingRow("Continuar viendo", Modifier.restorableFocus(s, "recs"), icon = Icons.Tv,
            value = if (p.showRecs) "Activado" else "Desactivado") { nav.push(Screen.RecsSettings) }
        SettingRow("Tamaño de los menús", Modifier.restorableFocus(s, "scale"), icon = Icons.Display, value = p.menuScale.label) {
            val all = com.andresterrazas.simple.data.MenuScale.entries
            app.store.update { it.copy(menuScale = all[(all.indexOf(it.menuScale) + 1) % all.size]) }
        }

        SectionLabel("Gestión")
        SettingRow(
            "Aplicaciones", Modifier.restorableFocus(s, "apps"), icon = Icons.Grid,
            subtitle = "Orden ${p.orderMode.label.lowercase()} · " + if (p.hidden.isEmpty()) "ninguna oculta" else "${p.hidden.size} oculta${if (p.hidden.size == 1) "" else "s"}",
        ) { nav.push(Screen.Apps) }
        SettingRow(if (DefaultHome.isFireTv) "Ajustes del Fire TV" else "Ajustes de la TV", Modifier.restorableFocus(s, "system"), icon = Icons.Tv,
            subtitle = "Red, pantalla, controles, apps y más") { nav.push(Screen.System) }
        if (DefaultHome.isFireTv) {
            SettingRow("Abrir el inicio de Amazon", Modifier.restorableFocus(s, "amazon"), icon = Icons.Home, subtitle = "simple se pausa 5 minutos") {
                DefaultHome.pause(5)
                SystemActions.openAmazonHome(ctx)
            }
        }

        SectionLabel("Acerca de")
        SettingRow("Diagnóstico", Modifier.restorableFocus(s, "diag"), icon = Icons.Alert, subtitle = "Estado del servicio y compatibilidad") { nav.push(Screen.Diagnostics) }
    }
}
