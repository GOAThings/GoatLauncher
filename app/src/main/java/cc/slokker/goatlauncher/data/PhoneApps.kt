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
import java.io.InputStream
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
                val client = runCatching { server.accept() }.getOrNull()
                if (client == null) {
                    delay(ACCEPT_RETRY_MS)
                    continue
                }
                // Quem não pode falar, ou chega com tudo ocupado, é fechado logo: não fica com lugar nem thread.
                if (!runCatching { allowedPeer(client) }.getOrDefault(false) || !slots.tryAcquire()) {
                    runCatching { client.close() }
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

/**
 * O início de um pedido à porta [PhoneApps.PORT], lido com limite de tamanho. Só se responde a pedidos diretos, como
 * os do GoatRemote (um IP em Host e sem Origin): o que vier de uma página web é recusado.
 */
internal class LanRequest private constructor(
    val method: String,
    val path: String,
    private val hasOrigin: Boolean,
    private val hosts: List<String>,
) {
    val allowed: Boolean get() = !hasOrigin && hosts.size == 1 && isIpHost(hosts[0])

    companion object {
        const val MAX_HEAD_BYTES = 8 * 1024

        /** Lê até à linha em branco. null: fechou antes disso, passou de [MAX_HEAD_BYTES] ou não se percebe. */
        fun read(input: InputStream): LanRequest? {
            val head = StringBuilder()
            var emptyLine = true
            while (head.length < MAX_HEAD_BYTES) {
                val byte = input.read()
                if (byte < 0) return null
                val char = byte.toChar()
                head.append(char)
                if (char == '\n') {
                    if (emptyLine) return parse(head.toString())
                    emptyLine = true
                } else if (char != '\r') {
                    emptyLine = false
                }
            }
            return null
        }

        private fun parse(head: String): LanRequest? {
            val lines = head.split('\n').map { it.trimEnd('\r') }
            val requestLine = lines.first().split(' ')
            val method = requestLine[0].takeIf { it.isNotEmpty() } ?: return null
            val path = requestLine.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
            val headers = lines.drop(1).filter { ':' in it }.map { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            return LanRequest(method, path, headers.any { it.first == "origin" }, headers.filter { it.first == "host" }.map { it.second })
        }

        /** Um IPv4 ou um IPv6 entre parênteses retos, com ou sem porta. Nunca um nome (nem se tenta resolvê-lo). */
        fun isIpHost(host: String): Boolean {
            if (host.startsWith('[')) {
                val end = host.indexOf(']')
                if (end < 0 || !isIpv6(host.substring(1, end))) return false
                val rest = host.substring(end + 1)
                return rest.isEmpty() || (rest.startsWith(':') && isPort(rest.substring(1)))
            }
            val parts = host.split(':')
            return when (parts.size) {
                1 -> isIpv4(parts[0])
                2 -> isIpv4(parts[0]) && isPort(parts[1])
                else -> false
            }
        }

        private fun isPort(text: String): Boolean =
            text.length in 1..5 && text.all { it in '0'..'9' } && text.toInt() <= 65535

        private fun isIpv4(text: String): Boolean {
            val parts = text.split('.')
            return parts.size == 4 && parts.all { p -> p.length in 1..3 && p.all { it in '0'..'9' } && p.toInt() <= 255 }
        }

        private fun isIpv6(text: String): Boolean {
            val address = text.substringBefore('%')
            if ('%' in text && !text.substringAfter('%').matches(ZONE)) return false
            val gap = address.indexOf("::")
            if (gap != address.lastIndexOf("::")) return false
            if (gap < 0) return groups(address, last = true) == 8
            val before = groups(address.substring(0, gap), last = false)
            val after = groups(address.substring(gap + 2), last = true)
            return before >= 0 && after >= 0 && before + after <= 7
        }

        /** Quantos grupos de 16 bits tem [part] (um IPv4 no fim conta 2), ou -1 se não estiver bem escrito. */
        private fun groups(part: String, last: Boolean): Int {
            if (part.isEmpty()) return 0
            val items = part.split(':')
            var count = 0
            for ((i, item) in items.withIndex()) {
                count += when {
                    item.length in 1..4 && item.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> 1
                    last && i == items.lastIndex && isIpv4(item) -> 2
                    else -> return -1
                }
            }
            return count
        }

        private val ZONE = Regex("[A-Za-z0-9._~-]+")
    }
}
