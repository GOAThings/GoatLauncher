package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.SunTimes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.TimeZone

/**
 * O dia/noite: o nascer e o pôr do sol no sítio da TV e se é de noite agora, de minuto a minuto. O mesmo para o ecrã
 * inicial e para o protetor de ecrã (AmbientDream), que antes fazia a sua cópia da conta.
 *
 * [location]: onde a TV está (a escolhida nas definições ou a do IP; null até se saber). [ticks]: a hora (ms), de
 * minuto a minuto ([everyMinute]; outra nos testes).
 */
class NightClock(
    account: Flow<AccountSettings>,
    location: Flow<Pair<Double, Double>?>,
    ticks: Flow<Long> = everyMinute(),
    private val zone: () -> TimeZone = TimeZone::getDefault,
) {
    /** Nascer e pôr do sol de hoje aqui (minutos desde a meia-noite); null sem localização ou no verão/inverno polar. */
    val sunTimes: Flow<Pair<Int, Int>?> =
        combine(location, ticks.distinctUntilChangedBy { minuteOfDay(it) / 60 }) { loc, now ->
            loc?.let { (lat, lon) -> SunTimes.of(lat, lon, now, zone()) }
        }

    /**
     * Modo noite em vigor: o escolhido no botão, ou, com o automático ligado, do pôr ao nascer do sol (sem saber
     * onde a box está, das [AccountSettings.NIGHT_FROM] às [AccountSettings.NIGHT_TO]).
     */
    val night: Flow<Boolean> = combine(account, ticks.map(::minuteOfDay), sunTimes) { a, minute, sun ->
        SunTimes.isNight(a, minute, sun)
    }.distinctUntilChanged()

    /** O minuto do dia (desde a meia-noite) em [millis]. */
    private fun minuteOfDay(millis: Long): Int {
        val cal = Calendar.getInstance(zone()).apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    companion object {
        /** Quanto o fundo escurece de noite, no ecrã inicial e no protetor de ecrã (a interface por cima fica igual). */
        const val DIM = 0.35f

        /** A hora agora e depois no início de cada minuto. */
        fun everyMinute(): Flow<Long> = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
}
