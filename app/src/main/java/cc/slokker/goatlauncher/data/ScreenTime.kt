package cc.slokker.goatlauncher.data

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar

/** O tempo de ecrã gasto nas apps no dia [day] ("aaaa-mm-dd", na hora local). Outro dia começa do zero. */
internal data class ScreenTimeDay(val day: String = "", val usedMs: Long = 0L) {
    fun add(today: String, ms: Long) = if (today == day) copy(usedMs = usedMs + ms) else ScreenTimeDay(today, ms)

    fun usedOn(today: String): Long = if (today == day) usedMs else 0L

    companion object {
        fun dayOf(calendar: Calendar): String =
            "%04d-%02d-%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }
}

/**
 * O tempo de ecrã por dia do controlo parental ([DeviceSettings.dailyLimit]). Conta o HomeGuardService (o tempo
 * com uma app à frente e o ecrã ligado; o launcher não conta) e guarda-se só nesta TV: não vai para a conta, e um
 * dia novo começa do zero à meia-noite. Acabado o tempo, todas as apps pedem o código (ver [ParentalLock.appLocked]).
 */
class ScreenTime(context: Context, private val now: () -> Calendar = Calendar::getInstance) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var current = ScreenTimeDay(prefs.getString(KEY_DAY, null).orEmpty(), prefs.getLong(KEY_USED_MS, 0L))

    private fun today() = ScreenTimeDay.dayOf(now())

    /** O tempo gasto hoje. */
    fun usedMs(): Long = current.usedOn(today())

    @Synchronized
    fun add(ms: Long) {
        current = current.add(today(), ms)
        prefs.edit {
            putString(KEY_DAY, current.day)
            putLong(KEY_USED_MS, current.usedMs)
        }
    }

    /** Já se gastou o tempo de hoje? (Só com o controlo parental ligado e um limite escolhido.) */
    fun timeUp(device: DeviceSettings): Boolean = ParentalLock.timeUp(device, usedMs())

    /** Nomes no disco: nunca mudam (ver PersistedNamesTest). */
    internal companion object {
        const val PREFS = "screen_time"
        const val KEY_DAY = "day"
        const val KEY_USED_MS = "used_ms"
    }
}
