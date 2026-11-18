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

// Ajustes y estado de Continuar viendo.

@Composable
fun RecsScreen(app: SimpleApp) {
    val ctx = LocalContext.current
    val p by app.store.state.collectAsState()
    val recs by com.andresterrazas.simple.home.Recs.items.collectAsState()
    val enabled by produceState(com.andresterrazas.simple.home.Recs.isEnabled(ctx)) {
        while (true) { value = com.andresterrazas.simple.home.Recs.isEnabled(ctx); delay(1000) }
    }
    val seen by produceState(com.andresterrazas.simple.home.Recs.seenSummary()) {
        while (true) { value = com.andresterrazas.simple.home.Recs.seenSummary(); delay(1500) }
    }
    val s = "recs"
    SettingsPage(title = "Continuar viendo", subtitle = "Sugerencias de tus apps", left = { BigIcon(Icons.PlayCircle) }) {
        ToggleRow("Mostrar en el inicio", p.showRecs, Modifier.restorableFocus(s, "show", default = true),
            subtitle = "Al posarte en una app de streaming, muestra lo que dejaste a medias") {
            app.store.update { it.copy(showRecs = !it.showRecs) }
        }
        SectionLabel("Android TV / Google TV · fila del sistema")
        val wn = com.andresterrazas.simple.home.WatchNext
        val wnAvail = remember { wn.available(ctx) }
        val wnGranted by produceState(wn.granted(ctx)) { while (true) { value = wn.granted(ctx); delay(1000) } }
        if (!wnAvail) {
            InfoBlock("Este dispositivo no tiene la fila \"Continuar viendo\" del sistema" + (if (com.andresterrazas.simple.home.DefaultHome.isFireTv) " (normal en Fire TV)" else "") + ". Se usan las sugerencias por notificación de abajo.")
        } else if (wnGranted) {
            InfoBlock("Permiso concedido · ${wn.lastCount} elementos leídos" + (wn.lastError?.let { "\nError: $it" } ?: ""))
        } else {
            SettingRow("Permitir leer Continuar viendo", Modifier.restorableFocus(s, "wn"), icon = Icons.ShieldCheck,
                subtitle = "Tu TV pedirá confirmar una sola vez") {
                (ctx as? com.andresterrazas.simple.MainActivity)?.requestWatchNextPermission()
            }
            InfoBlock("Si no aparece el diálogo, por ADB:")
            InfoBlock(wn.adbCommand(ctx), mono = true)
        }
        InfoBlock("La sección aparece al posarte en una app de streaming del dock que tenga algo a medias. YouTube está excluido por defecto; cambia cualquier app desde su menú (mantén OK) › Continuar viendo.")

        SectionLabel("Sugerencias por notificación")
        InfoBlock(
            "Acceso a notificaciones: ${if (enabled) "activo" else "no activo"}\n" +
                "Tarjetas disponibles (todas las fuentes): ${recs.size}" +
                if (enabled && recs.isEmpty()) "\nAbre y usa algunas apps (YouTube, Prime Video, Plex…); las que publiquen sugerencias aparecerán aquí." else ""
        )
        if (!enabled) {
            SettingRow("Abrir acceso a notificaciones", Modifier.restorableFocus(s, "access"), icon = Icons.Bell,
                subtitle = "Busca simple y permítelo (en algunas TV está en Apps › Acceso especial)") {
                val ok = runCatching {
                    ctx.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true
                }.getOrDefault(false)
                if (!ok) Toast.makeText(ctx, "Tu TV no tiene esa pantalla; usa el comando de ADB", Toast.LENGTH_LONG).show()
            }
            if (DefaultHome.canWriteSecure(ctx)) {
                SettingRow("Activar ahora", Modifier.restorableFocus(s, "enable"), icon = Icons.Zap) {
                    val ok = com.andresterrazas.simple.home.Recs.enableViaSecure(ctx)
                    Toast.makeText(ctx, if (ok) "Listo" else "No se pudo activar", Toast.LENGTH_SHORT).show()
                }
            } else {
                InfoBlock("O una sola vez por ADB:")
                InfoBlock(com.andresterrazas.simple.home.Recs.adbCommand(ctx), mono = true)
            }
        }
        if (!DefaultHome.isFireTv) {
            InfoBlock("En Google TV, Google solo deja publicar en esta fila a apps certificadas (Netflix, Prime Video, Disney+, etc.); otras apps pueden no aparecer.")
        }
        SectionLabel("Contenido detectado")
        val apps by app.repo.apps.collectAsState()
        InfoBlock(
            if (recs.isEmpty()) "Nada todavía."
            else recs.take(12).joinToString("\n") { r ->
                val inDock = p.favorites.any { com.andresterrazas.simple.home.Recs.samePackage(it, r.pkg) }
                val name = apps.firstOrNull { com.andresterrazas.simple.home.Recs.samePackage(it.pkg, r.pkg) }?.label ?: "app no instalada"
                "• ${r.title} — $name (${r.pkg}) · ${r.source}${if (inDock) "" else " · no está en el dock"}"
            },
            mono = true,
        )
        SectionLabel("Qué apps envían información")
        InfoBlock(if (seen.isEmpty()) "Nada todavía." else seen.joinToString("\n"), mono = true)
        InfoBlock("simple solo lee las sugerencias que las apps publican para el inicio de TV; no lee mensajes ni datos personales, y nada sale de tu ${com.andresterrazas.simple.home.DefaultHome.tvName}.")
    }
}
