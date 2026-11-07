package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** O programa de um canal em direto (GoatTV) no "Continuar a ver": o atual, ou o seguinte quando o atual já acabou. */
class OnAirTest {
    private fun channel(airing: String?, start: Long, end: Long, next: String?, channel: Boolean = true) = WatchNextItem(
        id = 1, packageName = "cc.slokker.itv", title = "Canal 1", episodeTitle = null, season = null, episode = null,
        imageUri = null, aspectRatio = 16f / 9f, positionMs = 0, durationMs = 0, type = 0, lastEngagement = 0, intentUri = null,
        isChannel = channel, airing = airing, airStart = start, airEnd = end, upNext = next,
    )

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
    fun withoutTimesTheProgrammeStays() {
        val air = channel("Jornal", 0, 0, null).onAir(99_000)!!
        assertEquals("Jornal", air.title)
        assertNull(air.progress)
        assertNull(air.endsAt)
    }

    @Test
    fun nothingForEndedOrNonChannels() {
        assertNull(channel("Jornal", 1_000, 3_000, null).onAir(4_000))
        assertNull(channel(null, 1_000, 3_000, "Entrevista").onAir(2_000))
        assertNull(channel("Sinopse", 1_000, 3_000, null, channel = false).onAir(2_000))
    }
}
