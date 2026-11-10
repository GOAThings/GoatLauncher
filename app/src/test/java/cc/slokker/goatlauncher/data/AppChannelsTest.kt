package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppChannelsTest {
    private fun item(id: Long, pkg: String, intent: String = "u$id") = WatchNextItem(
        id = id, packageName = pkg, title = "t$id", episodeTitle = null, season = null, episode = null, imageUri = null,
        aspectRatio = 1f, positionMs = 0, durationMs = 0, type = WatchNextItem.TYPE_RECOMMENDED, lastEngagement = 0,
        intentUri = intent,
    )

    @Test
    fun soEnchemAsAppsSemWatchNext() {
        val watchNext = mapOf("a" to listOf(item(1, "a")))
        val previews = listOf(item(10, "a"), item(11, "b"), item(12, "b"))
        val all = WatchNextRows.withChannels(watchNext, previews)
        // "a" tem Watch Next: fica só com ele. "b" fica com os programas dos canais, pela ordem da app.
        assertEquals(listOf(1L), all.getValue("a").map { it.id })
        assertEquals(listOf(11L, 12L), all.getValue("b").map { it.id })
    }

    @Test
    fun semCanaisFicaOWatchNextTalComoEra() {
        val watchNext = mapOf("a" to listOf(item(1, "a")))
        assertSame(watchNext, WatchNextRows.withChannels(watchNext, emptyList()))
    }

    @Test
    fun repetidosSaemENaoPassamDoMaximoPorApp() {
        // O mesmo programa em dois canais da app aparece uma vez.
        val previews = listOf(item(1, "b", "x"), item(2, "b", "x")) + (3L..100L).map { item(it, "b") }
        val b = WatchNextRows.withChannels(emptyMap(), previews).getValue("b")
        assertEquals(WatchNextRows.MAX_PER_APP, b.size)
        assertEquals(1, b.count { it.intentUri == "x" })
    }
}
