package cc.slokker.goatlauncher.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

/** O tempo agora: temperatura (°C), código WMO do Open-Meteo e se é de dia. */
data class WeatherNow(val temperature: Int, val code: Int, val isDay: Boolean) {
    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

    val kind: Kind
        get() = when (code) {
            0 -> Kind.CLEAR
            1, 2 -> Kind.PARTLY
            3 -> Kind.CLOUDY
            45, 48 -> Kind.FOG
            in 51..67, in 80..82 -> Kind.RAIN
            in 71..77, 85, 86 -> Kind.SNOW
            in 95..99 -> Kind.STORM
            else -> Kind.CLOUDY
        }
}

/**
 * Meteorologia do sítio onde a box está, sem conta nem chave: a localização vem do IP (ipwho.is, guardada
 * um dia) e o tempo do Open-Meteo. Sem rede devolve o último valor guardado.
 */
class WeatherSource(context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(): WeatherNow? {
        if (!prefs.contains("temp")) return null
        return WeatherNow(prefs.getInt("temp", 0), prefs.getInt("code", 3), prefs.getBoolean("day", true))
    }

    suspend fun current(): WeatherNow? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon) = locate() ?: return@runCatching cached()
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day"
            val cur = getJson(url).getAsJsonObject("current")
            WeatherNow(
                temperature = cur.get("temperature_2m").asDouble.roundToInt(),
                code = cur.get("weather_code").asInt,
                isDay = cur.get("is_day").asInt == 1,
            ).also {
                prefs.edit {
                    putInt("temp", it.temperature)
                    putInt("code", it.code)
                    putBoolean("day", it.isDay)
                }
            }
        }.onFailure { Log.w("Weather", "Tempo: ${it.message}") }.getOrElse { cached() }
    }

    /** A última localização conhecida (latitude, longitude), sem ir à rede. */
    fun cachedLocation(): Pair<Double, Double>? =
        if (prefs.contains("lat")) prefs.getFloat("lat", 0f).toDouble() to prefs.getFloat("lon", 0f).toDouble() else null

    /** A localização da box pelo IP (guardada um dia); também serve para o nascer e o pôr do sol. */
    suspend fun location(): Pair<Double, Double>? = withContext(Dispatchers.IO) { runCatching { locate() }.getOrElse { cachedLocation() } }

    private fun locate(): Pair<Double, Double>? {
        val at = prefs.getLong("loc_at", 0)
        if (prefs.contains("lat") && System.currentTimeMillis() - at < LOCATION_MS) {
            return prefs.getFloat("lat", 0f).toDouble() to prefs.getFloat("lon", 0f).toDouble()
        }
        val json = runCatching { getJson("https://ipwho.is/?fields=success,latitude,longitude") }.getOrNull()
        if (json == null || json.get("success")?.asBoolean != true) {
            return if (prefs.contains("lat")) prefs.getFloat("lat", 0f).toDouble() to prefs.getFloat("lon", 0f).toDouble() else null
        }
        val lat = json.get("latitude").asDouble
        val lon = json.get("longitude").asDouble
        prefs.edit {
            putFloat("lat", lat.toFloat())
            putFloat("lon", lon.toFloat())
            putLong("loc_at", System.currentTimeMillis())
        }
        return lat to lon
    }

    private fun getJson(url: String) = http.newCall(Request.Builder().url(url).build()).execute().use { res ->
        if (!res.isSuccessful) error("HTTP ${res.code}")
        JsonParser.parseString(res.body.string()).asJsonObject
    }

    private companion object {
        const val LOCATION_MS = 24 * 60 * 60_000L
    }
}
