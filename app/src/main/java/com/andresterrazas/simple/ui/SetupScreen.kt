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

// Configurar simple como inicio predeterminado.

@Composable
fun SetupScreen(app: SimpleApp, nav: Nav) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val enabled = rememberServiceEnabled()
    val canWrite = remember { DefaultHome.canWriteSecure(ctx) }
    val s = "setup"
    androidx.compose.runtime.LaunchedEffect(Unit) { app.store.update { it.copy(setupShown = true) } }

    SettingsPage(title = "Inicio", subtitle = if (enabled) "simple está activo" else "Falta un paso", left = { BigIcon(Icons.Home) }) {
        SettingRow(
            if (enabled) "simple ya es tu inicio" else "simple aún no es tu inicio",
            Modifier.restorableFocus(s, "status", default = true),
            icon = if (enabled) Icons.Check else Icons.Alert,
            subtitle = if (enabled) "Al presionar Inicio en el control se abrirá simple" else "Activa el servicio de simple con alguna de estas opciones",
        ) { if (!enabled && canWrite) DefaultHome.enableViaSecure(ctx) }

        if (!DefaultHome.isFireTv) DefaultHomeSection(s)

        if (canWrite && !enabled) {
            SettingRow("Activar automáticamente", Modifier.restorableFocus(s, "auto"), icon = Icons.Zap, subtitle = "simple tiene permiso para activarse solo") {
                val ok = DefaultHome.enableViaSecure(ctx)
                Toast.makeText(ctx, if (ok) "Listo, simple está activo" else "No se pudo activar", Toast.LENGTH_SHORT).show()
            }
        }

        SectionLabel(if (DefaultHome.isFireTv) "Opción 1 · Desde el Fire TV" else "Respaldo · Servicio de accesibilidad")
        SettingRow("Abrir Accesibilidad", Modifier.restorableFocus(s, "a11y"), icon = Icons.Accessibility,
            subtitle = "Busca simple y actívalo. Si no aparece en la lista, usa la opción 2.") { SystemActions.openAccessibility(ctx) }

        SectionLabel("Opción 2 · Con ADB, una sola vez")
        InfoBlock(
            (if (DefaultHome.isFireTv) "1. En el Fire TV: Mi Fire TV › Opciones para desarrolladores › Depuración ADB: activada.\n"
            else "1. En la TV: Ajustes › Sistema › Opciones para desarrolladores › Depuración USB/ADB: activada.\n") +
            "2. Desde tu computadora o una app de ADB en el celular, conéctate a la IP de tu ${DefaultHome.tvName} y ejecuta:")
        InfoBlock(DefaultHome.adbCommands(ctx).joinToString("\n\n"), mono = true)
        InfoBlock("El último comando es opcional pero recomendado: permite que simple se vuelva a activar solo si una actualización del sistema lo desactiva.")
        SettingRow("Comprobar de nuevo", Modifier.restorableFocus(s, "check"), icon = Icons.Refresh,
            subtitle = if (enabled) "Activo" else "Inactivo") {
            if (DefaultHome.isEnabled(ctx)) Toast.makeText(ctx, "simple está activo", Toast.LENGTH_SHORT).show()
            else if (DefaultHome.enableViaSecure(ctx)) Toast.makeText(ctx, "Listo, simple está activo", Toast.LENGTH_SHORT).show()
            else Toast.makeText(ctx, "Todavía no está activo", Toast.LENGTH_SHORT).show()
        }

        SectionLabel("Detección del botón Inicio")
        InterceptMode.entries.forEach { m ->
            RadioRow(m.label, p.intercept == m, Modifier.restorableFocus(s, "mode_${m.name}"), subtitle = m.description) {
                app.store.update { it.copy(intercept = m) }
            }
        }
    }
}

/** Android TV / Google TV (Xiaomi, Sony, TCL…): ahí sí existe el "inicio predeterminado" del sistema. */
@Composable
internal fun DefaultHomeSection(scope: String) {
    val ctx = LocalContext.current
    val isDefault by produceState(DefaultHome.isDefaultHome(ctx)) {
        while (true) { value = DefaultHome.isDefaultHome(ctx); delay(1000) }
    }
    SectionLabel("Inicio predeterminado del sistema")
    SettingRow(
        if (isDefault) "simple es el inicio predeterminado" else "Elegir simple como inicio predeterminado",
        Modifier.restorableFocus(scope, "role"),
        icon = if (isDefault) Icons.Check else Icons.Home,
        subtitle = if (isDefault) "Tu TV abre simple al presionar Inicio, sin trucos" else "Tu TV te pedirá confirmar; elige simple",
    ) { if (!isDefault) requestDefaultHome(ctx) }
    if (!isDefault) {
        InfoBlock("Si tu TV no muestra la opción, conéctate por ADB y ejecuta:")
        InfoBlock(DefaultHome.setHomeAdbCommand(ctx), mono = true)
        InfoBlock("Mientras tanto, el servicio de accesibilidad de abajo también funciona como respaldo.")
    }
}

internal fun requestDefaultHome(ctx: android.content.Context) {
    val activity = ctx as? android.app.Activity
    fun tryStart(i: android.content.Intent): Boolean = runCatching {
        if (activity != null) activity.startActivityForResult(i, 42)
        else ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
    if (Build.VERSION.SDK_INT >= 29) {
        val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
            tryStart(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))) return
    }
    if (tryStart(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS))) return
    // Último recurso: el selector de inicio del sistema (con la opción "Siempre").
    val chooser = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
    if (!tryStart(chooser)) Toast.makeText(ctx, "Tu TV no permite elegirlo desde aquí; usa el comando de ADB", Toast.LENGTH_LONG).show()
}
