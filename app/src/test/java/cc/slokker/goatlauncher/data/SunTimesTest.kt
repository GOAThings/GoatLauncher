package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Nascer e pôr do sol comparados com as tabelas (timeanddate.com), com 5 minutos de folga. */
class SunTimesTest {
    private val lisbon = TimeZone.getTimeZone("Europe/Lisbon")

    private fun at(year: Int, month: Int, day: Int, zone: TimeZone) =
        Calendar.getInstance(zone).apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    private fun hm(h: Int, m: Int) = h * 60 + m

    @Test
    fun lisboaNoVerao() {
        val (rise, set) = SunTimes.of(38.72, -9.14, at(2026, 6, 21, lisbon), lisbon)!!
        assertEquals(hm(6, 12).toDouble(), rise.toDouble(), 5.0)
        assertEquals(hm(21, 5).toDouble(), set.toDouble(), 5.0)
    }

    @Test
    fun lisboaNoInverno() {
        val (rise, set) = SunTimes.of(38.72, -9.14, at(2026, 12, 21, lisbon), lisbon)!!
        assertEquals(hm(7, 51).toDouble(), rise.toDouble(), 5.0)
        assertEquals(hm(17, 18).toDouble(), set.toDouble(), 5.0)
    }

    @Test
    fun saoPaulo() {
        val zone = TimeZone.getTimeZone("America/Sao_Paulo")
        val (rise, set) = SunTimes.of(-23.55, -46.63, at(2026, 6, 21, zone), zone)!!
        assertEquals(hm(6, 47).toDouble(), rise.toDouble(), 5.0)
        assertEquals(hm(17, 28).toDouble(), set.toDouble(), 5.0)
    }

    @Test
    fun solDaMeiaNoite() {
        val zone = TimeZone.getTimeZone("Europe/Oslo")
        assertNull(SunTimes.of(69.65, 18.96, at(2026, 6, 21, zone), zone))
    }

    @Test
    fun noite() {
        val sun = hm(7, 0) to hm(19, 30)
        assertTrue(SunTimes.isNight(hm(6, 59), sun))
        assertFalse(SunTimes.isNight(hm(12, 0), sun))
        assertTrue(SunTimes.isNight(hm(19, 30), sun))
    }
}
