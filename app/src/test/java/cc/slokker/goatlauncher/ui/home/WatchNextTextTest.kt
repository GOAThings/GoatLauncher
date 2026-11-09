package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.data.Programme
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.ui.state.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Test

/** A linha de detalhes dos cartões do "Continuar a ver": as três maneiras (cartão, Apple TV, destaque). */
class WatchNextTextTest {
    private val s = TestStrings.create()

    private fun item(
        title: String = "Série",
        episodeTitle: String? = null,
        season: String? = null,
        episode: String? = null,
        positionMs: Long = 0,
        durationMs: Long = 0,
        channel: Boolean = false,
        airing: String? = null,
        airEnd: Long = 0,
        schedule: List<Programme> = emptyList(),
    ) = WatchNextItem(
        id = 1, packageName = "p", title = title, episodeTitle = episodeTitle, season = season, episode = episode,
        imageUri = null, aspectRatio = 16f / 9f, positionMs = positionMs, durationMs = durationMs, type = 0,
        lastEngagement = 0, intentUri = null, isChannel = channel, airing = airing, airStart = if (airing != null) 1 else 0,
        airEnd = airEnd, schedule = schedule,
    )

    private fun line(item: WatchNextItem, style: DetailsStyle, now: Long = 0) = detailsLine(item, s, now, style)

    @Test
    fun seasonAndEpisodeComeFirstAndTheTimeLeftAfter() {
        val i = item(season = "2", episode = "5", episodeTitle = "Piloto", positionMs = 60_000, durationMs = 1_260_000)
        val se = s.seasonEpisode("2", "5")
        val left = s.minutesLeft(20)
        assertEquals("$se · $left", line(i, DetailsStyle.Card))
        assertEquals("$se · $left", line(i, DetailsStyle.TopShelf))
        // O destaque mostra também o nome do episódio.
        assertEquals("$se · Piloto · $left", line(i, DetailsStyle.Hero))
    }

    @Test
    fun withoutSeasonTheEpisodeTitleTakesItsPlace() {
        val i = item(episodeTitle = "Piloto")
        assertEquals("Piloto", line(i, DetailsStyle.Card))
        assertEquals("Piloto", line(i, DetailsStyle.TopShelf))
        assertEquals("Piloto", line(i, DetailsStyle.Hero))
    }

    @Test
    fun anEpisodeTitleEqualToTheTitleIsOnlyShownOnSmallCards() {
        val i = item(title = "Filme", episodeTitle = "Filme")
        assertEquals("Filme", line(i, DetailsStyle.Card))
        assertEquals("", line(i, DetailsStyle.TopShelf))
        assertEquals("", line(i, DetailsStyle.Hero))
        assertEquals(emptyList<String>(), detailsParts(i, s, 0, DetailsStyle.TopShelf))
    }

    @Test
    fun aLiveChannelShowsWhatIsOnAtThatMinute() {
        val i = item(channel = true, airing = "Jornal", airEnd = 1_000, schedule = listOf(Programme("Filme", 1_000)))
        assertEquals("Jornal", line(i, DetailsStyle.Card, now = 500))
        assertEquals("Filme", line(i, DetailsStyle.Hero, now = 1_500))
        assertEquals("Filme", line(i, DetailsStyle.TopShelf, now = 1_500))
    }

    @Test
    fun nothingFinishedOrUnknownIsLeftOut() {
        // Visto até ao fim (0 min) e sem duração: não há "faltam".
        assertEquals("", line(item(positionMs = 1_000, durationMs = 1_000), DetailsStyle.Card))
        assertEquals("", line(item(positionMs = 1_000), DetailsStyle.Card))
    }
}
