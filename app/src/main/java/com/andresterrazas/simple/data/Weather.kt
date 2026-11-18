package com.andresterrazas.simple.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class HourForecast(val hour: Int, val temp: Int, val sky: Sky, val day: Boolean)

data class WeatherNow(
    val temp: Int,
    val sky: Sky,
    val day: Boolean,
    val max: Int,
    val min: Int,
    val rainChance: Int,
    val place: String,
    val hours: List<HourForecast>,
    val fetchedAt: Long,
)

data class Place(val lat: Double, val lon: Double, val name: String) {
    fun encode() = "$lat,$lon,$name"

    companion object {
        fun decode(s: String): Place? {
            val p = s.split(',', limit = 3)
            if (p.size < 3) return null
            return Place(p[0].toDoubleOrNull() ?: return null, p[1].toDoubleOrNull() ?: return null, p[2])
        }
    }
}

/**
 * Clima con Open-Meteo (gratis, sin cuenta ni llave). La ubicación se detecta por la conexión o se
 * elige por nombre de ciudad. Se guarda el último resultado para mostrarlo al instante.
 */
class WeatherRepo(private val context: Context) {
    private val _now = MutableStateFlow<WeatherNow?>(null)
    val now: StateFlow<WeatherNow?> = _now
    @Volatile var lastError: String? = null
    private val cacheFile get() = File(context.filesDir, "weather.json")

    init { runCatching { loadCache() } }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7000
        c.readTimeout = 9000
        c.setRequestProperty("User-Agent", "simple-launcher")
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Ubicación aproximada por la IP de la conexión (ciudad). */
    private fun autoPlace(): Place {
        val j = JSONObject(get("https://ipapi.co/json/"))
        val lat = j.optDouble("latitude", Double.NaN)
        val lon = j.optDouble("longitude", Double.NaN)
        require(!lat.isNaN() && !lon.isNaN()) { "Sin ubicación" }
        return Place(lat, lon, j.optString("city").ifBlank { "Tu zona" })
    }

    /** Busca ciudades por nombre (para elegir la ubicación a mano). */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val j = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=es&format=json"))
            val arr = j.optJSONArray("results") ?: return@runCatching emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val parts = listOf(o.optString("name"), o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct()
                Place(o.getDouble("latitude"), o.getDouble("longitude"), parts.joinToString(", "))
            }
        }.getOrDefault(emptyList())
    }

    suspend fun refresh(manual: String, unit: TempUnit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val place = Place.decode(manual) ?: autoPlace()
            val u = if (unit == TempUnit.FAHRENHEIT) "&temperature_unit=fahrenheit" else ""
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=2$u"
            val raw = get(url)
            val w = parse(raw, place.name)
            _now.value = w
            cacheFile.writeText(JSONObject().put("place", place.name).put("data", JSONObject(raw)).put("at", w.fetchedAt).toString())
            lastError = null
            true
        }.getOrElse { lastError = it.javaClass.simpleName + (it.message?.let { m -> ": $m" } ?: ""); false }
    }

    private fun loadCache() {
        if (!cacheFile.exists()) return
        val j = JSONObject(cacheFile.readText())
        _now.value = parse(j.getJSONObject("data").toString(), j.optString("place"), j.optLong("at"))
    }

    fun clear() { _now.value = null; cacheFile.delete() }

    companion object {
        fun sky(code: Int): Sky = when (code) {
            0, 1 -> Sky.CLEAR
            2 -> Sky.PARTLY
            3 -> Sky.CLOUDY
            45, 48 -> Sky.FOG
            in 51..57 -> Sky.DRIZZLE
            in 61..67, in 80..82 -> Sky.RAIN
            in 71..77, 85, 86 -> Sky.SNOW
            in 95..99 -> Sky.STORM
            else -> Sky.CLOUDY
        }

        fun label(s: Sky) = when (s) {
            Sky.CLEAR -> "Despejado"; Sky.PARTLY -> "Parcialmente nublado"; Sky.CLOUDY -> "Nublado"
            Sky.FOG -> "Neblina"; Sky.DRIZZLE -> "Llovizna"; Sky.RAIN -> "Lluvia"; Sky.SNOW -> "Nieve"; Sky.STORM -> "Tormenta"
        }

        fun parse(raw: String, place: String, at: Long = System.currentTimeMillis()): WeatherNow {
            val j = JSONObject(raw)
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            val hourly = j.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val days = hourly.getJSONArray("is_day")
            // Próximas horas en pasos de 3 desde la hora actual (hora local de la ciudad).
            val nowIso = cur.getString("time").take(13)
            var start = (0 until times.length()).indexOfFirst { times.getString(it).take(13) == nowIso }
            if (start < 0) start = 0
            val hours = (1..4).mapNotNull { k ->
                val i = start + k * 3
                if (i >= times.length()) null
                else HourForecast(times.getString(i).substring(11, 13).toInt(), Math.round(temps.getDouble(i)).toInt(), sky(codes.getInt(i)), days.getInt(i) == 1)
            }
            return WeatherNow(
                temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
                sky = sky(cur.getInt("weather_code")),
                day = cur.optInt("is_day", 1) == 1,
                max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                rainChance = daily.optJSONArray("precipitation_probability_max")?.optInt(0, 0) ?: 0,
                place = place,
                hours = hours,
                fetchedAt = at,
            )
        }
    }
}
