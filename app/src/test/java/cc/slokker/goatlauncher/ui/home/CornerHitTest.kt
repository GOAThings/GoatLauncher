package cc.slokker.goatlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A cabra do protetor de ecrã DVD só aparece quando o logótipo acerta mesmo num canto. */
class CornerHitTest {
    private val maxX = 1500f
    private val maxY = 800f

    @Test
    fun cantoExato() {
        assertTrue(isCornerHit(hitX = true, hitY = true, x = 0f, y = 0f, maxX, maxY, 4f))
        assertTrue(isCornerHit(hitX = true, hitY = false, x = maxX, y = maxY - 3f, maxX, maxY, 4f))
    }

    @Test
    fun quaseCantoNaoConta() {
        // 17 px do canto (o que a regra antiga, por tempo, aceitava): não.
        assertFalse(isCornerHit(hitX = true, hitY = false, x = 0f, y = 17f, maxX, maxY, 4f))
        assertFalse(isCornerHit(hitX = false, hitY = true, x = maxX - 20f, y = maxY, maxX, maxY, 4f))
    }

    @Test
    fun bordaNormalNaoConta() {
        assertFalse(isCornerHit(hitX = true, hitY = false, x = 0f, y = 400f, maxX, maxY, 4f))
        assertFalse(isCornerHit(hitX = false, hitY = false, x = 0f, y = 0f, maxX, maxY, 4f))
    }

    /** Simula o logótipo a andar uma hora a 60 fps: os cantos detetados ficam todos a menos da tolerância. */
    @Test
    fun simulacao() {
        var x = 37f
        var y = 11f
        var vx = 140f
        var vy = 105f
        var corners = 0
        repeat(60 * 60 * 60) {
            val dt = 1f / 60
            x += vx * dt
            y += vy * dt
            val hitX = x <= 0f || x >= maxX
            val hitY = y <= 0f || y >= maxY
            if (hitX) { vx = -vx; x = x.coerceIn(0f, maxX) }
            if (hitY) { vy = -vy; y = y.coerceIn(0f, maxY) }
            if (isCornerHit(hitX, hitY, x, y, maxX, maxY, 4f)) {
                corners++
                val dx = minOf(x, maxX - x)
                val dy = minOf(y, maxY - y)
                assertTrue("canto longe: $dx, $dy", dx <= 4f && dy <= 4f)
            }
        }
        // Raro mas acontece (senão o easter egg nunca se via).
        assertEquals(true, corners in 0..200)
    }
}
