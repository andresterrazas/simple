package com.andresterrazas.simple.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.Collator
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OrderMode(val label: String) {
    ALPHA("Alfabético"),
    CUSTOM("Personalizado"),
    RECENT("Más recientes"),
}

enum class ClockMode(val label: String) {
    TIME("Solo hora"),
    TIME_DATE("Hora y fecha"),
    HIDDEN("Oculto"),
}

enum class DateStyle(private val pattern: String?) {
    SYSTEM(null),
    LONG("EEEE, MMMM d"),
    SHORT_DAY("EEE, MMM d"),
    DAY_MONTH("d MMM"),
    MONTH_DAY("MMM d"),
    DAY_DE_MONTH("d 'de' MMMM"),
    NUM_SHORT("dd/MM/yy"),
    NUM_LONG("dd/MM/yyyy");

    fun format(date: Date, locale: Locale = Locale.getDefault()): String =
        if (pattern == null) DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
        else SimpleDateFormat(pattern, locale).format(date)

    fun label(date: Date): String = if (this == SYSTEM) "Predeterminado del sistema" else format(date)
}

enum class OverlayStyle(val label: String) { LIGHT("Claro"), DARK("Oscuro") }

enum class IconMode(val label: String) {
    AUTO("Automático"),
    BANNER("Banner"),
    ICON("Ícono"),
    CUSTOM("Imagen propia"),
}

enum class InterceptMode(val label: String, val description: String) {
    STRICT("Estricto", "Solo cuando aparece la pantalla de inicio de Amazon."),
    BROAD("Amplio", "Cualquier pantalla del inicio de Amazon. Úsalo si el modo estricto no responde."),
}

enum class DockOpacity(val label: String, val alpha: Float) {
    NONE("Sin fondo", 0f),
    LOW("Baja", 0.10f),
    MEDIUM("Media", 0.18f),
    HIGH("Alta", 0.30f),
    MAX("Máxima", 0.45f),
}

/** Tamaño del dock como fracción del ancho seguro de la pantalla (tile/gap son valores de referencia para la vista previa). */
enum class DockSize(val label: String, val tile: Int, val gap: Int, val widthFraction: Float) {
    SMALL("Pequeño", 108, 18, 0.80f),
    MEDIUM("Mediano", 124, 18, 0.90f),
    LARGE("Grande", 138, 14, 1.00f),
}

enum class DockShape(val label: String, val tileRadius: Int, val panelRadius: Int) {
    SOFT("Suave", 14, 32),
    ROUND("Redonda", 22, 40),
    SQUARE("Recta", 6, 14),
}

enum class LabelMode(val label: String) {
    FOCUS("Al enfocar"),
    ALWAYS("Siempre"),
    NEVER("Nunca"),
}

enum class FocusZoom(val label: String, val scale: Float) {
    SUBTLE("Sutil", 1.05f),
    NORMAL("Normal", 1.10f),
    LARGE("Grande", 1.16f),
}

enum class MenuScale(val label: String, val factor: Float) {
    COMPACT("Compacto", 0.76f),
    NORMAL("Normal", 0.84f),
    LARGE("Grande", 0.94f),
}

/** Apps que no muestran "Continuar viendo" por defecto (sus sugerencias son infinitas, no contenido a medias). */
val DEFAULT_RECS_HIDDEN = setOf(
    "com.google.android.youtube.tv", "com.amazon.firetv.youtube", "com.google.android.youtube.tvmusic",
    "com.google.android.youtube.tvkids", "com.amazon.firetv.youtube.tv", "com.google.android.youtube.tvunplugged",
)

enum class WeatherStyle(val label: String, val description: String) {
    OFF("Oculto", "No se muestra el clima"),
    COMPACT("Compacto", "Ícono y temperatura junto a la hora"),
    DETAILED("Con pronóstico", "Compacto y, al posarte en la hora, el pronóstico del día"),
}

enum class TempUnit(val label: String, val symbol: String) { CELSIUS("Celsius", "°C"), FAHRENHEIT("Fahrenheit", "°F") }

enum class RotateMode(val label: String) {
    OFF("Desactivado"),
    HOURLY("Cada hora"),
    DAILY("Cada día"),
    WEEKLY("Cada semana"),
    MONTHLY("Cada mes"),
    CUSTOM("Personalizado"),
}

enum class RotateUnit(val label: String, val singular: String, val minutes: Long) {
    MINUTES("minutos", "minuto", 1),
    HOURS("horas", "hora", 60),
    DAYS("días", "día", 60 * 24),
}

const val CONTROL_CUSTOM = "custom"
const val CONTROL_SETTINGS = "settings"
const val CONTROL_WIFI = "wifi"
const val CONTROL_STORE = "store"
val DEFAULT_CONTROLS = listOf(CONTROL_CUSTOM, CONTROL_SETTINGS, CONTROL_WIFI, CONTROL_STORE)

const val DEFAULT_WALLPAPER = "builtin:amanecer"

data class Prefs(
    val orderMode: OrderMode = OrderMode.ALPHA,
    val clockMode: ClockMode = ClockMode.TIME_DATE,
    val dateStyle: DateStyle = DateStyle.LONG,
    val overlay: OverlayStyle = OverlayStyle.LIGHT,
    val blur: Int = 20,
    val wallpaper: String = DEFAULT_WALLPAPER,
    val favorites: List<String> = emptyList(),
    val customOrder: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val iconModes: Map<String, IconMode> = emptyMap(),
    val controls: List<String> = DEFAULT_CONTROLS,
    val hiddenControls: Set<String> = setOf(CONTROL_STORE),
    val intercept: InterceptMode = InterceptMode.STRICT,
    val seeded: Boolean = false,
    val setupShown: Boolean = false,
    val dockOpacity: DockOpacity = DockOpacity.MEDIUM,
    val dockSize: DockSize = DockSize.MEDIUM,
    val dockShape: DockShape = DockShape.SOFT,
    val labelMode: LabelMode = LabelMode.FOCUS,
    val focusZoom: FocusZoom = FocusZoom.NORMAL,
    val focusOutline: Boolean = true,
    val menuScale: MenuScale = MenuScale.NORMAL,
    val peekRow: Boolean = true,
    val showRecs: Boolean = true,
    val recsHidden: Set<String> = DEFAULT_RECS_HIDDEN,
    val weatherStyle: WeatherStyle = WeatherStyle.COMPACT,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    /** Ubicación elegida a mano: "lat,lon,nombre". Vacío = automática por la conexión. */
    val weatherPlace: String = "",
    val rotateMode: RotateMode = RotateMode.OFF,
    val rotateValue: Int = 30,
    val rotateUnit: RotateUnit = RotateUnit.MINUTES,
    val rotateShuffle: Boolean = false,
    val rotateKey: String = "",
) {
    fun iconMode(pkg: String): IconMode = iconModes[pkg] ?: IconMode.AUTO

    fun toggleFavorite(pkg: String): Prefs =
        copy(favorites = if (pkg in favorites) favorites - pkg else favorites + pkg)

    fun toggleHidden(pkg: String): Prefs =
        if (pkg in hidden) copy(hidden = hidden - pkg)
        else copy(hidden = hidden + pkg, favorites = favorites - pkg)

    fun moveInFavorites(pkg: String, delta: Int): Prefs {
        val list = favorites.toMutableList()
        val i = list.indexOf(pkg)
        if (i < 0) return this
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i == j) return this
        list.removeAt(i)
        list.add(j, pkg)
        return copy(favorites = list)
    }

    /** Coloca [pkg] junto a [target] en el orden general y cambia a orden personalizado. */
    fun moveNextTo(all: List<AppEntry>, pkg: String, target: String, after: Boolean): Prefs {
        val base = orderApps(all, this).map { it.pkg }.toMutableList()
        if (pkg !in base || target !in base || pkg == target) return this
        base.remove(pkg)
        var t = base.indexOf(target)
        if (after) t += 1
        base.add(t, pkg)
        return copy(orderMode = OrderMode.CUSTOM, customOrder = base)
    }

    fun moveControl(id: String, delta: Int): Prefs {
        val list = controls.toMutableList()
        val i = list.indexOf(id)
        if (i < 0) return this
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i == j) return this
        list.removeAt(i)
        list.add(j, id)
        return copy(controls = list)
    }
}

fun orderApps(apps: List<AppEntry>, p: Prefs): List<AppEntry> {
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return when (p.orderMode) {
        OrderMode.ALPHA -> apps.sortedWith(compareBy(collator) { it.label })
        OrderMode.RECENT -> apps.sortedByDescending { it.installTime }
        OrderMode.CUSTOM -> {
            val idx = HashMap<String, Int>()
            p.customOrder.forEachIndexed { i, pkg -> idx[pkg] = i }
            apps.sortedWith(
                compareBy<AppEntry> { idx[it.pkg] ?: Int.MAX_VALUE }.thenBy(collator) { it.label }
            )
        }
    }
}

class PrefsStore(context: Context) {
    private val sp = context.getSharedPreferences("simple", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Prefs> = _state

    fun update(transform: (Prefs) -> Prefs) {
        synchronized(this) {
            val next = transform(_state.value)
            if (next != _state.value) {
                _state.value = next
                save(next)
            }
        }
    }

    private fun list(key: String): List<String> =
        (sp.getString(key, "") ?: "").split('|').filter { it.isNotBlank() }

    private fun load(): Prefs {
        val d = Prefs()
        val savedControls = list("controls")
        val controls = (savedControls.filter { it in DEFAULT_CONTROLS } +
            DEFAULT_CONTROLS.filter { it !in savedControls }).distinct()
        return Prefs(
            orderMode = enumOr(sp.getString("order", null), d.orderMode),
            clockMode = enumOr(sp.getString("clock", null), d.clockMode),
            dateStyle = enumOr(sp.getString("date", null), d.dateStyle),
            overlay = enumOr(sp.getString("overlay", null), d.overlay),
            blur = sp.getInt("blur", d.blur),
            wallpaper = sp.getString("wallpaper", null) ?: d.wallpaper,
            favorites = list("favorites"),
            customOrder = list("customOrder"),
            hidden = list("hidden").toSet(),
            iconModes = list("iconModes").mapNotNull { e ->
                val i = e.indexOf('=')
                if (i <= 0) null else e.substring(0, i) to enumOr(e.substring(i + 1), IconMode.AUTO)
            }.toMap(),
            controls = controls,
            hiddenControls = if (sp.contains("hiddenControls")) list("hiddenControls").toSet() - CONTROL_CUSTOM else d.hiddenControls,
            intercept = enumOr(sp.getString("intercept", null), d.intercept),
            seeded = sp.getBoolean("seeded", false),
            setupShown = sp.getBoolean("setupShown", false),
            dockOpacity = enumOr(sp.getString("dockOpacity", null), d.dockOpacity),
            dockSize = enumOr(sp.getString("dockSize", null), d.dockSize),
            dockShape = enumOr(sp.getString("dockShape", null), d.dockShape),
            labelMode = enumOr(sp.getString("labelMode", null), d.labelMode),
            focusZoom = enumOr(sp.getString("focusZoom", null), d.focusZoom),
            focusOutline = sp.getBoolean("focusOutline", d.focusOutline),
            menuScale = enumOr(sp.getString("menuScale", null), d.menuScale),
            peekRow = sp.getBoolean("peekRow", d.peekRow),
            showRecs = sp.getBoolean("showRecs", d.showRecs),
            recsHidden = if (sp.contains("recsHidden")) list("recsHidden").toSet() else d.recsHidden,
            weatherStyle = enumOr(sp.getString("weatherStyle", null), d.weatherStyle),
            tempUnit = enumOr(sp.getString("tempUnit", null), d.tempUnit),
            weatherPlace = sp.getString("weatherPlace", null) ?: d.weatherPlace,
            rotateMode = enumOr(sp.getString("rotateMode", null), d.rotateMode),
            rotateValue = sp.getInt("rotateValue", d.rotateValue),
            rotateUnit = enumOr(sp.getString("rotateUnit", null), d.rotateUnit),
            rotateShuffle = sp.getBoolean("rotateShuffle", d.rotateShuffle),
            rotateKey = sp.getString("rotateKey", null) ?: d.rotateKey,
        )
    }

    private fun save(p: Prefs) {
        sp.edit()
            .putString("order", p.orderMode.name)
            .putString("clock", p.clockMode.name)
            .putString("date", p.dateStyle.name)
            .putString("overlay", p.overlay.name)
            .putInt("blur", p.blur)
            .putString("wallpaper", p.wallpaper)
            .putString("favorites", p.favorites.joinToString("|"))
            .putString("customOrder", p.customOrder.joinToString("|"))
            .putString("hidden", p.hidden.joinToString("|"))
            .putString("iconModes", p.iconModes.entries.joinToString("|") { "${it.key}=${it.value.name}" })
            .putString("controls", p.controls.joinToString("|"))
            .putString("hiddenControls", p.hiddenControls.joinToString("|"))
            .putString("intercept", p.intercept.name)
            .putBoolean("seeded", p.seeded)
            .putBoolean("setupShown", p.setupShown)
            .putString("dockOpacity", p.dockOpacity.name)
            .putString("dockSize", p.dockSize.name)
            .putString("dockShape", p.dockShape.name)
            .putString("labelMode", p.labelMode.name)
            .putString("focusZoom", p.focusZoom.name)
            .putBoolean("focusOutline", p.focusOutline)
            .putString("menuScale", p.menuScale.name)
            .putBoolean("peekRow", p.peekRow)
            .putBoolean("showRecs", p.showRecs)
            .putString("recsHidden", p.recsHidden.joinToString("|"))
            .putString("weatherStyle", p.weatherStyle.name)
            .putString("tempUnit", p.tempUnit.name)
            .putString("weatherPlace", p.weatherPlace)
            .putString("rotateMode", p.rotateMode.name)
            .putInt("rotateValue", p.rotateValue)
            .putString("rotateUnit", p.rotateUnit.name)
            .putBoolean("rotateShuffle", p.rotateShuffle)
            .putString("rotateKey", p.rotateKey)
            .apply()
    }
}

inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    if (name == null) default else runCatching { enumValueOf<T>(name) }.getOrDefault(default)
