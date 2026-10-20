package cc.slokker.goatlauncher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.slokker.goatlauncher.BuildConfig
import cc.slokker.goatlauncher.LauncherApp
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.AppRelease
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.data.RedditImage
import cc.slokker.goatlauncher.data.LauncherApp as App
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Updater
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.api.BackendDevice
import cc.slokker.goatlauncher.data.api.BackendUser
import cc.slokker.goatlauncher.ui.home.CardHeight
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

sealed interface Screen {
    data object Home : Screen
    data object Apps : Screen
    data object Settings : Screen
    data object Activation : Screen
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Application = application
    private val c = (application as LauncherApp).container
    private val settings = c.settings
    val strings = Strings.forSystem()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    val account: StateFlow<AccountSettings> = settings.account
    val device: StateFlow<DeviceSettings> = settings.device
    val session: StateFlow<SessionStore.Session?> = settings.session
    val ready: StateFlow<Boolean> = settings.ready

    /** Todas as apps instaladas, por nome; null enquanto a primeira leitura não acaba. */
    private val installed = MutableStateFlow<List<App>?>(null)
    val allApps: StateFlow<List<App>?> = installed.asStateFlow()

    /** A fila do ecrã inicial: pela ordem guardada, sem as escondidas. */
    val apps: StateFlow<List<App>?> = combine(installed, settings.device) { all, d ->
        all?.let { ordered(it, d.apps).filterNot { app -> app.packageName in d.hidden } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** O "Continuar a ver" de todas as apps, por pacote. Mudar o foco só filtra isto (sem consultas novas). */
    private val _watchNext = MutableStateFlow<Map<String, List<WatchNextItem>>>(emptyMap())
    val watchNext: StateFlow<Map<String, List<WatchNextItem>>> = _watchNext.asStateFlow()

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Botão Home com o launcher já aberto: volta ao início da fila. */
    val homeEvents: SharedFlow<Unit> = _homeEvents

    /** A última app com foco no ecrã inicial (ao voltar das definições, o foco regressa a ela). */
    var focusedPackage: String? = null

    private val _reddit = MutableStateFlow<RedditImage?>(null)

    /** Com o fundo do Reddit: a imagem atual (já carregada na cache, para entrar com um fade sem saltos). */
    val redditImage: StateFlow<RedditImage?> = _reddit.asStateFlow()

    /** Imagens que não servem de fundo (ao alto, ou que não carregaram). */
    private val badReddit = mutableSetOf<String>()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    val appVersion: String get() = c.updater.currentVersion

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val watchNextRequests = Channel<Unit>(Channel.CONFLATED)
    private val appRequests = Channel<Unit>(Channel.CONFLATED)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appRequests.trySend(Unit)
        }
    }
    private val stopObservingWatchNext: () -> Unit

    init {
        viewModelScope.launch {
            settings.init()
            settings.sync(refreshToken = true)
        }
        viewModelScope.launch {
            appRequests.receiveAsFlow().collectLatest { installed.value = c.apps.load() }
        }
        viewModelScope.launch {
            // As apps escrevem no Watch Next várias vezes seguidas: junta as mudanças antes de ler.
            watchNextRequests.receiveAsFlow().collectLatest {
                delay(300)
                val all = c.watchNext.load().let { if (demo) it + demoWatchNext() else it }
                _watchNext.value = all
                prefetchImages(all)
            }
        }
        viewModelScope.launch {
            // Fundo do Reddit: mostra uma imagem e troca-a a cada RedditWallpapers.ROTATE_MS.
            settings.account.map { it.wallpaper }.distinctUntilChanged().collectLatest { wp ->
                if (wp.type != Wallpaper.TYPE_REDDIT) {
                    _reddit.value = null
                    return@collectLatest
                }
                var next = false
                while (true) {
                    showReddit(wp.value, next)
                    delay(c.reddit.msUntilRotation(wp.value))
                    next = true
                }
            }
        }
        viewModelScope.launch {
            c.updater.installFailures.collect { _update.value = UpdateState.Failed(it) }
        }
        viewModelScope.launch {
            // Procura versões novas pouco depois de abrir e de 12 em 12 horas (o launcher fica aberto dias a fio).
            delay(20_000)
            while (true) {
                checkForUpdate(manual = false)
                delay(UPDATE_CHECK_MS)
            }
        }
        reloadApps()
        reloadWatchNext()
        stopObservingWatchNext = c.watchNext.observe(::reloadWatchNext)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Exportado: são difusões protegidas do sistema (só o sistema as pode enviar).
        ContextCompat.registerReceiver(application, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onCleared() {
        context.unregisterReceiver(packageReceiver)
        stopObservingWatchNext()
    }

    fun onResume() {
        reloadApps()
        reloadWatchNext()
        viewModelScope.launch { settings.syncIfStale() }
    }

    fun onHomePressed() {
        _screen.value = Screen.Home
        _homeEvents.tryEmit(Unit)
    }

    fun reloadApps() {
        appRequests.trySend(Unit)
    }

    fun reloadWatchNext() {
        watchNextRequests.trySend(Unit)
    }

    /** Escolhe a imagem do subreddit e só a mostra depois de carregada (deitada); senão tenta outra. */
    private suspend fun showReddit(subreddit: String, next: Boolean) {
        repeat(5) { attempt ->
            val image = c.reddit.current(subreddit, next = next || attempt > 0, skip = badReddit) ?: return
            if (image.url == _reddit.value?.url) return
            if (preloadWallpaper(image.url)) {
                _reddit.value = image
                return
            }
            badReddit += image.url
        }
    }

    /** Carrega a imagem para a cache no tamanho do ecrã; false se falhar ou for ao alto. */
    private suspend fun preloadWallpaper(url: String): Boolean {
        val metrics = context.resources.displayMetrics
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(url)
                .size(metrics.widthPixels, metrics.heightPixels)
                .scale(Scale.FILL)
                .build(),
        )
        val image = (result as? SuccessResult)?.image ?: return false
        return image.width > image.height
    }

    /**
     * Pede já as imagens de todos os cartões (para a cache do Coil), no tamanho em que aparecem: ao mudar de app,
     * os cartões aparecem logo em vez de carregarem um a um.
     */
    private fun prefetchImages(all: Map<String, List<WatchNextItem>>) {
        val loader = SingletonImageLoader.get(context)
        val height = (CardHeight.value * context.resources.displayMetrics.density).toInt()
        all.values.flatten().forEach { item ->
            val uri = item.imageUri ?: return@forEach
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(uri)
                    .size((height * item.aspectRatio.coerceIn(0.6f, 2f)).toInt(), height)
                    .build(),
            )
        }
    }

    private var demo = false

    /** Só em debug (`am start … --ez demo_watch_next true`): "Continuar a ver" de exemplo nos 2 primeiros favoritos. */
    fun enableDemoWatchNext() {
        if (!BuildConfig.DEBUG || demo) return
        demo = true
        reloadWatchNext()
    }

    private fun demoWatchNext(): Map<String, List<WatchNextItem>> {
        val pkgs = apps.value.orEmpty().take(2).map { it.packageName }
        fun item(id: Long, pkg: String, title: String, image: String, ratio: Float, s: String?, e: String?, pos: Long) = WatchNextItem(
            id = -id, packageName = pkg, title = title, episodeTitle = null, season = s, episode = e,
            imageUri = image, aspectRatio = ratio, positionMs = pos, durationMs = 2_700_000, type = WatchNextItem.TYPE_CONTINUE,
            lastEngagement = System.currentTimeMillis() - id, intentUri = null,
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

    // ---- Atualizações ----

    fun checkForUpdate(manual: Boolean) {
        if (!c.updater.enabled) {
            if (manual) toast(strings.updatesOnlyRelease)
            return
        }
        val state = _update.value
        if (state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing) return
        if (manual) _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = try {
                val latest = c.updater.latest()
                if (latest != null && Updater.isNewer(latest.version, c.updater.currentVersion)) {
                    if (!manual && state !is UpdateState.Available) toast(strings.updateAvailable(latest.version))
                    UpdateState.Available(latest)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) UpdateState.Failed(e.message ?: e.javaClass.simpleName) else state
            }
        }
    }

    /** Descarrega e entrega ao instalador do Android a versão encontrada (ou procura, se ainda não houver). */
    fun installUpdate() {
        val release = (_update.value as? UpdateState.Available)?.release ?: return checkForUpdate(manual = true)
        if (!c.updater.canInstall()) {
            toast(strings.allowInstall)
            c.updater.openInstallPermission()
            return
        }
        viewModelScope.launch {
            try {
                _update.value = UpdateState.Downloading(release, 0f)
                val apk = c.updater.download(release) { p -> _update.value = UpdateState.Downloading(release, p) }
                _update.value = UpdateState.Installing(release)
                c.updater.install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _update.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // ---- Abrir ----

    fun launchApp(activity: Context, pkg: String) {
        if (!SystemIntents.start(activity, c.apps.launchIntent(pkg))) toast(strings.cantOpen)
    }

    /** Abre o conteúdo; se o `intent_uri` falhar, abre pelo menos a app. */
    fun openWatchNext(activity: Context, item: WatchNextItem) {
        if (SystemIntents.start(activity, item.intent())) return
        if (!SystemIntents.start(activity, c.apps.launchIntent(item.packageName))) toast(strings.cantOpen)
    }

    // ---- Navegação ----

    fun openHome() {
        _screen.value = Screen.Home
    }

    fun openApps() {
        _screen.value = Screen.Apps
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun closeSettings() {
        _screen.value = Screen.Home
    }

    fun openActivation() {
        _screen.value = Screen.Activation
    }

    fun closeActivation() {
        _screen.value = Screen.Settings
    }

    fun toast(message: String) {
        _toast.value = message
    }

    fun clearToast() {
        _toast.value = null
    }

    // ---- Conta ----

    fun onLinked(token: String, user: BackendUser?, device: BackendDevice?) {
        _screen.value = Screen.Settings
        viewModelScope.launch {
            settings.link(token, user?.email, device?.name)
            toast(strings.linked)
        }
    }

    fun unlink() {
        viewModelScope.launch {
            settings.unlink()
            toast(strings.unlinked)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            settings.sync()
            if (session.value != null) toast(strings.synced)
        }
    }

    // ---- Definições ----

    fun setWallpaper(wallpaper: Wallpaper) = settings.updateAccount { it.copy(wallpaper = wallpaper) }

    fun setClock24h(on: Boolean) = settings.updateAccount { it.copy(clock24h = on) }

    fun setShowGreeting(on: Boolean) = settings.updateAccount { it.copy(showGreeting = on) }

    fun setShowContinueWatching(on: Boolean) = settings.updateAccount { it.copy(showContinueWatching = on) }

    fun setShowAppName(on: Boolean) = settings.updateAccount { it.copy(showAppName = on) }

    fun setContinueStyle(style: String) = settings.updateAccount { it.copy(continueStyle = style) }

    val icons = c.icons

    /** Ícone da app [pkg] (ver CustomIcons); null repõe o banner da app. A cópia antiga, se havia, é apagada. */
    fun setCustomIcon(pkg: String, spec: String?) {
        val old = device.value.icons[pkg]
        settings.updateDevice { it.copy(icons = if (spec == null) it.icons - pkg else it.icons + (pkg to spec)) }
        if (old != spec) c.icons.discard(old)
    }

    /** Ícone a partir de uma imagem já desenhada (ex.: devolvida pelo seletor do icon pack). */
    fun setIconFromBitmap(pkg: String, bitmap: Bitmap) {
        viewModelScope.launch { setCustomIcon(pkg, c.icons.saveBitmap(pkg, bitmap)) }
    }

    /** Ícone a partir de uma imagem da box ([source]: File ou Uri do gestor de ficheiros). */
    fun setIconFromImage(pkg: String, source: Any) {
        viewModelScope.launch {
            val bitmap = ImageImport.decode(context, source, 640, 360)
            if (bitmap == null) toast(strings.imageFailed) else setCustomIcon(pkg, c.icons.saveBitmap(pkg, bitmap))
        }
    }

    /** Fundo a partir de uma imagem da box (copiada para a app). */
    fun setWallpaperFromImage(source: Any) {
        viewModelScope.launch {
            val name = ImageImport.saveWallpaper(context, source)
            if (name == null) toast(strings.imageFailed) else setWallpaper(Wallpaper(Wallpaper.TYPE_FILE, name))
        }
    }

    fun setHidden(pkg: String, hidden: Boolean) = settings.updateDevice {
        it.copy(hidden = if (hidden) it.hidden + pkg else it.hidden - pkg)
    }

    /**
     * Move [pkg] [delta] posições na fila visível. Grava a ordem de todas as apps instaladas
     * (as escondidas mantêm o lugar). Só envia para a conta em [finishMove].
     */
    fun moveApp(pkg: String, delta: Int) {
        val all = installed.value ?: return
        settings.updateDevice(push = false) { d ->
            val full = ordered(all, d.apps).map { it.packageName }.toMutableList()
            val visible = full.filterNot { it in d.hidden }
            val from = visible.indexOf(pkg)
            if (from < 0) return@updateDevice d
            val to = (from + delta).coerceIn(visible.indices)
            if (to == from) return@updateDevice d
            val neighbor = visible[to]
            full.remove(pkg)
            val at = full.indexOf(neighbor)
            full.add(if (delta > 0) at + 1 else at, pkg)
            d.copy(apps = full)
        }
    }

    fun finishMove() {
        viewModelScope.launch { settings.pushPending() }
    }

    private companion object {
        /** De quanto em quanto tempo procurar versões novas. */
        const val UPDATE_CHECK_MS = 12 * 60 * 60_000L

        /** Primeiro as apps pela ordem guardada; as que lá não estão vão para o fim (por nome). */
        fun ordered(all: List<App>, order: List<String>): List<App> {
            val byPkg = all.associateBy { it.packageName }
            val first = order.mapNotNull { byPkg[it] }
            val placed = first.mapTo(HashSet()) { it.packageName }
            return first + all.filterNot { it.packageName in placed }
        }
    }
}
