package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.SunTimes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** O dia/noite do ecrã inicial e do protetor de ecrã (a mesma regra para os dois). */
class NightClockTest {
    private val lisbon = TimeZone.getTimeZone("Europe/Lisbon")
    private val here = 38.72 to -9.14

    /** 9 de novembro de 2026 às [hour]:[minute] em Lisboa. */
    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance(lisbon).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 9, hour, minute)
    }.timeInMillis

    /** Os minutos a passar, um de cada vez (no ecrã há um minuto entre cada um; aqui, o suficiente para o combine os ver). */
    private fun ticks(vararg times: Long) = flow {
        for (t in times) {
            emit(t)
            delay(5)
        }
    }

    private fun clock(account: AccountSettings, location: Pair<Double, Double>?, vararg times: Long) =
        NightClock(flowOf(account), flowOf(location), ticks(*times), zone = { lisbon })

    @Test
    fun withoutAutoTheButtonDecides() = runBlocking {
        assertEquals(listOf(true), clock(AccountSettings(nightMode = true), here, at(12)).night.toList())
        assertEquals(listOf(false), clock(AccountSettings(nightMode = false), here, at(23)).night.toList())
    }

    @Test
    fun autoWithoutLocationIsNightFrom20To7() = runBlocking {
        val auto = AccountSettings(nightAuto = true)
        assertEquals(listOf(false, true, false), clock(auto, null, at(19, 59), at(20), at(23), at(6, 59), at(7)).night.toList())
    }

    @Test
    fun autoWithLocationFollowsSunsetAndSunrise() = runBlocking {
        val sun = SunTimes.of(here.first, here.second, at(12), lisbon)!!
        val sunset = sun.second
        val auto = AccountSettings(nightAuto = true)
        val night = clock(auto, here, at(sunset / 60, sunset % 60 - 1), at(sunset / 60, sunset % 60)).night.toList()
        // Um minuto antes do pôr do sol ainda é dia; no minuto dele já é noite.
        assertEquals(listOf(false, true), night)
    }

    @Test
    fun sunTimesNeedALocationAndFollowIt() = runBlocking {
        val location = MutableStateFlow<Pair<Double, Double>?>(null)
        val clock = NightClock(flowOf(AccountSettings()), location, flowOf(at(12)), zone = { lisbon })
        assertNull(clock.sunTimes.first())
        location.value = here
        assertEquals(SunTimes.of(here.first, here.second, at(12), lisbon), clock.sunTimes.first())
        assertNotNull(clock.sunTimes.first())
    }

    @Test
    fun sunTimesAreRecomputedOncePerHour() = runBlocking {
        var computed = 0
        val clock = NightClock(flowOf(AccountSettings()), flowOf(here), ticks(at(12), at(12, 1), at(12, 59), at(13)), zone = { lisbon })
        clock.sunTimes.collect { computed++ }
        assertEquals(2, computed)
    }
}
