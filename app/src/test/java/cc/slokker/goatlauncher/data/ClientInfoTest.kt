package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Como o launcher se identifica: o site (X-Goat-Client, relatórios) e o Wikimedia contam com estes textos. */
class ClientInfoTest {
    @Test
    fun theBackendAndPublicIdentifications() {
        val client = ClientInfo("0.9.11", "android-tv")
        assertEquals("goatlauncher/0.9.11 (android-tv)", client.goatClient)
        assertEquals("goatlauncher/0.9.11 (android-tv; +https://github.com/GOAThings/GoatLauncher)", client.publicUserAgent)
        assertEquals("goatlauncher", ClientInfo.APP)
    }

    @Test
    fun amazonTvsAreFireTv() {
        assertEquals("fire-tv", ClientInfo.platformOf("Amazon"))
        assertEquals("fire-tv", ClientInfo.platformOf("amazon"))
        assertEquals("android-tv", ClientInfo.platformOf("NVIDIA"))
        assertEquals("android-tv", ClientInfo.platformOf(null))
    }
}
