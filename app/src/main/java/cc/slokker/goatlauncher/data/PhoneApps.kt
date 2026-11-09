package cc.slokker.goatlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import cc.slokker.goatlauncher.AutoStart
import cc.slokker.goatlauncher.HomeGuardService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * O GoatRemote (o comando no telemóvel) na rede de casa, só para outros aparelhos com endereços privados, em pedidos
 * diretos (ver [LanRequest]):
 *
 *   GET /apps            [{"package", "label", "banner": true|false, "color": "#RRGGBB"}], pelo nome: o protocolo
 *                        das Android TV não dá a lista de apps instaladas
 *   GET /icon/<pacote>   PNG do banner (480×270) ou, sem banner, do ícone (192×192)
 *   POST /volume/up|down|mute
 *                        o volume, pedido ao sistema como faz o comando físico (com HDMI-CEC vai para a TV). As
 *                        teclas de volume do protocolo do comando rebentam o Android 11 da SHIELD quando um serviço
 *                        de acessibilidade vê as teclas (o HomeGuardService, com botões a abrir apps).
 *   POST /open/<pacote>  abre a app, com o controlo parental. A SHIELD recusa o market://launch?id=<pacote> do
 *                        protocolo do comando e fecha a ligação. Sem o serviço de acessibilidade o Android não deixa
 *                        abrir apps a partir do fundo: responde 409, e o comando tenta pelo protocolo.
 */
class PhoneApps(private val context: Context, private val apps: InstalledApps, private val scope: CoroutineScope) {
    private val audio = context.getSystemService(AudioManager::class.java)

    private val pngs = HashMap<String, ByteArray>()

    /** Só desta porta (mais um para esperar ligações): nunca ocupa os threads do Dispatchers.IO do resto da app. */
    private val listener = Dispatchers.IO.limitedParallelism(MAX_CONNECTIONS + 1)
    private val slots = Semaphore(MAX_CONNECTIONS)

    fun start() {
        scope.launch(listener) {
            val server = runCatching { ServerSocket(PORT) }.getOrElse {
                Log.w(TAG, "Porta $PORT ocupada", it)
                return@launch
            }
            while (true) {
                // Com tudo ocupado, os seguintes esperam na fila do sistema: um telemóvel pede muitos ícones de uma vez.
                slots.acquire()
                val client = runCatching { server.accept() }.getOrNull()
                if (client == null) {
                    slots.release()
                    delay(ACCEPT_RETRY_MS)
                    continue
                }
                // Quem não pode falar é fechado logo: não fica com lugar nem thread.
                if (!runCatching { allowedPeer(client) }.getOrDefault(false)) {
                    runCatching { client.close() }
                    slots.release()
                    continue
                }
                scope.launch(listener) {
                    try {
                        connection(client)
                    } finally {
                        slots.release()
                    }
                }
            }
        }
    }

    /** Uma ligação dura no máximo [MAX_CONNECTION_MS], e o que falhar nela fica nela: o launcher continua. */
    private suspend fun connection(client: Socket) {
        val deadline = scope.launch(Dispatchers.Default) {
            delay(MAX_CONNECTION_MS)
            runCatching { client.close() }
        }
        try {
            client.use { serve(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Ligação: ${e.javaClass.simpleName}")
        } finally {
            deadline.cancel()
        }
    }

    private suspend fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val request = LanRequest.read(client.getInputStream().buffered()) ?: return
        if (!request.allowed) {
            reply(client, "text/plain", "recusado".toByteArray(), status = "403 Forbidden")
            return
        }
        val method = request.method
        val path = request.path
        if (path.startsWith("/volume/")) return volume(client, method, path.removePrefix("/volume/"))
        if (path.startsWith("/open/")) return open(client, method, path.removePrefix("/open/"))
        when {
            path == "/apps" -> {
                val json = JSONArray()
                for (app in apps.load()) {
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
                val app = apps.load().firstOrNull { it.packageName == pkg }
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

    private fun open(client: Socket, method: String, pkg: String) {
        when {
            method != "POST" || apps.launchIntent(pkg) == null ->
                reply(client, "text/plain", "não há".toByteArray(), status = "404 Not Found")
            HomeGuardService.instance == null ->
                reply(client, "text/plain", "sem acessibilidade".toByteArray(), status = "409 Conflict")
            else -> {
                AutoStart.open(context, pkg)
                reply(client, "text/plain", "ok".toByteArray())
            }
        }
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

    private fun allowedPeer(client: Socket): Boolean = isLocal(client.inetAddress) && !isThisTv(client)

    /** Só a rede de casa: nunca quem venha de fora. */
    private fun isLocal(address: InetAddress): Boolean = when (address) {
        is Inet4Address, is Inet6Address ->
            address.isSiteLocalAddress || address.isLinkLocalAddress ||
                (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC) // fc00::/7
        else -> false
    }

    /** A porta é para os telemóveis: uma ligação da própria TV (por qualquer dos seus endereços) não conta. */
    private fun isThisTv(client: Socket): Boolean {
        val peer = client.inetAddress
        return peer.isLoopbackAddress || peer == client.localAddress ||
            runCatching { NetworkInterface.getByInetAddress(peer) != null }.getOrDefault(false)
    }

    companion object {
        const val PORT = 6470
        private const val TAG = "PhoneApps"

        /** Chega para dois telemóveis a pedir os ícones todos ao mesmo tempo. */
        private const val MAX_CONNECTIONS = 16
        private const val MAX_CONNECTION_MS = 10_000L
        private const val READ_TIMEOUT_MS = 5000
        private const val ACCEPT_RETRY_MS = 1000L
    }
}
