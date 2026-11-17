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
}

enum class InterceptMode(val label: String, val description: String) {
    STRICT("Estricto", "Solo cuando aparece la pantalla de inicio de Amazon."),
    BROAD("Amplio", "Cualquier pantalla del inicio de Amazon. Úsalo si el modo estricto no responde."),
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
            .apply()
    }
}

inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    if (name == null) default else runCatching { enumValueOf<T>(name) }.getOrDefault(default)
