package cc.slokker.goatlauncher.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A moldura preta das imagens do fundo (ver ImageFrame), em imagens feitas aqui, do tamanho da cópia que se mede. */
class ImageFrameTest {
    private val black = 0xFF080808.toInt()
    private val bright = 0xFFD8B040.toInt()

    /** Uma imagem [w]×[h]: [pixel] dá a cor de cada (x, y). */
    private fun image(w: Int, h: Int, pixel: (Int, Int) -> Int) = IntArray(w * h) { pixel(it % w, it / w) }

    private fun framed(w: Int, h: Int, pixel: (Int, Int) -> Int) = ImageFrame.isFramed(image(w, h, pixel), w, h)

    @Test
    fun aTriptychWithABlackFrameIsFramed() {
        // Como a "Trilogie Hexentanz": moldura de 6% em cima e em baixo, 3% dos lados, e duas divisórias pretas.
        assertTrue(framed(256, 119) { x, y ->
            val frame = y < 7 || y >= 112 || x < 7 || x >= 249 || x in 85..89 || x in 166..170
            if (frame) black else bright
        })
    }

    @Test
    fun letterboxAndPillarboxAreFramed() {
        assertTrue(framed(256, 192) { _, y -> if (y < 24 || y >= 168) black else bright })
        assertTrue(framed(256, 144) { x, _ -> if (x < 30 || x >= 226) black else bright })
    }

    @Test
    fun anOrdinaryPhotoIsNot() {
        assertFalse(framed(256, 171) { x, y -> if ((x + y) % 7 == 0) 0xFF203040.toInt() else bright })
    }

    @Test
    fun aDarkSkyOnOneSideIsNot() {
        // O céu de noite por cima de uma cidade: só um lado escuro.
        assertFalse(framed(256, 144) { _, y -> if (y < 50) black else bright })
    }

    @Test
    fun spaceThatFadesInIsNot() {
        // Uma galáxia: escuro em todas as pontas, mas clareia aos poucos para o centro (sem corte).
        assertFalse(framed(256, 144) { x, y ->
            val dx = (x - 128) / 128.0
            val dy = (y - 72) / 72.0
            val v = (255 * (1 - minOf(1.0, kotlin.math.sqrt(dx * dx + dy * dy)))).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        })
    }

    @Test
    fun aThinLineAtTheEdgeIsNot() {
        // Uma linha de um píxel (menos de 1% do lado): o rebordo de uma digitalização, não uma moldura.
        assertFalse(framed(256, 171) { _, y -> if (y == 0 || y == 170) black else bright })
    }

    @Test
    fun aDarkPhotoIsNot() {
        assertFalse(framed(256, 144) { _, _ -> black })
    }
}
