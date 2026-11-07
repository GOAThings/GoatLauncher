package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/** O programa de um canal em direto (GoatTV) no "Continuar a ver": o atual, ou o seguinte quando o atual já acabou. */
class OnAirTest {
    private fun channel(airing: String?, start: Long, end: Long, next: String?, channel: Boolean = true) =
        channel(airing, start, end, listOfNotNull(next?.let { Programme(it, end) }), channel)

    private fun channel(airing: String?, start: Long, end: Long, schedule: List<Programme>, channel: Boolean = true) = WatchNextItem(
        id = 1, packageName = "cc.slokker.itv", title = "Canal 1", episodeTitle = null, season = null, episode = null,
        imageUri = null, aspectRatio = 16f / 9f, positionMs = 0, durationMs = 0, type = 0, lastEngagement = 0, intentUri = null,
        isChannel = channel, airing = airing, airStart = start, airEnd = end, schedule = schedule,
    )

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun duringTheProgramme() {
        val air = channel("Jornal", 1_000, 3_000, "Entrevista").onAir(2_000)!!
        assertEquals("Jornal", air.title)
        assertEquals(0.5f, air.progress!!, 0.001f)
        assertEquals(3_000L, air.endsAt)
        assertEquals("Entrevista", air.next)
    }

    @Test
    fun afterItEndsTheNextOneIsOn() {
        val air = channel("Jornal", 1_000, 3_000, "Entrevista").onAir(3_500)!!
        assertEquals("Entrevista", air.title)
        assertNull(air.progress)
        assertNull(air.next)
    }

    @Test
    fun afterItEndsTheScheduleCarriesOn() {
        val item = channel("Jornal", 1_000, 3_000, listOf(Programme("Entrevista", 3_000), Programme("Filme", 5_000), Programme("Notícias", 9_000)))
        val air = item.onAir(4_000)!!
        assertEquals("Entrevista", air.title)
        assertEquals(0.5f, air.progress!!, 0.001f)
        assertEquals(5_000L, air.endsAt)
        assertEquals("Filme", air.next)
        assertEquals(listOf("Filme", "Notícias"), item.upcoming(4_000).map { it.title })
    }

    @Test
    fun withoutTimesTheProgrammeStays() {
        val air = channel("Jornal", 0, 0, null as String?).onAir(99_000)!!
        assertEquals("Jornal", air.title)
        assertNull(air.progress)
        assertNull(air.endsAt)
    }

    @Test
    fun nothingForEndedOrNonChannels() {
        assertNull(channel("Jornal", 1_000, 3_000, null as String?).onAir(4_000))
        assertNull(channel(null, 1_000, 3_000, "Entrevista").onAir(2_000))
        assertNull(channel("Sinopse", 1_000, 3_000, null as String?, channel = false).onAir(2_000))
    }

    @Test
    fun scheduleFromTheLongDescription() {
        val end = at(7, 22, 15)
        val list = parseSchedule("Entrevista\n23:00 Filme da noite\n00:40 Notícias", end, 0)
        assertEquals(listOf("Entrevista", "Filme da noite", "Notícias"), list.map { it.title })
        assertEquals(listOf(end, at(7, 23, 0), at(8, 0, 40)), list.map { it.startMs })
    }

    @Test
    fun anOldGoatTvOnlySendsTheNextTitle() {
        assertEquals(listOf(Programme("Entrevista: 23:00 em direto", 5L)), parseSchedule("Entrevista: 23:00 em direto", 5L, 0))
        assertEquals(emptyList<Programme>(), parseSchedule("  ", 5L, 0))
    }
}
