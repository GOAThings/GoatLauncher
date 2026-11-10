package cc.slokker.goatlauncher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A tabela de caminhos da porta 6470 com as interfaces da app (AppOpener, Volume, os PNG do AppCatalog). O contrato com o
 * GoatRemote está no PhoneApiTest; aqui ficam o que cada OpenResult responde, os caminhos parecidos que não existem, e os
 * PNG partilhados com o índice do site.
 */
class PhoneRoutesTest {
    private class Image(override val width: Int, override val height: Int) : ImageBitmap {
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override val hasAlpha: Boolean get() = true
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit
        override fun prepareToDraw() = Unit
    }

    private val netflix = LauncherApp("com.netflix.ninja", "Netflix", Image(480, 270), null, Color(0xFF1C2333), null)
    private var encoded = 0
    private val pngs = PerAppCache<ImageBitmap, ByteArray> { encoded++; "png".toByteArray() }
    private var result = OpenResult.OPENED
    private val opened = mutableListOf<String>()
    private val routes = PhoneRoutes(
        apps = { listOf(netflix) },
        volume = { },
        opener = { pkg -> result.also { if (it == OpenResult.OPENED) opened += pkg } },
        pngs = pngs,
    )

    private fun call(method: String, path: String): LanReply {
        val raw = "$method $path HTTP/1.1\r\nHost: 192.168.1.20:6470\r\n\r\n"
        return runBlocking { routes.reply(requireNotNull(LanRequest.read(raw.byteInputStream()))) }
    }

    @Test
    fun eachOpenResultHasItsCode() {
        assertEquals("200 OK", call("POST", "/open/com.netflix.ninja").status)
        result = OpenResult.NOT_FOUND
        assertEquals("404 Not Found", call("POST", "/open/com.netflix.ninja").status)
        result = OpenResult.NO_BACKGROUND_START
        val conflict = call("POST", "/open/com.netflix.ninja")
        assertEquals("409 Conflict", conflict.status)
        assertEquals("sem acessibilidade", String(conflict.body))
        assertEquals(listOf("com.netflix.ninja"), opened)
    }

    @Test
    fun nearbyPathsAreNotRoutes() {
        for (path in listOf("/apps/", "/app", "/volume", "/open", "/icon", "/APPS")) {
            val reply = call("GET", path)
            assertEquals(path, "404 Not Found", reply.status)
            assertEquals(path, "GoatLauncher", String(reply.body))
        }
        // /apps e /icon nunca olharam ao método.
        assertEquals("200 OK", call("POST", "/apps").status)
        assertEquals("200 OK", call("HEAD", "/icon/com.netflix.ninja").status)
    }

    @Test
    fun theIconPngIsTheCatalogues() {
        // O mesmo PerAppCache do AppCatalog (que o índice do site também usa): a imagem codifica-se uma vez.
        pngs.get(netflix.packageName, netflix.banner!!)
        call("GET", "/icon/com.netflix.ninja")
        call("GET", "/icon/com.netflix.ninja")
        assertEquals(1, encoded)
    }

    @Test
    fun theChosenNameAndIconGoToThePhone() {
        val kodi = LauncherApp("org.xbmc.kodi", "Kodi", null, Image(192, 192), Color(0xFF12AB3F), null)
        val custom = Image(640, 360)
        val chosen = runBlocking {
            listOf(netflix, kodi).asChosen(
                names = mapOf("org.xbmc.kodi" to "Filmes"),
                icons = mapOf("org.xbmc.kodi" to "file:kodi.png", "com.netflix.ninja" to "pack:gone/netflix"),
                // O pack do Netflix foi desinstalado: fica o banner original, como no ecrã.
                load = { spec -> if (spec == "file:kodi.png") custom else null },
            )
        }
        val routes = PhoneRoutes(apps = { chosen }, volume = { }, opener = { OpenResult.OPENED }, pngs = pngs)
        val raw = "GET /apps HTTP/1.1\r\nHost: 192.168.1.20:6470\r\n\r\n"
        val list = String(runBlocking { routes.reply(requireNotNull(LanRequest.read(raw.byteInputStream()))) }.body)
        assertEquals(
            """[{"package":"com.netflix.ninja","label":"Netflix","banner":true,"color":"#1C2333"},""" +
                """{"package":"org.xbmc.kodi","label":"Filmes","banner":true,"color":"#12AB3F"}]""",
            list,
        )
        assertSame(custom, chosen[1].banner)
        assertSame(netflix.banner, chosen[0].banner)
    }
}
