package cc.slokker.goatlauncher.ui.state

import android.content.Context
import android.os.SystemClock
import android.util.Log
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.WatchNextFeed
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.YouTubeAccount
import cc.slokker.goatlauncher.data.YouTubeLogin
import cc.slokker.goatlauncher.ui.Strings
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O "Continuar a ver": o Watch Next de todas as apps (lido de novo quando alguma o muda) e as recomendações do YouTube
 * (com a conta ligada em Definições → YouTube) na app de YouTube instalada.
 *
 * [installed]: as apps instaladas (null até à primeira leitura). [homeApps]: a fila do ecrã inicial (para o exemplo
 * de debug). [prefetch]: pede já as imagens dos primeiros cartões (ver [CoilCardPrefetch]).
 */
class ContinueWatching(
    private val scope: CoroutineScope,
    private val source: WatchNextFeed,
    private val youtube: YouTubeAccount,
    private val installed: StateFlow<List<LauncherApp>?>,
    device: StateFlow<DeviceSettings>,
    private val homeApps: () -> List<LauncherApp>,
    private val youtubeFeed: () -> String,
    private val prefetch: (Map<String, List<WatchNextItem>>) -> Unit,
    private val strings: () -> Strings,
    private val toast: (String) -> Unit,
    /** O exemplo de debug ([enableDemo]) só existe nas versões de debug. */
    private val demoAllowed: Boolean,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    /** O "Continuar a ver" de todas as apps, por pacote. Mudar o foco só filtra isto (sem consultas novas). */
    private val _system = MutableStateFlow<Map<String, List<WatchNextItem>>>(emptyMap())

    /** Recomendações do YouTube, com o pacote da app que as abre. */
    private val _youtube = MutableStateFlow<List<WatchNextItem>>(emptyList())

    /** O Watch Next de todas as apps, mais as recomendações do YouTube na app de YouTube instalada. */
    val watchNext: StateFlow<Map<String, List<WatchNextItem>>> = combine(_system, _youtube, device) { system, yt, d ->
        merged(system, yt, d.noContinue)
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _youtubeLinked = MutableStateFlow(youtube.linked)
    val youtubeLinked: StateFlow<Boolean> = _youtubeLinked.asStateFlow()

    /** Ligação ao YouTube em curso: o código a mostrar (ou null enquanto se pede), e erros. */
    private val _youtubeLogin = MutableStateFlow<YouTubeLogin?>(null)
    val youtubeLogin: StateFlow<YouTubeLogin?> = _youtubeLogin.asStateFlow()
    private var youtubeLoginJob: Job? = null
    private var youtubeLoadedAt = 0L

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var stopObserving: () -> Unit = {}
    private var demo = false

    /** Chamado uma vez, ao arrancar o ecrã. */
    fun start() {
        scope.launch {
            // As apps escrevem no Watch Next várias vezes seguidas: junta as mudanças antes de ler.
            requests.receiveAsFlow().collectLatest {
                wait(DEBOUNCE_MS)
                // Se a leitura falhar, ficam as entradas que já havia.
                val all = (source.load() ?: return@collectLatest).let { if (demo) it + demoItems() else it }
                _system.value = all
                prefetch(all)
            }
        }
        scope.launch {
            // YouTube: quando já se sabe que apps há, e de 30 em 30 minutos.
            installed.first { it != null }
            while (true) {
                loadYouTube()
                wait(YOUTUBE_MS)
            }
        }
        reload()
        stopObserving = source.observe(::reload)
    }

    fun stop() {
        stopObserving()
    }

    /** De volta ao ecrã inicial: lê de novo o Watch Next, e o YouTube se já tiver uns minutos. */
    fun onResume() {
        reload()
        if (clock() - youtubeLoadedAt > YOUTUBE_STALE_MS) refreshYouTube()
    }

    fun reload() {
        requests.trySend(Unit)
    }

    fun refreshYouTube() {
        scope.launch { loadYouTube() }
    }

    /** Só em debug: "Continuar a ver" de exemplo nos 2 primeiros favoritos. */
    fun enableDemo() {
        if (!demoAllowed || demo) return
        demo = true
        reload()
    }

    /** A app que abre os vídeos (TizenTube, a app oficial, SmartTube…), a primeira instalada da lista. */
    private fun youtubeApp(): String? {
        val pkgs = installed.value.orEmpty().map { it.packageName }.toSet()
        return YOUTUBE_APPS.firstOrNull { it in pkgs }
    }

    private suspend fun loadYouTube() {
        val pkg = youtubeApp()
        if (!youtube.linked || pkg == null) {
            _youtube.value = emptyList()
            return
        }
        try {
            val items = youtube.recommendations(pkg, youtubeFeed())
            Log.d("YouTube", "Recomendações: ${items.size}")
            _youtube.value = items
            youtubeLoadedAt = clock()
            prefetch(mapOf(pkg to items))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("YouTube", "Recomendações: ${e.message}")
        }
        _youtubeLinked.value = youtube.linked
    }

    /** Pede um código e espera que a pessoa o confirme em google.com/device (pede outro se expirar). */
    fun startYouTubeLogin() {
        youtubeLoginJob?.cancel()
        youtubeLoginJob = scope.launch {
            _youtubeLogin.value = null
            while (true) {
                val login = try {
                    youtube.startLogin()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(strings().youtubeError)
                    wait(LOGIN_RETRY_MS)
                    continue
                }
                _youtubeLogin.value = login
                while (System.currentTimeMillis() < login.expiresAt) {
                    wait(login.intervalSec * 1000L)
                    val done = runCatching { youtube.poll(login) }.getOrNull() ?: continue
                    if (done) {
                        _youtubeLinked.value = true
                        _youtubeLogin.value = null
                        toast(strings().youtubeLinked)
                        loadYouTube()
                        return@launch
                    }
                    break // recusado ou expirado: pede um código novo
                }
            }
        }
    }

    fun cancelYouTubeLogin() {
        youtubeLoginJob?.cancel()
        youtubeLoginJob = null
        _youtubeLogin.value = null
    }

    fun unlinkYouTube() {
        youtube.unlink()
        _youtubeLinked.value = false
        _youtube.value = emptyList()
    }

    private fun demoItems(): Map<String, List<WatchNextItem>> {
        val pkgs = homeApps().take(2).map { it.packageName }
        fun item(id: Long, pkg: String, title: String, image: String, ratio: Float, s: String?, e: String?, pos: Long) = WatchNextItem(
            id = -id, packageName = pkg, title = title, episodeTitle = null, season = s, episode = e,
            imageUri = image, aspectRatio = ratio, positionMs = pos, durationMs = 2_700_000, type = WatchNextItem.TYPE_CONTINUE,
            lastEngagement = System.currentTimeMillis() - id, intentUri = null, isChannel = id == 4L,
        )
        val out = mutableMapOf<String, List<WatchNextItem>>()
        pkgs.getOrNull(0)?.let { p ->
            out[p] = listOf(
                item(1, p, "Lago ao amanhecer", "https://picsum.photos/id/1018/1280/720", 16f / 9f, "2", "5", 1_200_000),
                item(2, p, "Montanha", "https://picsum.photos/id/1015/1280/720", 16f / 9f, "1", "3", 300_000),
                item(3, p, "Costa", "https://picsum.photos/id/1016/1280/720", 16f / 9f, null, null, 0),
            )
        }
        // Um logo quadrado publicado como 16:9 (como os canais da GoatTV).
        pkgs.getOrNull(1)?.let { p -> out[p] = listOf(item(4, p, "CANAL", "https://picsum.photos/id/237/400/400", 16f / 9f, null, null, 0)) }
        return out
    }

    companion object {
        /** As apps escrevem várias vezes seguidas: espera-se isto depois da última mudança antes de ler. */
        const val DEBOUNCE_MS = 300L
        const val YOUTUBE_MS = 30 * 60_000L
        const val YOUTUBE_STALE_MS = 10 * 60_000L
        const val LOGIN_RETRY_MS = 10_000L

        /** Apps que abrem links do YouTube, por ordem de preferência. */
        val YOUTUBE_APPS = listOf(
            "io.gh.reisxd.tizentube.cobalt",
            "io.gh.reisxd.tizentube",
            "com.google.android.youtube.tv",
            "com.teamsmart.videomanager.tv",
            "com.liskovsoft.smarttubetv.beta",
        )

        /**
         * O Watch Next do sistema com as recomendações do YouTube no fim da lista da app que as abre, sem as apps em
         * que a pessoa desligou o "Continuar a ver" (menu da app).
         */
        internal fun merged(
            system: Map<String, List<WatchNextItem>>,
            youtube: List<WatchNextItem>,
            noContinue: Set<String>,
        ): Map<String, List<WatchNextItem>> {
            val all = if (youtube.isEmpty()) {
                system
            } else {
                system + (youtube.first().packageName to (system[youtube.first().packageName].orEmpty() + youtube))
            }
            return all - noContinue
        }
    }
}

/**
 * Pede já as imagens dos primeiros cartões de cada app (para a cache do Coil), no tamanho em que aparecem: ao mudar
 * de app, os cartões aparecem logo em vez de carregarem um a um. Um novo pedido substitui o anterior dessa app.
 * [cardHeightDp]: a altura dos cartões no ecrã (o ecrã inicial dá-a; este estado não depende dele).
 */
class CoilCardPrefetch(private val context: Context, private val cardHeightDp: Float) : (Map<String, List<WatchNextItem>>) -> Unit {
    private val prefetches = HashMap<String, List<Disposable>>()

    override fun invoke(all: Map<String, List<WatchNextItem>>) {
        val loader = SingletonImageLoader.get(context)
        val height = (cardHeightDp * context.resources.displayMetrics.density).toInt()
        for ((pkg, items) in all) {
            prefetches.remove(pkg)?.forEach { it.dispose() }
            prefetches[pkg] = items.filter { it.imageUri != null }.take(PER_APP).map { item ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(item.imageUri)
                        .size((height * item.aspectRatio.coerceIn(0.6f, 2f)).toInt(), height)
                        .build(),
                )
            }
        }
    }

    private companion object {
        /** Os cartões que se veem de uma vez numa fila; os outros carregam ao chegar lá. */
        const val PER_APP = 8
    }
}
