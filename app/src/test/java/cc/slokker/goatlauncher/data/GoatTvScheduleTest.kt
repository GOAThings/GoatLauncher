package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A descrição longa que o GoatTV publica no Watch Next de um canal ([GoatTvContract]), lida pelo [parseSchedule].
 * O texto é feito por uma cópia literal do produtor do GoatTV: se um dos lados mudar, muda-se o outro e esta cópia.
 */
class GoatTvScheduleTest {
    /** Só os campos do EpgEntry do GoatTV que o scheduleText usa. */
    private class EpgEntry(val title: String, val startMs: Long)

    // Cópia literal de `scheduleText` em itv/app/src/main/java/cc/slokker/itv/data/HomeScreen.kt (linhas 104-109,
    // GoatTV @ 60fcc97). O GoatTV manda os HOME_UPCOMING = 4 programas depois do atual (itv ui/AppViewModel.kt 1871).
    private fun scheduleText(upcoming: List<EpgEntry>): String {
        val first = upcoming.firstOrNull() ?: return ""
        val clock = SimpleDateFormat("HH:mm", Locale.ROOT)
        return (listOf(first.title) + upcoming.drop(1).map { "${clock.format(Date(it.startMs))} ${it.title}" })
            .joinToString("\n") { it.replace('\n', ' ') }
    }

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val upcoming = listOf(
        EpgEntry("Telejornal", at(9, 21, 0)),
        EpgEntry("Futebol: Benfica\nPorto", at(9, 21, 45)),
        EpgEntry("Filme da noite", at(9, 23, 30)),
        EpgEntry("Notícias", at(10, 1, 10)),
    )

    @Test
    fun goldenString() {
        assertEquals("Telejornal\n21:45 Futebol: Benfica Porto\n23:30 Filme da noite\n01:10 Notícias", scheduleText(upcoming))
    }

    @Test
    fun theLauncherReadsWhatGoatTvWrites() {
        // O primeiro começa quando o programa atual acaba (end_time_utc_millis), às 21:00.
        val read = parseSchedule(scheduleText(upcoming), nextStart = at(9, 21, 0), now = at(9, 20, 30))
        assertEquals(
            listOf(
                Programme("Telejornal", at(9, 21, 0)),
                Programme("Futebol: Benfica Porto", at(9, 21, 45)),
                Programme("Filme da noite", at(9, 23, 30)),
                // Passa da meia-noite: é no dia seguinte.
                Programme("Notícias", at(10, 1, 10)),
            ),
            read,
        )
        assertEquals(true, scheduleText(upcoming).length <= GoatTvContract.MAX_SCHEDULE)
    }

    @Test
    fun olderAndEmptySchedules() {
        // Um GoatTV antigo só punha o título do seguinte.
        assertEquals(listOf(Programme("Telejornal", at(9, 21, 0))), parseSchedule(scheduleText(upcoming.take(1)), at(9, 21, 0), at(9, 20, 0)))
        // Sem guia não há descrição longa (o launcher lê "" como nada).
        assertEquals("", scheduleText(emptyList()))
        assertEquals(emptyList<Programme>(), parseSchedule("", at(9, 21, 0), at(9, 20, 0)))
    }

    @Test
    fun onlyGoatTvBuildsCarryASchedule() {
        // Nas outras apps a descrição longa é uma sinopse, nunca uma lista de programas.
        assertEquals(true, "cc.slokker.itv" in GoatTvContract.PACKAGES)
        assertEquals(true, "cc.slokker.itv.debug" in GoatTvContract.PACKAGES)
        assertEquals(false, "com.netflix.ninja" in GoatTvContract.PACKAGES)
    }
}
