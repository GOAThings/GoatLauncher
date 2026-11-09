package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** As horas do controlo parental (Definições → Controlo parental → Só a certas horas). */
class ParentalLockTest {
    private fun lockedHours(from: Int?, to: Int?) = (0..23).filter { ParentalLock.inWindow(from, to, it) }

    @Test
    fun withoutHoursAlwaysLocked() {
        assertEquals((0..23).toList(), lockedHours(null, null))
        assertEquals((0..23).toList(), lockedHours(20, null))
    }

    @Test
    fun aWindowInTheDay() {
        assertEquals((9..16).toList(), lockedHours(9, 17))
    }

    @Test
    fun aWindowAcrossMidnight() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 20, 21, 22, 23), lockedHours(20, 8))
        assertEquals(listOf(23), lockedHours(23, 0))
    }

    @Test
    fun equalHoursAreTheWholeDay() {
        // "Bloqueadas das 20:00 às 20:00": antes não bloqueava a hora nenhuma, e as apps abriam sem código.
        for (h in 0..23) assertEquals("$h → $h", (0..23).toList(), lockedHours(h, h))
    }
}
