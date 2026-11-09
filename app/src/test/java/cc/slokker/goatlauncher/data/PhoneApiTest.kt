package cc.slokker.goatlauncher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * A porta 6470 como o GoatRemote e a GoatTV a usam: caminhos, métodos, códigos e o JSON de `/apps`. Cada teste diz onde
 * o outro lado depende disto (GoatRemote em `GoatRemote/android` e `GoatRemote/ios`, GoatTV em `itv`).
 *
 * Um código que mude aqui pode partir o comando: o volume do GoatRemote só usa a tecla do protocolo (que reinicia a
 * SHIELD com o HomeGuardService a ver as teclas) quando o launcher responde 404 ou recusa a ligação numa TV onde nunca
 * se viu launcher (`GoatRemote/android/.../tv/Remote.kt` 46-50, `GoatRemote/ios/RemoteCore/.../Remote.swift`
 * `LauncherVolume.outcome`).
 */
class PhoneApiTest {
    private class Image(override val width: Int, override val height: Int) : ImageBitmap {
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override val hasAlpha: Boolean get() = true
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit
        override fun prepareToDraw() = Unit
    }

    private val netflixBanner = Image(480, 270)
    private val kodiIcon = Image(192, 192)
    private val installed = listOf(
        LauncherApp("com.netflix.ninja", "Netflix", banner = netflixBanner, icon = null, color = Color(0xFF1C2333), smallIcon = null),
        LauncherApp("org.xbmc.kodi", "Kodi \"Omega\"", banner = null, icon = kodiIcon, color = Color(0xFF12AB3F), smallIcon = null),
        LauncherApp("no.image", "Sem imagem", banner = null, icon = null, color = Color(0xFF000000), smallIcon = null),
    )

    private val volumes = mutableListOf<Int>()
    private val opened = mutableListOf<String>()
    private var encoded = 0
    private var accessibility = true
    private val launchable = setOf("com.netflix.ninja", "org.xbmc.kodi")

    private val routes = PhoneRoutes(
        apps = { installed },
        png = { image -> encoded++; "png:${image.width}x${image.height}".toByteArray() },
        volume = { volumes += it },
        canOpen = { it in launchable },
        canStartFromBackground = { accessibility },
        open = { opened += it },
    )

    /** O pedido tal como chega à porta (o mesmo que o GoatRemote Android manda, com HttpURLConnection). */
    private fun call(method: String, path: String, vararg headers: String = arrayOf("Host: 192.168.1.20:6470")): LanReply {
        val raw = (listOf("$method $path HTTP/1.1", "User-Agent: Dalvik/2.1.0") + headers + "Connection: Keep-Alive")
            .joinToString("\r\n") + "\r\n\r\n"
        val request = requireNotNull(LanRequest.read(raw.byteInputStream()))
        return runBlocking { routes.reply(request) }
    }

    private val LanReply.text get() = String(body)

    @Test
    fun appsListShape() {
        // GoatRemote Android RemoteViewModel.kt 372-382: GET /apps, 200, JSONArray; getString("package"/"label") (obrigatórios),
        // optBoolean("banner"), optString("color"). iOS RemoteModel.swift 292-298 e TVApp 1020-1025: JSONDecoder com
        // "banner" Bool obrigatório e "color" opcional, no máximo 2 MB. Qualquer 200 marca "esta TV tem launcher".
        val reply = call("GET", "/apps")
        assertEquals("200 OK", reply.status)
        assertEquals("application/json", reply.type)
        val list = JsonParser.parseString(reply.text).asJsonArray
        // A ordem é a das apps instaladas (pelo nome, ver InstalledApps); as escondidas e bloqueadas também vão.
        assertEquals(listOf("com.netflix.ninja", "org.xbmc.kodi", "no.image"), list.map { it.asJsonObject.get("package").asString })
        for (item in list) assertEquals(setOf("package", "label", "banner", "color"), item.asJsonObject.keySet())
        val netflix = list[0].asJsonObject
        assertEquals("Netflix", netflix.get("label").asString)
        assertTrue(netflix.get("banner").asJsonPrimitive.isBoolean)
        assertEquals(true, netflix.get("banner").asBoolean)
        assertEquals("#1C2333", netflix.get("color").asString)
        val kodi = list[1].asJsonObject
        assertEquals("Kodi \"Omega\"", kodi.get("label").asString)
        assertEquals(false, kodi.get("banner").asBoolean)
        // "#RRGGBB" em maiúsculas, sem alfa (o iPhone lê-o com radix 16 depois do "#").
        assertEquals("#12AB3F", kodi.get("color").asString)
    }

    @Test
    fun iconIsTheBannerElseTheIcon() {
        // GoatRemote Android RemoteViewModel.kt 395 e iOS RemoteModel.swift 309: "http://<tv>:6470/icon/<pacote>".
        assertEquals(LanReply("200 OK", "image/png", "png:480x270".toByteArray()).bytes().toList(), call("GET", "/icon/com.netflix.ninja").bytes().toList())
        assertEquals("png:192x192", call("GET", "/icon/org.xbmc.kodi").text)
        assertEquals("image/png", call("GET", "/icon/org.xbmc.kodi").type)
        assertEquals("404 Not Found", call("GET", "/icon/no.image").status)
        assertEquals("404 Not Found", call("GET", "/icon/not.installed").status)
        // A mesma imagem não se volta a codificar.
        assertEquals(2, encoded)
    }

    @Test
    fun volume() {
        // GoatRemote Android RemoteViewModel.kt 780 e iOS RemoteModel.swift 725: POST /volume/up|down|mute; só 200 conta
        // como feito (Remote.kt 46-50).
        for (what in listOf("up", "down", "mute")) {
            val reply = call("POST", "/volume/$what")
            assertEquals("200 OK", reply.status)
            assertEquals("ok", reply.text)
        }
        // AudioManager.ADJUST_RAISE, ADJUST_LOWER, ADJUST_TOGGLE_MUTE.
        assertEquals(listOf(1, -1, 101), volumes)
        // Outro método ou outra ação: 404, e o volume não mexe.
        assertEquals("404 Not Found", call("GET", "/volume/up").status)
        assertEquals("404 Not Found", call("POST", "/volume/louder").status)
        assertEquals(3, volumes.size)
    }

    @Test
    fun openGoesThroughTheLauncher() {
        // GoatRemote Android RemoteViewModel.kt 347-360 e iOS RemoteModel.swift 272-285: POST /open/<pacote>; tudo o que
        // não seja 200 faz o comando abrir pelo market://launch do protocolo.
        val ok = call("POST", "/open/com.netflix.ninja")
        assertEquals("200 OK", ok.status)
        assertEquals(listOf("com.netflix.ninja"), opened)
        assertEquals("404 Not Found", call("POST", "/open/not.installed").status)
        assertEquals("404 Not Found", call("GET", "/open/com.netflix.ninja").status)
        // Sem o serviço de acessibilidade o Android não deixa abrir a partir do fundo: 409. A GoatTV conta com isto:
        // o /play dela responde 409 fora de frente e o telemóvel abre-a por aqui (itv PhoneChannels.kt 35-36, GoatRemote
        // RemoteUiTest.kt 152-156).
        accessibility = false
        val conflict = call("POST", "/open/org.xbmc.kodi")
        assertEquals("409 Conflict", conflict.status)
        assertEquals(listOf("com.netflix.ninja"), opened)
    }

    @Test
    fun anythingElseIs404() {
        val reply = call("GET", "/")
        assertEquals("404 Not Found", reply.status)
        assertEquals("text/plain", reply.type)
        assertEquals("GoatLauncher", reply.text)
        assertEquals("404 Not Found", call("GET", "/channels").status)
    }

    @Test
    fun aWebPageIsRefused() {
        // As mesmas regras do LanRequest da GoatTV (itv data/LanRequest.kt) e do GoatCore (LanRequest.swift).
        for (headers in listOf(
            arrayOf("Host: 192.168.1.20:6470", "Origin: https://site.example"),
            arrayOf("Host: site.example:6470"),
        )) {
            val reply = call("POST", "/volume/up", *headers)
            assertEquals("403 Forbidden", reply.status)
            assertEquals("recusado", reply.text)
        }
        assertTrue(volumes.isEmpty())
        // Um nome de casa (uma TV juntada à mão pelo nome) serve.
        assertEquals("200 OK", call("GET", "/apps", "Host: shield.local:6470").status)
    }

    @Test
    fun everyReplyClosesAndCarriesItsLength() {
        val bytes = String(call("POST", "/volume/up").bytes())
        assertEquals("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\nok", bytes)
        val notFound = call("GET", "/icon/not.installed")
        assertEquals("não há", notFound.text)
        assertTrue(String(notFound.bytes()).contains("Content-Length: ${"não há".toByteArray().size}\r\n"))
    }

    @Test
    fun onlyPhonesOnTheHomeNetwork() {
        for (ip in listOf("192.168.1.20", "10.0.2.2", "172.16.5.4", "169.254.1.1", "fe80::1", "fd12:3456::1", "fc00::1")) {
            assertTrue(ip, PhoneApps.isLocal(InetAddress.getByName(ip)))
        }
        for (ip in listOf("8.8.8.8", "127.0.0.1", "::1", "2001:db8::1", "100.64.0.1")) {
            assertFalse(ip, PhoneApps.isLocal(InetAddress.getByName(ip)))
        }
        assertEquals(6470, PhoneApps.PORT)
    }
}
