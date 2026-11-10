package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGridTest {
    private val order = listOf("a", "b", "c", "d", "e", "f", "g", "h")

    @Test
    fun aGrelhaSaoOsFavoritosVisiveisDepoisOResto() {
        // "b" escondida: os favoritos são os 5 primeiros visíveis; "b" fica logo a seguir, com as outras.
        assertEquals(listOf("a", "c", "d", "e", "f", "b", "g", "h"), AppGrid.of(order, setOf("b")))
    }

    @Test
    fun moverChegaAsDoMeioEPrendeNasPontas() {
        assertEquals(listOf("b", "a", "c", "d", "e", "f", "g", "h"), AppGrid.move(order, emptySet(), "a", 1)!!.first)
        assertEquals(listOf("b", "c", "d", "e", "f", "g", "h", "a"), AppGrid.move(order, emptySet(), "a", 99)!!.first)
        assertEquals(listOf("h", "a", "b", "c", "d", "e", "f", "g"), AppGrid.move(order, emptySet(), "h", -99)!!.first)
        assertNull(AppGrid.move(order, emptySet(), "zz", 1))
    }

    @Test
    fun umaEscondidaQueEntraNosFavoritosVoltaAAparecer() {
        val (apps, hidden) = AppGrid.move(order, setOf("g"), "g", -6)!!
        assertEquals(listOf("g", "a", "b", "c", "d", "e", "f", "h"), apps)
        assertTrue(hidden.isEmpty())
    }

    @Test
    fun tirarDosFavoritosDesceParaOInicioDaListaDeBaixo() {
        // O mesmo que o ecrã fazia: delta = FAVORITES - posição.
        val (apps, _) = AppGrid.unfavorite(order, emptySet(), "b")!!
        assertEquals(listOf("a", "c", "d", "e", "f", "b", "g", "h"), apps)
        assertEquals(apps, AppGrid.move(order, emptySet(), "b", AppGrid.FAVORITES - 1)!!.first)
    }

    @Test
    fun porNosFavoritosEntraNoUltimoLugar() {
        val (apps, _) = AppGrid.favorite(order, emptySet(), "g")!!
        assertEquals(listOf("a", "b", "c", "d", "g", "e", "f", "h"), apps)
        // Com menos de 5 apps visíveis, entra a seguir à última.
        val few = listOf("a", "b", "c")
        assertEquals(listOf("a", "c", "b"), AppGrid.favorite(few, setOf("b"), "b")!!.first)
    }

    @Test
    fun soSeTiraUmFavoritoSeHouverQuemEntre() {
        assertFalse(AppGrid.canUnfavorite(AppGrid.FAVORITES))
        assertTrue(AppGrid.canUnfavorite(AppGrid.FAVORITES + 1))
    }
}
