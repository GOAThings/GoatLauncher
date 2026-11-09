package cc.slokker.goatlauncher.data

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
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
 *
 * Aqui fica a rede (ligações, limites, quem pode falar); o que cada pedido responde está no [PhoneRoutes]. Abrir uma
 * app é com o [opener] (o AutoStart, na camada da app).
 */
class PhoneApps(context: Context, apps: AppCatalog, opener: AppOpener, private val scope: CoroutineScope) {
    private val audio = context.getSystemService(AudioManager::class.java)

    private val routes = PhoneRoutes(
        // A lista do catálogo (já lida), e não uma consulta ao PackageManager por pedido: o telemóvel pede os ícones
        // todos de uma vez, até 16 ao mesmo tempo.
        apps = apps::current,
        volume = { direction ->
            runCatching { audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI) }
                .onFailure { Log.w(TAG, "Volume", it) }
        },
        opener = opener,
        pngs = apps.pngs,
    )

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
        client.getOutputStream().apply {
            write(routes.reply(request).bytes())
            flush()
        }
    }

    private fun allowedPeer(client: Socket): Boolean = isLocal(client.inetAddress) && !isThisTv(client)

    /** A porta é para os telemóveis: uma ligação da própria TV (por qualquer dos seus endereços) não conta. */
    private fun isThisTv(client: Socket): Boolean {
        val peer = client.inetAddress
        return peer.isLoopbackAddress || peer == client.localAddress ||
            runCatching { NetworkInterface.getByInetAddress(peer) != null }.getOrDefault(false)
    }

    companion object {
        const val PORT = 6470
        private const val TAG = "PhoneApps"

        /** Só a rede de casa: nunca quem venha de fora. */
        internal fun isLocal(address: InetAddress): Boolean = when (address) {
            is Inet4Address, is Inet6Address ->
                address.isSiteLocalAddress || address.isLinkLocalAddress ||
                    (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC) // fc00::/7
            else -> false
        }

        /** Chega para dois telemóveis a pedir os ícones todos ao mesmo tempo. */
        private const val MAX_CONNECTIONS = 16
        private const val MAX_CONNECTION_MS = 10_000L
        private const val READ_TIMEOUT_MS = 5000
        private const val ACCEPT_RETRY_MS = 1000L
    }
}
