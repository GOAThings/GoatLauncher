package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Os limites do servidor para os banners e ícones das apps (1024×1024; 400 `icon_too_big`). */
class AppIndexTest {
    @Test
    fun cabeSemMudar() {
        assertEquals(480 to 270, fitIconSize(480, 270))
        assertEquals(192 to 192, fitIconSize(192, 192))
        assertEquals(1024 to 1024, fitIconSize(1024, 1024))
    }

    @Test
    fun reduzSemMudarAProporcao() {
        assertEquals(1024 to 576, fitIconSize(1920, 1080))
        assertEquals(512 to 1024, fitIconSize(1000, 2000))
        assertEquals(1024 to 1024, fitIconSize(4096, 4096))
        // Muito estreita: nunca 0 px.
        assertEquals(1024 to 1, fitIconSize(100_000, 10))
    }

    @Test
    fun iconeGrandeDemais() {
        assertTrue(isIconTooBig(400, """{"status":400,"code":"icon_too_big","message":"Icon too big.","data":{}}"""))
        assertTrue(isIconTooBig(400, """{"message":"Icon too big.","data":{"code":"icon_too_big"}}"""))
    }

    @Test
    fun outrosErros() {
        assertFalse(isIconTooBig(400, """{"code":"icon_hash_mismatch"}"""))
        assertFalse(isIconTooBig(413, """{"code":"icon_too_big"}"""))
        assertFalse(isIconTooBig(400, null))
        assertFalse(isIconTooBig(400, "<html>Bad request</html>"))
        assertFalse(isIconTooBig(500, ""))
    }
}
