package com.andresterrazas.simple.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andresterrazas.simple.SimpleApp
import com.andresterrazas.simple.data.CONTROL_CUSTOM
import com.andresterrazas.simple.data.ClockMode
import com.andresterrazas.simple.data.DateStyle
import com.andresterrazas.simple.data.Place
import com.andresterrazas.simple.data.TempUnit
import com.andresterrazas.simple.data.WeatherRepo
import com.andresterrazas.simple.data.WeatherStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Controles del encabezado: todo lo que aparece arriba a la derecha del inicio, en un solo lugar.

@Composable
fun HeaderSettingsScreen(app: SimpleApp, nav: Nav) {
    val p by app.store.state.collectAsState()
    val weather by app.weather.now.collectAsState()
    val now = rememberNow()
    val s = "header"
    SettingsPage(title = "Encabezado", subtitle = "Fecha y hora, clima y botones", left = { BigIcon(Icons.PanelTop) }) {
        SectionLabel("Fecha y hora")
        DropdownRow(
            "Mostrar", ClockMode.entries, p.clockMode, { it.label }, s, "clock", icon = Icons.Clock, default = true,
        ) { m -> app.store.update { it.copy(clockMode = m) } }
        if (p.clockMode == ClockMode.TIME_DATE) {
            DropdownRow(
                "Formato de fecha", DateStyle.entries, p.dateStyle, { it.label(now) }, s, "date", icon = Icons.Calendar,
            ) { d -> app.store.update { it.copy(dateStyle = d) } }
        }

        SectionLabel("Clima")
        DropdownRow(
            "Estilo", WeatherStyle.entries, p.weatherStyle, { it.label }, s, "weather", icon = Icons.CloudSun,
            optionSubtitle = { it.description },
        ) { w -> app.store.update { it.copy(weatherStyle = w) } }
        if (p.weatherStyle != WeatherStyle.OFF) {
            val place = Place.decode(p.weatherPlace)
            SettingRow("Ubicación", Modifier.restorableFocus(s, "place"), icon = Icons.Pin,
                value = place?.name?.substringBefore(',') ?: "Automática") { nav.push(Screen.WeatherPlace) }
            SettingRow("Unidades", Modifier.restorableFocus(s, "unit"), icon = Icons.Thermometer, value = p.tempUnit.symbol) {
                app.store.update { it.copy(tempUnit = if (it.tempUnit == TempUnit.CELSIUS) TempUnit.FAHRENHEIT else TempUnit.CELSIUS) }
            }
            InfoBlock(
                weather?.let { w ->
                    val at = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(w.fetchedAt))
                    "${w.place}: ${w.temp}${p.tempUnit.symbol}, ${WeatherRepo.label(w.sky).lowercase()} · actualizado a las $at"
                } ?: (app.weather.lastError?.let { "No se pudo obtener el clima. Revisa la conexión." } ?: "Obteniendo el clima…")
            )
        }

        SectionLabel("Botones")
        p.controls.forEachIndexed { i, id ->
            key(id) {
                val (label, icon) = controlInfo(id)
                val hidden = id != CONTROL_CUSTOM && id in p.hiddenControls
                ManageRow(label, leading = { Icon(icon, Color.White, 22.dp) }, dimmed = hidden) {
                    if (id != CONTROL_CUSTOM) {
                        CircleButton(if (hidden) Icons.EyeOff else Icons.Eye, Modifier.restorableFocus(s, "eye_$id"),
                            iconTint = if (hidden) Color(0xFFFF8A7A) else null) {
                            app.store.update { it.copy(hiddenControls = if (hidden) it.hiddenControls - id else it.hiddenControls + id) }
                        }
                    }
                    if (i > 0) CircleButton(Icons.Up, Modifier.restorableFocus(s, "up_$id")) { app.store.update { it.moveControl(id, -1) } }
                    if (i < p.controls.lastIndex) CircleButton(Icons.Down, Modifier.restorableFocus(s, "down_$id")) { app.store.update { it.moveControl(id, 1) } }
                }
            }
        }
        InfoBlock("Personalizaciones siempre está visible para que puedas volver a esta configuración.")
    }
}

@Composable
fun WeatherPlaceScreen(app: SimpleApp) {
    val p by app.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val style = LocalStyle.current
    val s = "place"
    fun search() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = app.weather.search(query)
            searching = false
        }
    }
    SettingsPage(title = "Ubicación", subtitle = "Para el clima", left = { BigIcon(Icons.Pin) }) {
        RadioRow("Automática", p.weatherPlace.isBlank(), Modifier.restorableFocus(s, "auto", default = true),
            subtitle = "Según la ubicación aproximada de tu conexión") {
            app.store.update { it.copy(weatherPlace = "") }
        }
        Place.decode(p.weatherPlace)?.let { cur ->
            RadioRow(cur.name, true, Modifier.restorableFocus(s, "current"), subtitle = "Ubicación elegida") {}
        }

        SectionLabel("Buscar ciudad")
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 50.dp)
                .glass(RoundedCornerShape(50), if (fieldFocused) style.focusedFill else style.panel)
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = ts(17.sp, FontWeight.SemiBold, if (fieldFocused) style.focusedText else Color.White),
                cursorBrush = SolidColor(if (fieldFocused) style.focusedText else Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }, onDone = { search() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { fieldFocused = it.isFocused },
                decorationBox = { inner ->
                    if (query.isEmpty()) BasicText("Escribe una ciudad, por ejemplo Guadalajara",
                        style = ts(17.sp, FontWeight.Medium, (if (fieldFocused) style.focusedText else Color.White).copy(alpha = 0.55f)))
                    inner()
                },
            )
        }
        SettingRow("Buscar", Modifier.restorableFocus(s, "search"), icon = Icons.Search,
            subtitle = if (searching) "Buscando…" else null) { search() }
        results?.let { list ->
            if (list.isEmpty()) InfoBlock("No se encontraron ciudades con ese nombre.")
            list.forEachIndexed { i, place ->
                RadioRow(place.name, p.weatherPlace == place.encode(), Modifier.restorableFocus(s, "r$i")) {
                    app.store.update { it.copy(weatherPlace = place.encode()) }
                }
            }
        }
    }
}
