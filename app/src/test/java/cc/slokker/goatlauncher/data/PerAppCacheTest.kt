package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PerAppCacheTest {
    private class Image

    @Test
    fun computesOncePerImageAndKeepsOnlyInstalledApps() {
        var computed = 0
        val cache = PerAppCache<Image, Int> { computed++; computed }
        val a = Image()
        assertEquals(1, cache.get("a", a))
        assertEquals(1, cache.get("a", a))
        assertEquals(1, computed)
        // Outra imagem para a mesma app (atualizada): calcula outra vez e substitui.
        assertEquals(2, cache.get("a", Image()))
        assertEquals(1, cache.size)
        cache.get("b", Image())
        cache.retain(listOf("b"))
        assertEquals(1, cache.size)
        assertEquals(4, cache.get("a", a))
    }
}
