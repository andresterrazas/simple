package com.andresterrazas.simple.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Íconos de trazo propios (24x24), dibujados como vectores para que se vean nítidos en 4K. */
object Icons {
    private fun circ(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun build(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) {
                addPath(
                    pathData = addPathNodes(p),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** Rectángulo con esquinas redondeadas como trazo. */
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    // Geometría basada en Lucide (licencia ISC): mismo trazo de 2 px y puntas redondas que el resto.

    /** Engrane de verdad (dientes redondeados), para los ajustes del sistema. */
    val Gear = build(
        "gear",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        circ(12f, 12f, 3f),
    )
    val Sliders = build("sliders", "M4 8h9M17 8h3M4 16h3M11 16h9", circ(15f, 8f, 2f), circ(9f, 16f, 2f))
    val Wifi = build("wifi", "M2 8.82a15 15 0 0 1 20 0", "M5 12.86a10 10 0 0 1 14 0", "M8.5 16.43a5 5 0 0 1 7 0", "M12 20h.01")
    val WifiOff = build("wifioff", "M12 20h.01", "M8.5 16.43a5 5 0 0 1 7 0", "M5 12.86a10 10 0 0 1 5.17-2.69", "M19 12.86a10 10 0 0 0-2.01-1.45", "M2 8.82a15 15 0 0 1 4.18-2.65", "M22 8.82a15 15 0 0 0-11.29-3.76", "M2 2l20 20")
    val Eye = build("eye", "M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0", circ(12f, 12f, 3f))
    val EyeOff = build("eyeoff", "M10.73 5.08A10.43 10.43 0 0 1 12 5c4.97 0 8.7 3.4 9.94 6.65a1 1 0 0 1 0 .7 10.75 10.75 0 0 1-1.44 2.49", "M14.08 14.16a3 3 0 0 1-4.17-4.17", "M17.48 17.5a10.75 10.75 0 0 1-15.42-5.15 1 1 0 0 1 0-.7 10.75 10.75 0 0 1 4.45-5.14", "M2 2l20 20")
    val Up = build("up", "M12 19V5", "M5 12l7-7 7 7")
    val Down = build("down", "M12 5v14", "M19 12l-7 7-7-7")
    val Left = build("left", "M19 12H5", "M12 19l-7-7 7-7")
    val Right = build("right", "M5 12h14", "M12 5l7 7-7 7")
    val Move = build("move", "M7 7h13", "M16 3l4 4-4 4", "M17 17H4", "M8 13l-4 4 4 4")
    val Plus = build("plus", "M12 5v14", "M5 12h14")
    val Minus = build("minus", "M5 12h14")
    val Brush = build("brush", "M9.06 11.9l8.07-8.06a2.85 2.85 0 1 1 4.03 4.03l-8.06 8.08", "M7.07 14.94c-1.66 0-3 1.35-3 3.02 0 1.33-2.5 1.52-2 2.02 1.08 1.1 2.49 2.02 4 2.02 2.2 0 4-1.8 4-4.04a3.01 3.01 0 0 0-3-3.02z")
    val Info = build("info", circ(12f, 12f, 10f), "M12 16v-4", "M12 8h.01")
    val Store = build("store", "M16 10a4 4 0 0 1-8 0", "M3.1 6.04h17.8", "M3.4 5.53A2 2 0 0 0 3 6.73V20a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6.73a2 2 0 0 0-.4-1.2l-2-2.66A2 2 0 0 0 17 2H7a2 2 0 0 0-1.6.8z")
    val Trash = build("trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M10 11v6", "M14 11v6")
    val Clock = build("clock", circ(12f, 12f, 10f), "M12 6v6l4 2")
    val ImageIc = build("image", rect(3f, 3f, 18f, 18f, 2f), circ(9f, 9f, 2f), "M21 15l-3.09-3.09a2 2 0 0 0-2.82 0L6 21")
    val Grid = build("grid", rect(3f, 3f, 7f, 7f, 1f), rect(14f, 3f, 7f, 7f, 1f), rect(14f, 14f, 7f, 7f, 1f), rect(3f, 14f, 7f, 7f, 1f))
    val Sort = build("sort", "M3 16l4 4 4-4", "M7 20V4", "M11 4h10", "M11 8h7", "M11 12h4")
    val Check = build("check", "M20 6L9 17l-5-5")
    val Home = build("home", "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .71-1.53l7-6a2 2 0 0 1 2.58 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Tv = build("tv", rect(2f, 7f, 20f, 15f, 2f), "M17 2l-5 5-5-5")
    val Phone = build("phone", rect(5f, 2f, 14f, 20f, 2f), "M12 18h.01")
    val Star = build("star", "M11.53 2.3a.53.53 0 0 1 .95 0l2.31 4.68a2.12 2.12 0 0 0 1.6 1.16l5.16.76a.53.53 0 0 1 .3.9l-3.74 3.64a2.12 2.12 0 0 0-.61 1.88l.88 5.14a.53.53 0 0 1-.77.56l-4.62-2.43a2.12 2.12 0 0 0-1.97 0L6.4 21.01a.53.53 0 0 1-.77-.56l.88-5.13a2.12 2.12 0 0 0-.61-1.88L2.16 9.8a.53.53 0 0 1 .29-.9l5.17-.76a2.12 2.12 0 0 0 1.6-1.16z")
    val Alert = build("alert", "M21.73 18l-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01")
    val Person = build("person", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circ(12f, 7f, 4f))
    val Display = build("display", rect(2f, 3f, 20f, 14f, 2f), "M8 21h8", "M12 17v4")
    val Bluetooth = build("bluetooth", "M7 7l10 10-5 5V2l5 5L7 17")
    val Calendar = build("calendar", rect(3f, 4f, 18f, 18f, 2f), "M3 10h18", "M8 2v4", "M16 2v4")
    /** Panel con una fila de apps en la base: el dock. */
    val Dock = build("dock", rect(3f, 3f, 18f, 18f, 2f), "M3 15h18", "M7.5 18h.01", "M12 18h.01", "M16.5 18h.01")
    /** Panel con la franja superior marcada: el encabezado. */
    val PanelTop = build("paneltop", rect(3f, 3f, 18f, 18f, 2f), "M3 9h18", "M14 6h4")
    val PlayCircle = build("playcircle", circ(12f, 12f, 10f), "M10 8.5v7a.5.5 0 0 0 .77.42l5.5-3.5a.5.5 0 0 0 0-.84l-5.5-3.5A.5.5 0 0 0 10 8.5z")
    val TextSize = build("textsize", "M21 14h-5", "M16 16v-3.5a2.5 2.5 0 0 1 5 0V16", "M4.5 13h6", "M3 16l4.5-9 4.5 9")
    val ExternalLink = build("external", "M15 3h6v6", "M10 14L21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")
    val Activity = build("activity", "M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2")
    val Contrast = build("contrast", circ(12f, 12f, 10f), "M12 18a6 6 0 0 0 0-12v12z")
    val Aperture = build("aperture", circ(12f, 12f, 10f), "M14.31 8l5.74 9.94", "M9.69 8h11.48", "M7.38 12l5.74-9.94", "M9.69 16L3.95 6.06", "M14.31 16H2.83", "M16.62 12l-5.74 9.94")
    val Shuffle = build("shuffle", "M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22", "M18 2l4 4-4 4", "M2 6h1.9c1.5 0 2.9.9 3.6 2.2", "M22 18h-5.9c-1.3 0-2.6-.7-3.3-1.8l-.5-.8", "M18 14l4 4-4 4")
    val SkipForward = build("skip", "M6 4.5v15a.5.5 0 0 0 .8.4l10-7.5a.5.5 0 0 0 0-.8l-10-7.5a.5.5 0 0 0-.8.4z", "M20 4v16")
    val Thermometer = build("thermometer", "M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0z")
    val Bell = build("bell", "M10.27 21a2 2 0 0 0 3.46 0", "M3.26 15.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.67C19.41 13.96 18 12.5 18 8A6 6 0 0 0 6 8c0 4.5-1.41 5.96-2.74 7.33")
    val ShieldCheck = build("shieldcheck", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "M9 12l2 2 4-4")
    val Zap = build("zap", "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z")
    val Accessibility = build("a11y", circ(16f, 4f, 1f), "M18 19l1-7-6 1", "M5 8l3-3 5.5 3-2.36 3.5", "M4.24 14.5a5 5 0 0 0 6.88 6", "M13.76 17.5a5 5 0 0 0-6.88-6")
    val Package = build("package", "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z", "M12 22V12", "M3.29 7L12 12l8.71-5", "M7.5 4.27l9 5.15")
    val QrCode = build("qrcode", rect(3f, 3f, 5f, 5f, 1f), rect(16f, 3f, 5f, 5f, 1f), rect(3f, 16f, 5f, 5f, 1f), "M21 16h-3a2 2 0 0 0-2 2v3", "M21 21v.01", "M12 7v3a2 2 0 0 1-2 2H7", "M3 12h.01", "M12 3h.01", "M12 16v.01", "M16 12h1", "M21 12v.01", "M12 21v-1")
    val Search = build("search", circ(11f, 11f, 8f), "M21 21l-4.3-4.3")
    val ChevronDown = build("chevdown", "M6 9l6 6 6-6")
    val ChevronUp = build("chevup", "M18 15l-6-6-6 6")
    val Gamepad = build("gamepad", "M6 11h4", "M8 9v4", "M15 12h.01", "M18 10h.01", "M17.32 5H6.68a4 4 0 0 0-3.98 3.59c-.01.05-.01.1-.02.15C2.6 9.42 2 14.46 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.41-1.41A2 2 0 0 1 9.83 16h4.34a2 2 0 0 1 1.42.59L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.55-.6-6.58-.68-7.26-.01-.05-.01-.1-.02-.15A4 4 0 0 0 17.32 5z")
    val Moon = build("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Power = build("power", "M12 3v8", "M6.3 6.3a8 8 0 1 0 11.4 0")
    val Sun = build("sun", circ(12f, 12f, 4f), "M12 2v2", "M12 20v2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4", "M2 12h2", "M20 12h2", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4")
    val Cloud = build("cloud", "M7 18h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.4 1.6A3.3 3.3 0 0 0 7 18z")
    val CloudSun = build("cloudsun", "M8 19h9a3.5 3.5 0 0 0 .4-7 5 5 0 0 0-9.6 1.4A2.8 2.8 0 0 0 8 19z", "M7 3v1.5", "M2.8 7h1.5", "M3.8 3.8l1 1", "M10.2 3.8l-1 1", "M9.6 9.4a3.5 3.5 0 1 0-5 2.8")
    val Rain = build("rain", "M7 14h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.4 1.6A3.3 3.3 0 0 0 7 14z", "M8 17l-1 3", "M12 17l-1 3", "M16 17l-1 3")
    val Snow = build("snow", "M7 14h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.4 1.6A3.3 3.3 0 0 0 7 14z", "M8 18h.01", "M12 18h.01", "M16 18h.01", "M10 21h.01", "M14 21h.01")
    val Storm = build("storm", "M7 14h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.4 1.6A3.3 3.3 0 0 0 7 14z", "M12 14l-2 4h4l-2 4")
    val Fog = build("fog", "M4 9h16", "M3 13h18", "M5 17h14")
    val Pin = build("pin", "M20 10c0 4.99-5.54 10.19-7.4 11.8a1 1 0 0 1-1.2 0C9.54 20.19 4 14.99 4 10a8 8 0 0 1 16 0", circ(12f, 10f, 3f))
    val Refresh = build("refresh", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5")
}

@Composable
fun Icon(icon: ImageVector, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

fun skyIcon(sky: com.andresterrazas.simple.data.Sky, day: Boolean): ImageVector = when (sky) {
    com.andresterrazas.simple.data.Sky.CLEAR -> if (day) Icons.Sun else Icons.Moon
    com.andresterrazas.simple.data.Sky.PARTLY -> if (day) Icons.CloudSun else Icons.Cloud
    com.andresterrazas.simple.data.Sky.CLOUDY -> Icons.Cloud
    com.andresterrazas.simple.data.Sky.FOG -> Icons.Fog
    com.andresterrazas.simple.data.Sky.DRIZZLE, com.andresterrazas.simple.data.Sky.RAIN -> Icons.Rain
    com.andresterrazas.simple.data.Sky.SNOW -> Icons.Snow
    com.andresterrazas.simple.data.Sky.STORM -> Icons.Storm
}
