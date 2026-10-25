package cc.slokker.goatlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * O GoatRemote (o comando no telemóvel) na rede de casa, só para endereços privados:
 *
 *   GET /apps            [{"package", "label", "banner": true|false, "color": "#RRGGBB"}], pelo nome: o protocolo
 *                        das Android TV não dá a lista de apps instaladas
 *   GET /icon/<pacote>   PNG do banner (480×270) ou, sem banner, do ícone (192×192)
 *   POST /volume/up|down|mute
 *                        o volume, pedido ao sistema como faz o comando físico (com HDMI-CEC vai para a TV). As
 *                        teclas de volume do protocolo do comando rebentam o Android 11 da SHIELD quando um serviço
 *                        de acessibilidade vê as teclas (o HomeGuardService, com botões a abrir apps).
 *
 * Abrir uma app continua a ser pelo comando (market://launch?id=<pacote>), não por aqui.
 */
class PhoneApps(context: Context, private val apps: InstalledApps, private val scope: CoroutineScope) {
    private val audio = context.getSystemService(AudioManager::class.java)

    private val pngs = HashMap<String, ByteArray>()

    fun start() {
        scope.launch(Dispatchers.IO) {
            val server = runCatching { ServerSocket(PORT) }.getOrElse {
                Log.w(TAG, "Porta $PORT ocupada", it)
                return@launch
            }
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                scope.launch(Dispatchers.IO) { client.use(::serve) }
            }
        }
    }

    private fun serve(client: Socket) {
        if (!isLocal(client.inetAddress)) return
        client.soTimeout = 5000
        val line = client.getInputStream().bufferedReader().readLine() ?: return
        val method = line.substringBefore(' ')
        val path = line.split(" ").getOrNull(1) ?: return
        if (path.startsWith("/volume/")) return volume(client, method, path.removePrefix("/volume/"))
        val list = runBlocking { apps.load() }
        when {
            path == "/apps" -> {
                val json = JSONArray()
                for (app in list) {
                    json.put(
                        JSONObject()
                            .put("package", app.packageName)
                            .put("label", app.label)
                            .put("banner", app.banner != null)
                            .put("color", String.format("#%06X", app.color.toArgb() and 0xFFFFFF)),
                    )
                }
                reply(client, "application/json", json.toString().toByteArray())
            }
            path.startsWith("/icon/") -> {
                val pkg = path.removePrefix("/icon/")
                val app = list.firstOrNull { it.packageName == pkg }
                val image = app?.banner ?: app?.icon
                if (image == null) {
                    reply(client, "text/plain", "não há".toByteArray(), status = "404 Not Found")
                    return
                }
                val png = synchronized(pngs) {
                    pngs.getOrPut(pkg + ":" + System.identityHashCode(image)) {
                        ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    }
                }
                reply(client, "image/png", png)
            }
            else -> reply(client, "text/plain", "GoatLauncher".toByteArray(), status = "404 Not Found")
        }
    }

    private fun volume(client: Socket, method: String, what: String) {
        val direction = when (what) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> null
        }
        if (method != "POST" || direction == null) {
            reply(client, "text/plain", "não há".toByteArray(), status = "404 Not Found")
            return
        }
        runCatching { audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI) }
            .onFailure { Log.w(TAG, "Volume", it) }
        reply(client, "text/plain", "ok".toByteArray())
    }

    private fun reply(client: Socket, type: String, body: ByteArray, status: String = "200 OK") {
        val head = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-cache\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray())
            write(body)
            flush()
        }
    }

    /** Só a rede de casa (e a própria TV): nunca quem venha de fora. */
    private fun isLocal(address: InetAddress): Boolean = when (address) {
        is Inet4Address, is Inet6Address ->
            address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
                (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC) // fc00::/7
        else -> false
    }

    companion object {
        const val PORT = 6470
        private const val TAG = "PhoneApps"
    }
}
