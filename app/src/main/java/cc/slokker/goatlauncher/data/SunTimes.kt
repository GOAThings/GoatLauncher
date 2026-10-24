package cc.slokker.goatlauncher.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Nascer e pôr do sol num sítio, pelas fórmulas aproximadas da NOAA (erro de 1–2 minutos, chega para o dia/noite).
 * Sem rede: só precisa da latitude e longitude (as do tempo).
 */
object SunTimes {
    /**
     * Minutos desde a meia-noite (hora local de [zone]) do nascer e do pôr do sol no dia de [atMillis]; null no
     * verão ou no inverno polares (o sol não nasce ou não se põe).
     */
    fun of(lat: Double, lon: Double, atMillis: Long, zone: TimeZone = TimeZone.getDefault()): Pair<Int, Int>? {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = atMillis }
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val g = 2 * PI / 365 * (day - 1 + 0.5)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        // 90,833°: o centro do sol no horizonte, mais a refração e o raio do disco.
        val arg = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (arg < -1 || arg > 1) return null
        val ha = Math.toDegrees(acos(arg))
        val offset = zone.getOffset(atMillis) / 60_000
        fun local(utcMinutes: Double) = (floor(utcMinutes + offset).toInt() % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return local(720 - 4 * (lon + ha) - eqTime) to local(720 - 4 * (lon - ha) - eqTime)
    }

    /**
     * O modo noite em vigor ao [minute] do dia: o escolhido no botão, ou, com o automático, do pôr ao nascer do sol
     * ([sun]; sem ele, das [AccountSettings.NIGHT_FROM] às [AccountSettings.NIGHT_TO]).
     */
    fun isNight(account: AccountSettings, minute: Int, sun: Pair<Int, Int>?): Boolean = when {
        !account.nightAuto -> account.nightMode
        sun != null -> isNight(minute, sun)
        else -> minute / 60 >= AccountSettings.NIGHT_FROM || minute / 60 < AccountSettings.NIGHT_TO
    }

    /** Se [minute] (desde a meia-noite) é de noite, com o nascer e o pôr do sol de [sun]. */
    fun isNight(minute: Int, sun: Pair<Int, Int>): Boolean = minute < sun.first || minute >= sun.second

    private const val MINUTES_PER_DAY = 24 * 60
}
