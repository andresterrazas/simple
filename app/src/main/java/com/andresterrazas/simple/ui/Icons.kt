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
import kotlin.math.cos
import kotlin.math.sin

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

    private fun gearSpokes(): String {
        val sb = StringBuilder()
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 + 22.5)
            val x1 = (12 + 6.5 * cos(a)).toFloat(); val y1 = (12 + 6.5 * sin(a)).toFloat()
            val x2 = (12 + 9.2 * cos(a)).toFloat(); val y2 = (12 + 9.2 * sin(a)).toFloat()
            sb.append("M$x1 ${y1}L$x2 $y2")
        }
        return sb.toString()
    }

    val Gear = build("gear", circ(12f, 12f, 3f), circ(12f, 12f, 6.5f), gearSpokes())
    val Sliders = build("sliders", "M4 8h9M17 8h3M4 16h3M11 16h9", circ(15f, 8f, 2f), circ(9f, 16f, 2f))
    val Wifi = build("wifi", "M2 9a14.5 14.5 0 0 1 20 0", "M5 12.5a10 10 0 0 1 14 0", "M8.5 16a5 5 0 0 1 7 0", "M12 19.5h.01")
    val WifiOff = build("wifioff", "M2 9a14.5 14.5 0 0 1 20 0", "M5 12.5a10 10 0 0 1 14 0", "M12 19.5h.01", "M3 3l18 18")
    val Eye = build("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circ(12f, 12f, 3f))
    val EyeOff = build("eyeoff", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circ(12f, 12f, 3f), "M3 3l18 18")
    val Up = build("up", "M12 19V5", "M5 12l7-7 7 7")
    val Down = build("down", "M12 5v14", "M19 12l-7 7-7-7")
    val Left = build("left", "M19 12H5", "M12 19l-7-7 7-7")
    val Right = build("right", "M5 12h14", "M12 5l7 7-7 7")
    val Move = build("move", "M7 7h13", "M16 3l4 4-4 4", "M17 17H4", "M8 13l-4 4 4 4")
    val Plus = build("plus", "M12 5v14", "M5 12h14")
    val Minus = build("minus", "M5 12h14")
    val Brush = build("brush", "M14 4l6 6-8 8-6-6z", "M6 12c-2 1-3 3-3 5 0 2-1 3-1 3s4 0 6-2c1-1 1-2 1-3")
    val Info = build("info", circ(12f, 12f, 9f), "M12 11v5", "M12 7.5h.01")
    val Store = build("store", "M5 8h14l-1.2 12H6.2z", "M9 8a3 3 0 0 1 6 0")
    val Trash = build("trash", "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3")
    val Clock = build("clock", circ(12f, 12f, 9f), "M12 7v5l3 2")
    val ImageIc = build("image", "M4 5h16v14H4z", "M4 16l5-5 4 4 2-2 5 5", circ(15f, 9f, 1.5f))
    val Grid = build("grid", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z")
    val Sort = build("sort", "M4 7h10", "M4 12h7", "M4 17h4", "M17 5v14", "M14 16l3 3 3-3")
    val Check = build("check", "M5 12.5l4.5 4.5L19 7.5")
    val Home = build("home", "M4 11l8-7 8 7", "M6 10v10h12V10")
    val Tv = build("tv", "M3 6h18v12H3z", "M8 21h8")
    val Phone = build("phone", "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M11 18h2")
    val Star = build("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")
    val Alert = build("alert", "M12 3l10 18H2z", "M12 10v5", "M12 18h.01")
    val Person = build("person", circ(12f, 5f, 1.8f), "M5 9h14", "M12 9v5", "M9 21l3-7 3 7")
    val Display = build("display", "M3 5h18v11H3z", "M9 20h6", "M12 16v4")
    val Bluetooth = build("bluetooth", "M7 7l10 10-5 4V3l5 4L7 17")
    val Calendar = build("calendar", "M4 6h16v14H4z", "M4 10h16", "M8 3v4", "M16 3v4")
    val Dock = build("dock", "M3 14h18a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z", "M6 17h2", "M11 17h2", "M16 17h2", "M12 4v6", "M9 7l3 3 3-3")
    val Refresh = build("refresh", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5")
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
