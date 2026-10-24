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
        assertTrue(isCornerHit(hitX = true, hitY = true, x = 0f, y = 0f, maxX, maxY, 0f))
        assertTrue(isCornerHit(hitX = true, hitY = true, x = maxX, y = maxY, maxX, maxY, 0f))
    }

    @Test
    fun quaseCantoNaoConta() {
        // Perto não conta, nem a 1 px.
        assertFalse(isCornerHit(hitX = true, hitY = false, x = 0f, y = 17f, maxX, maxY, 0f))
        assertFalse(isCornerHit(hitX = true, hitY = false, x = 0f, y = 1f, maxX, maxY, 0f))
        assertFalse(isCornerHit(hitX = false, hitY = true, x = maxX - 1f, y = maxY, maxX, maxY, 0f))
    }

    @Test
    fun bordaNormalNaoConta() {
        assertFalse(isCornerHit(hitX = true, hitY = false, x = 0f, y = 400f, maxX, maxY, 0f))
        assertFalse(isCornerHit(hitX = false, hitY = false, x = 0f, y = 0f, maxX, maxY, 0f))
    }

    /** Simula o logótipo a andar uma hora a 60 fps: os cantos detetados são todos o canto exato. */
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
            if (isCornerHit(hitX, hitY, x, y, maxX, maxY, 0f)) {
                corners++
                assertTrue("não é o canto: $x, $y", (x == 0f || x == maxX) && (y == 0f || y == maxY))
            }
        }
        // Raro mas acontece (senão o easter egg nunca se via).
        assertEquals(true, corners in 0..200)
    }
}
