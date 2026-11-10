package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScreenTimeTest {
    private val parental = DeviceSettings(pin = "salt:hash", dailyLimit = 60)

    @Test
    fun oTempoSomaNoMesmoDiaEComecaDoZeroNoSeguinte() {
        val day = ScreenTimeDay().add("2026-11-10", 30_000L).add("2026-11-10", 30_000L)
        assertEquals(60_000L, day.usedOn("2026-11-10"))
        assertEquals(0L, day.usedOn("2026-11-11"))
        assertEquals(ScreenTimeDay("2026-11-11", 30_000L), day.add("2026-11-11", 30_000L))
    }

    @Test
    fun oDiaEODaHoraLocal() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 5, 23, 59) }
        assertEquals("2026-01-05", ScreenTimeDay.dayOf(c))
    }

    @Test
    fun acabaQuandoChegaAoLimiteESoComCodigo() {
        assertFalse(ParentalLock.timeUp(parental, 59 * 60_000L))
        assertTrue(ParentalLock.timeUp(parental, 60 * 60_000L))
        assertFalse(ParentalLock.timeUp(parental.copy(dailyLimit = null), 10 * 60 * 60_000L))
        assertFalse(ParentalLock.timeUp(parental.copy(pin = null), 10 * 60 * 60_000L))
    }

    @Test
    fun comOTempoAcabadoTodasAsAppsPedemOCodigo() {
        // Nenhuma app bloqueada: só o tempo acabado pede o código.
        assertFalse(ParentalLock.appLocked(parental, "a"))
        assertTrue(ParentalLock.appLocked(parental, "a", timeUp = true))
        assertFalse(ParentalLock.appLocked(parental.copy(pin = null), "a", timeUp = true))
    }

    @Test
    fun oLimiteLeSeDentroDeUmDia() {
        fun limit(json: String) = DeviceSettings.fromJson(com.google.gson.JsonParser.parseString(json).asJsonObject).dailyLimit
        assertEquals(90, limit("""{"time_limit":90}"""))
        assertEquals(null, limit("""{"time_limit":0}"""))
        assertEquals(null, limit("""{"time_limit":5000}"""))
        assertEquals(null, limit("""{"time_limit":"x"}"""))
        // O valor de cada opção das Definições lê-se de volta igual (0 = sem limite, não se grava).
        for (m in DeviceSettings.DAILY_LIMITS) {
            val d = DeviceSettings(dailyLimit = m.takeIf { it > 0 })
            assertEquals(d.dailyLimit, DeviceSettings.fromJson(d.toJson()).dailyLimit)
        }
    }
}
