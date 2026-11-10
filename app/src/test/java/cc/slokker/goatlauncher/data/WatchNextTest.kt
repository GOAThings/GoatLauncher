package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextTest {
    private fun item(id: Long, pkg: String = "pub.app", engagement: Long = id) = WatchNextItem(
        id = id, packageName = pkg, title = "t$id", episodeTitle = null, season = null, episode = null, imageUri = null,
        aspectRatio = 1f, positionMs = 0, durationMs = 0, type = 0, lastEngagement = engagement, intentUri = "u$id",
    )

    @Test
    fun aCardOnlyOpensItsPublisher() {
        val card = item(1)
        assertTrue(card.targetsPublisher(null, null))
        assertTrue(card.targetsPublisher("pub.app", null))
        assertTrue(card.targetsPublisher(null, "pub.app"))
        assertTrue(card.targetsPublisher("pub.app", "pub.app"))
        assertFalse(card.targetsPublisher("other.app", null))
        assertFalse(card.targetsPublisher(null, "other.app"))
        assertFalse(card.targetsPublisher("pub.app", "other.app"))
        assertFalse(card.targetsPublisher("other.app", "pub.app"))
    }

    @Test
    fun anImplausibleDurationShowsNoProgressOrTimeLeft() {
        val episode = item(1).copy(positionMs = 10 * 60_000, durationMs = 22 * 60_000)
        assertEquals(12L, episode.remainingMinutes)
        assertEquals(10f / 22, episode.progress!!, 0.001f)
        // O Nuvio deu um episódio com 33 h: nem barra nem "28 h a faltar".
        val broken = item(2).copy(positionMs = 4 * 3_600_000L, durationMs = 33 * 3_600_000L)
        assertEquals(null, broken.remainingMinutes)
        assertEquals(null, broken.progress)
    }

    private fun read(rows: List<() -> WatchNextItem?>, moveTo: (Int) -> Boolean = { true }, onBadRow: (Throwable) -> Unit = {}): List<WatchNextItem> {
        var position = -1
        return WatchNextRows.read(rows.size, { position = it; moveTo(it) }, { rows[position]() }, onBadRow)
    }

    @Test
    fun aRowThatFailsSkipsOnlyThatRow() {
        var bad = 0
        val out = read(
            listOf({ item(1) }, { throw IllegalStateException("blob") }, { item(3, pkg = "other.app") }),
            onBadRow = { bad++ },
        )
        assertEquals(setOf(1L, 3L), out.map { it.id }.toSet())
        assertEquals(1, bad)
    }

    @Test
    fun aPositionThatFailsSkipsOnlyThatRow() {
        val out = read(listOf({ item(1) }, { item(2) }, { item(3) }), moveTo = { if (it == 1) error("window") else true })
        assertEquals(setOf(1L, 3L), out.map { it.id }.toSet())
    }

    @Test
    fun oneAppWithManyRowsKeepsItsNewestAndDoesNotPushOutOthers() {
        val flood = (1L..3000L).map { id -> { item(id, pkg = "flood.app") } }
        val rows = listOf<() -> WatchNextItem?>({ item(1, pkg = "first.app", engagement = 0) }) + flood + { item(2, pkg = "last.app", engagement = 0) }
        val all = WatchNextRows.newestByApp(read(rows))
        assertEquals(WatchNextRows.MAX_PER_APP, all.getValue("flood.app").size)
        assertEquals(3000L, all.getValue("flood.app").first().id)
        assertEquals(listOf(1L), all.getValue("first.app").map { it.id })
        assertEquals(listOf(2L), all.getValue("last.app").map { it.id })
    }

    @Test
    fun readsNoMoreThanTheLimit() {
        var moves = 0
        WatchNextRows.read(WatchNextRows.MAX_SCANNED * 2, { moves++; false }, { null }, {})
        assertEquals(WatchNextRows.MAX_SCANNED, moves)
    }

    @Test
    fun totalIsCappedNewestFirstAndRepeatsAreDropped() {
        val rows = (1L..1000L).map { id -> item(id, pkg = "app${id % 100}") }
        val all = WatchNextRows.newestByApp(rows)
        assertEquals(WatchNextRows.MAX_TOTAL, all.values.sumOf { it.size })
        assertEquals(true, all.values.flatten().all { it.id > 600 })
        val repeated = WatchNextRows.newestByApp(listOf(item(1), item(2).copy(intentUri = "u1")))
        assertEquals(listOf(2L), repeated.getValue("pub.app").map { it.id })
    }
}
