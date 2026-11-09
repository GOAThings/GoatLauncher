package cc.slokker.goatlauncher.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.slokker.goatlauncher.BuildConfig
import cc.slokker.goatlauncher.data.BugReport
import cc.slokker.goatlauncher.data.ClientInfo
import cc.slokker.goatlauncher.LauncherApp
import cc.slokker.goatlauncher.data.ButtonMap
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.ui.state.LocalWeather
import cc.slokker.goatlauncher.ui.state.NightClock
import cc.slokker.goatlauncher.ui.state.ReportSender
import cc.slokker.goatlauncher.ui.state.ReportState
import cc.slokker.goatlauncher.ui.state.UpdateState
import cc.slokker.goatlauncher.ui.state.UpdatesController
import cc.slokker.goatlauncher.ui.state.CoilWallpaperImages
import cc.slokker.goatlauncher.ui.state.WallpaperRotation
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.data.NetworkKind
import cc.slokker.goatlauncher.data.networkKind
import cc.slokker.goatlauncher.data.OnlinePhoto
import cc.slokker.goatlauncher.data.LauncherApp as App
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.Place
import java.io.File
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.data.YouTubeLogin
import cc.slokker.goatlauncher.data.api.BackendDevice
import cc.slokker.goatlauncher.data.api.BackendUser
import cc.slokker.goatlauncher.ui.home.CardHeight
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.Settings as AndroidSettings
import androidx.core.content.edit
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.AutoStart
import cc.slokker.goatlauncher.data.ParentalLock
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

/** Pedido do código do controlo parental: o ecrã mostra o teclado e [onSuccess] corre quando acertar. */
class PinPrompt(val title: String, val onSuccess: () -> Unit)

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
    /** Textos na língua da app, para fora do ecrã (avisos); o ecrã usa os do contexto da Activity. */
    val strings: Strings get() = Strings.of(context)

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    val account: StateFlow<AccountSettings> = settings.account
    val device: StateFlow<DeviceSettings> = settings.device
    val session: StateFlow<SessionStore.Session?> = settings.session
    val ready: StateFlow<Boolean> = settings.ready

    /** Todas as apps instaladas, por nome; null enquanto a primeira leitura não acaba (ver AppCatalog). */
    private val installed: StateFlow<List<App>?> = c.catalog.apps
    val allApps: StateFlow<List<App>?> = installed

    /** A fila do ecrã inicial: pela ordem guardada, sem as escondidas. */
    val apps: StateFlow<List<App>?> = combine(installed, settings.device) { all, d ->
        all?.let {
            ordered(it, d.apps)
                .filterNot { app -> app.packageName in d.hidden }
                .map { app -> d.names[app.packageName]?.let(app::withLabel) ?: app }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Todas as apps para o separador Apps: pela ordem guardada, também as escondidas do Início. */
    val appsTab: StateFlow<List<App>?> = combine(installed, settings.device) { all, d ->
        all?.let { ordered(it, d.apps).map { app -> d.names[app.packageName]?.let(app::withLabel) ?: app } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** O "Continuar a ver" de todas as apps, por pacote. Mudar o foco só filtra isto (sem consultas novas). */
    private val _watchNext = MutableStateFlow<Map<String, List<WatchNextItem>>>(emptyMap())

    /** Recomendações do YouTube (conta ligada em Definições → YouTube), com o pacote da app que as abre. */
    private val _youtube = MutableStateFlow<List<WatchNextItem>>(emptyList())

    /** O Watch Next de todas as apps, mais as recomendações do YouTube na app de YouTube instalada. */
    val watchNext: StateFlow<Map<String, List<WatchNextItem>>> = combine(_watchNext, _youtube, settings.device) { system, yt, d ->
        val all = if (yt.isEmpty()) system else system + (yt.first().packageName to (system[yt.first().packageName].orEmpty() + yt))
        // Apps em que a pessoa desligou o "Continuar a ver" (menu da app).
        all - d.noContinue
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _youtubeLinked = MutableStateFlow(c.youtube.linked)
    val youtubeLinked: StateFlow<Boolean> = _youtubeLinked.asStateFlow()

    /** Ligação ao YouTube em curso: o código a mostrar (ou null enquanto se pede), e erros. */
    private val _youtubeLogin = MutableStateFlow<YouTubeLogin?>(null)
    val youtubeLogin: StateFlow<YouTubeLogin?> = _youtubeLogin.asStateFlow()
    private var youtubeLoginJob: Job? = null
    private var youtubeLoadedAt = 0L

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Botão Home com o launcher já aberto: volta ao início da fila. */
    val homeEvents: SharedFlow<Unit> = _homeEvents

    /** A última app com foco no ecrã inicial (ao voltar das definições, o foco regressa a ela). */
    var focusedPackage: String? = null

    private val _pinPrompt = MutableStateFlow<PinPrompt?>(null)
    val pinPrompt: StateFlow<PinPrompt?> = _pinPrompt.asStateFlow()

    /** Muda quando o código é acertado (os bloqueios dependem da hora, não de um estado que o ecrã observe). */
    private val _unlocks = MutableStateFlow(0)
    val unlocks: StateFlow<Int> = _unlocks.asStateFlow()

    /** O launcher está à frente (para o arranque automático não abrir uma app por cima de outra). */
    private var resumed = false

    /** Wi-Fi, cabo ou sem rede (o ícone no canto superior direito). */
    val network: StateFlow<NetworkKind> = networkKind(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkKind.WIFI)

    private val localWeather = LocalWeather(viewModelScope, c.weather, settings.account, settings.device)

    /** O tempo agora (null sem dados ou com a meteorologia desligada). */
    val weather: StateFlow<WeatherNow?> = localWeather.weather

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val updates = UpdatesController(
        scope = viewModelScope,
        updater = c.updater,
        strings = { strings },
        toast = ::toast,
        isGuardStuck = { HomeGuardService.isEnabled(context) && HomeGuardService.instance == null },
        sinceProcessStart = {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
            } else {
                UpdatesController.GUARD_GRACE_MS
            }
        },
        ownApk = { File(context.applicationInfo.sourceDir) },
    )
    val update: StateFlow<UpdateState> = updates.state
    val appVersion: String get() = updates.currentVersion

    /** Ver [UpdatesController.guardStuck]. */
    val guardStuck: StateFlow<Boolean> = updates.guardStuck

    private val reports = ReportSender(
        scope = viewModelScope,
        backend = c.backend,
        client = ClientInfo.current,
        device = BugReport.device,
        session = settings.session,
        layout = { account.value.layout },
        youtubeSecrets = c.youtube::secrets,
        strings = { strings },
        collectLog = { header, secrets -> withContext(Dispatchers.IO) { BugReport.collect(context, header, secrets) } },
    )
    val report: StateFlow<ReportState> = reports.state

    private val watchNextRequests = Channel<Unit>(Channel.CONFLATED)
    private val stopObservingWatchNext: () -> Unit

    private val nightClock = NightClock(settings.account, localWeather.location)

    /** Ver [NightClock.sunTimes]. */
    val sunTimes: StateFlow<Pair<Int, Int>?> = nightClock.sunTimes.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Modo noite em vigor (ver [NightClock.night]). */
    val night: StateFlow<Boolean> = nightClock.night.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val wallpaper = WallpaperRotation(viewModelScope, settings.account, night, c.wallpapers::source, CoilWallpaperImages(context))

    /** Com um fundo de fotos online (Wikimedia, NASA): a foto atual (já carregada na cache, para entrar com um fade sem saltos). */
    val onlinePhoto: StateFlow<OnlinePhoto?> = wallpaper.onlinePhoto

    /** Com o fundo "pasta": a imagem dela que está a aparecer agora. */
    val folderImage: StateFlow<File?> = wallpaper.folderImage

    init {
        viewModelScope.launch {
            settings.awaitReady()
            settings.sync(refreshToken = true)
            // Para o site da conta, já com o token renovado (só envia quando a lista mudou). Cada leitura das apps
            // também envia (ver AppContainer.start).
            c.appIndex.upload(c.catalog.current())
        }
        viewModelScope.launch {
            // As apps escrevem no Watch Next várias vezes seguidas: junta as mudanças antes de ler.
            watchNextRequests.receiveAsFlow().collectLatest {
                delay(300)
                // Se a leitura falhar, ficam as entradas que já havia.
                val all = (c.watchNext.load() ?: return@collectLatest).let { if (demo) it + demoWatchNext() else it }
                _watchNext.value = all
                prefetchImages(all)
            }
        }
        localWeather.start()
        wallpaper.start()
        viewModelScope.launch { autostartAfterBoot() }
        viewModelScope.launch {
            // A língua guardada na conta (ou só nesta TV): aplica-se quando as definições já foram lidas, para
            // um "auto" provisório do arranque não desfazer a escolha. Vinda de outra TV, aplica-se aqui também.
            settings.awaitReady()
            settings.account.map { it.language }.distinctUntilChanged().collect { Strings.apply(it) }
        }
        viewModelScope.launch {
            // YouTube: quando já se sabe que apps há, e de 30 em 30 minutos.
            installed.first { it != null }
            while (true) {
                refreshYouTube()
                delay(YOUTUBE_MS)
            }
        }
        updates.start()
        reloadApps()
        reloadWatchNext()
        stopObservingWatchNext = c.watchNext.observe(::reloadWatchNext)
    }

    override fun onCleared() {
        stopObservingWatchNext()
    }

    fun onResume() {
        resumed = true
        updates.checkGuard()
        reloadApps()
        reloadWatchNext()
        if (SystemClock.elapsedRealtime() - youtubeLoadedAt > YOUTUBE_STALE_MS) viewModelScope.launch { refreshYouTube() }
        viewModelScope.launch { settings.syncIfStale() }
    }

    /** Reinstala a própria app (ver [UpdatesController.repairGuard]). */
    fun repairGuard() = updates.repairGuard()

    /** "auto" ou um código de Strings.LANGUAGES; fica na conta e chega às outras TVs. */
    fun setLanguage(code: String) {
        // Mudar a língua recria o ecrã: espera que o OK acabe de ser largado, senão o "largar" chega ao ecrã
        // novo como um clique noutra linha.
        viewModelScope.launch {
            delay(LANGUAGE_DELAY_MS)
            settings.updateAccount { it.copy(language = Strings.normalize(code)) }
        }
    }

    fun setYoutubeFeed(feed: String) {
        settings.updateAccount { it.copy(youtubeFeed = feed) }
        viewModelScope.launch { refreshYouTube() }
    }

    fun onPause() {
        resumed = false
    }

    fun onHomePressed() {
        _screen.value = Screen.Home
        _homeEvents.tryEmit(Unit)
    }

    /** As apps instaladas mudam sozinhas (o AppCatalog está à escuta); isto apanha o que tenha escapado. */
    fun reloadApps() {
        c.catalog.refresh()
    }

    fun reloadWatchNext() {
        watchNextRequests.trySend(Unit)
    }

    /** O botão dia/noite: troca o que está agora e desliga o automático. */
    fun toggleNight() {
        val on = !night.value
        settings.updateAccount { it.copy(nightMode = on, nightAuto = false) }
    }

    fun setNightAuto(on: Boolean) = settings.updateAccount { it.copy(nightAuto = on) }

    /**
     * Arranque automático: logo depois de a TV ligar, abre a app escolhida (uma vez por arranque, e só se a
     * pessoa ainda estiver no ecrã inicial quando o tempo de espera acabar).
     */
    private suspend fun autostartAfterBoot() {
        settings.awaitReady()
        val boot = AndroidSettings.Global.getInt(context.contentResolver, AndroidSettings.Global.BOOT_COUNT, -1)
        val prefs = context.getSharedPreferences(AutoStart.BOOT_PREFS, Context.MODE_PRIVATE)
        if (boot < 0 || prefs.getInt(AutoStart.BOOT_KEY, -1) == boot) return
        prefs.edit { putInt(AutoStart.BOOT_KEY, boot) }
        if (SystemClock.elapsedRealtime() > BOOT_WINDOW_MS) return
        val device = settings.device.value
        val pkg = device.autostart ?: return
        delay(device.autostartDelay * 1000L)
        if (resumed && _screen.value == Screen.Home) AutoStart.open(context, pkg)
    }

    private val prefetches = HashMap<String, List<Disposable>>()

    /**
     * Pede já as imagens dos primeiros cartões de cada app (para a cache do Coil), no tamanho em que aparecem: ao mudar
     * de app, os cartões aparecem logo em vez de carregarem um a um. Um novo pedido substitui o anterior dessa app.
     */
    private fun prefetchImages(all: Map<String, List<WatchNextItem>>) {
        val loader = SingletonImageLoader.get(context)
        val height = (CardHeight.value * context.resources.displayMetrics.density).toInt()
        for ((pkg, items) in all) {
            prefetches.remove(pkg)?.forEach { it.dispose() }
            prefetches[pkg] = items.filter { it.imageUri != null }.take(PREFETCH_PER_APP).map { item ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(item.imageUri)
                        .size((height * item.aspectRatio.coerceIn(0.6f, 2f)).toInt(), height)
                        .build(),
                )
            }
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

    // ---- YouTube ----

    /** A app que abre os vídeos (TizenTube, a app oficial, SmartTube…), a primeira instalada da lista. */
    private fun youtubeApp(): String? {
        val pkgs = installed.value.orEmpty().map { it.packageName }.toSet()
        return YOUTUBE_APPS.firstOrNull { it in pkgs }
    }

    private suspend fun refreshYouTube() {
        val pkg = youtubeApp()
        if (!c.youtube.linked || pkg == null) {
            _youtube.value = emptyList()
            return
        }
        try {
            val items = c.youtube.recommendations(pkg, settings.account.value.youtubeFeed)
            Log.d("YouTube", "Recomendações: ${items.size}")
            _youtube.value = items
            youtubeLoadedAt = SystemClock.elapsedRealtime()
            prefetchImages(mapOf(pkg to items))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("YouTube", "Recomendações: ${e.message}")
        }
        _youtubeLinked.value = c.youtube.linked
    }

    /** Pede um código e espera que a pessoa o confirme em google.com/device (pede outro se expirar). */
    fun startYouTubeLogin() {
        youtubeLoginJob?.cancel()
        youtubeLoginJob = viewModelScope.launch {
            _youtubeLogin.value = null
            while (true) {
                val login = try {
                    c.youtube.startLogin()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(strings.youtubeError)
                    delay(10_000)
                    continue
                }
                _youtubeLogin.value = login
                while (System.currentTimeMillis() < login.expiresAt) {
                    delay(login.intervalSec * 1000L)
                    val done = runCatching { c.youtube.poll(login) }.getOrNull() ?: continue
                    if (done) {
                        _youtubeLinked.value = true
                        _youtubeLogin.value = null
                        toast(strings.youtubeLinked)
                        refreshYouTube()
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
        c.youtube.unlink()
        _youtubeLinked.value = false
        _youtube.value = emptyList()
    }

    // ---- Relatórios ----

    /** Ver [ReportSender]. */
    fun sendReport(message: String) = reports.send(message)

    // ---- Atualizações ----

    fun checkForUpdate(manual: Boolean) = updates.check(manual)

    /** Descarrega e instala a versão encontrada (ou procura, se ainda não houver). */
    fun installUpdate() = updates.install()

    // ---- Abrir ----

    fun launchApp(activity: Context, pkg: String) = unlocked(pkg) {
        if (!SystemIntents.start(activity, c.catalog.launchIntent(pkg))) toast(strings.cantOpen)
    }

    /** Abre o conteúdo; se o `intent_uri` falhar, abre pelo menos a app. */
    fun openWatchNext(activity: Context, item: WatchNextItem) = unlocked(item.packageName) {
        if (SystemIntents.start(activity, item.intent())) return@unlocked
        if (!SystemIntents.start(activity, c.catalog.launchIntent(item.packageName))) toast(strings.cantOpen)
    }

    // ---- Controlo parental ----

    /** Uma app bloqueada abriu por outro caminho: volta-se ao Início a pedir o código, e depois abre-se. */
    fun requestUnlock(activity: Context, pkg: String) {
        _screen.value = Screen.Home
        launchApp(activity, pkg)
    }

    /**
     * Corre [action] já se [locked] disser que não, senão depois de acertar o código ([title]). Com as definições lidas
     * decide logo, no mesmo clique (o viewModelScope corre já no thread principal); por ler, espera por elas (ver
     * ParentalGate): "por ler" nunca conta como "sem código".
     */
    private fun gated(locked: suspend () -> Boolean, title: () -> String, action: () -> Unit) {
        viewModelScope.launch {
            if (!locked()) action() else _pinPrompt.value = PinPrompt(title(), action)
        }
    }

    /** Corre [action] já, ou depois de acertar o código se [pkg] estiver bloqueada agora. */
    private fun unlocked(pkg: String, action: () -> Unit) = gated({ c.parental.appLocked(pkg) }, {
        strings.unlockApp(allApps.value?.firstOrNull { it.packageName == pkg }?.label ?: pkg)
    }, action)

    /** Corre [action] já, ou depois do código se as Definições e a organização estiverem protegidas. */
    fun withSettingsUnlocked(action: () -> Unit) = gated(c.parental::settingsLocked, { strings.unlockSettings }, action)

    /** Corre [action] depois do código (se houver): para entrar nas definições do controlo parental. */
    fun withParentalUnlocked(action: () -> Unit) = gated(c.parental::parentalLocked, { strings.unlockSettings }, action)

    fun settingsLocked(): Boolean = ParentalLock.settingsLocked(settings.device.value)

    /** Quanto falta para se poder tentar o código outra vez (0 = já). */
    fun pinWaitMs(): Long = c.pinAttempts.waitMs()

    /** true se o código estiver certo (e aí corre o que estava à espera dele). À espera, nem se compara. */
    fun submitPin(pin: String): Boolean {
        val prompt = _pinPrompt.value ?: return false
        if (c.pinAttempts.waitMs() > 0) return false
        if (!ParentalLock.matches(settings.device.value.pin, pin)) {
            c.pinAttempts.failed()
            return false
        }
        c.pinAttempts.reset()
        ParentalLock.grant()
        _unlocks.value++
        _pinPrompt.value = null
        prompt.onSuccess()
        return true
    }

    fun cancelPin() {
        _pinPrompt.value = null
    }

    /** Novo código (o primeiro liga o controlo parental); null desliga tudo. */
    fun setPin(pin: String?) = settings.updateDevice {
        if (pin == null) {
            it.copy(pin = null, locked = emptySet(), lockSettings = false, lockFrom = null, lockTo = null)
        } else {
            it.copy(pin = ParentalLock.encode(pin))
        }
    }

    fun setLocked(pkg: String, on: Boolean) = settings.updateDevice {
        it.copy(locked = if (on) it.locked + pkg else it.locked - pkg)
    }

    fun setLockSettings(on: Boolean) = settings.updateDevice { it.copy(lockSettings = on) }

    fun setLockHours(from: Int?, to: Int?) = settings.updateDevice { it.copy(lockFrom = from, lockTo = to) }

    // ---- Arranque automático e modo ambiente ----

    fun setAutostart(pkg: String?) = settings.updateDevice { it.copy(autostart = pkg) }

    /** O botão ([keyCode], [scanCode]) passa a abrir [app]; null tira-o. No máximo ButtonMap.MAX. */
    fun setButton(keyCode: Int, scanCode: Int, app: String?) = settings.updateDevice { d ->
        val rest = d.buttons.filterNot { it.keyCode == keyCode && it.scanCode == scanCode }
        val index = d.buttons.indexOfFirst { it.keyCode == keyCode && it.scanCode == scanCode }
        val buttons = when {
            app == null -> rest
            index >= 0 -> d.buttons.toMutableList().also { it[index] = ButtonMap(keyCode, scanCode, app) }
            d.buttons.size >= ButtonMap.MAX -> d.buttons
            else -> d.buttons + ButtonMap(keyCode, scanCode, app)
        }
        d.copy(buttons = buttons)
    }

    fun setAutostartWake(on: Boolean) = settings.updateDevice { it.copy(autostartWake = on) }

    fun setAutostartDelay(seconds: Int) = settings.updateDevice { it.copy(autostartDelay = seconds) }

    fun setIdleMinutes(minutes: Int) = settings.updateAccount { it.copy(idleMinutes = minutes) }

    fun setIdleKeepAwake(on: Boolean) = settings.updateAccount { it.copy(idleKeepAwake = on) }

    fun setLocation(place: Place?) = settings.updateDevice { it.copy(location = place) }

    suspend fun searchPlaces(query: String): List<Place> = c.weather.searchPlaces(query, Strings.normalize(account.value.language).let { if (it == "auto") java.util.Locale.getDefault().language else it.substringBefore('-') })

    /** A cidade que o IP diz (para mostrar em "Automática"). */
    fun ipCity(): String? = c.weather.ipCity()

    fun setAmbientStyle(style: String) = settings.updateAccount { it.copy(ambientStyle = style) }

    fun setNavSounds(on: Boolean) = settings.updateAccount { it.copy(navSounds = on) }

    fun setPhotoMinWidth(px: Int) = settings.updateAccount { it.copy(photoMinWidth = px) }
    fun setWallpaperMinutes(minutes: Int) = settings.updateAccount { it.copy(wallpaperMinutes = minutes) }

    // ---- Navegação ----

    fun openHome() {
        _screen.value = Screen.Home
    }

    fun openApps() {
        if (account.value.layout == AccountSettings.LAYOUT_APPLE_TV) return
        _screen.value = Screen.Apps
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun closeSettings() {
        _screen.value = Screen.Home
    }

    /** Ligar a outra conta mexe no controlo parental: pede o código, se houver. */
    fun openActivation() = withParentalUnlocked {
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
            c.scope.launch { c.appIndex.upload(c.catalog.current(), afterLink = true) }
            toast(strings.linked)
        }
    }

    /**
     * "Desligar conta" pedido pela pessoa: avisa o servidor (que apaga esta TV da conta) e esquece a conta aqui.
     * Quando a conta se perde sozinha (401/404, ver SettingsRepository) não se avisa.
     */
    fun unlink() = withParentalUnlocked {
        // O token antes de a sessão local ser apagada; o aviso não espera nem impede nada (ver logoutDevice).
        settings.session.value?.token?.let(c::logoutDevice)
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

    fun setShowWeather(on: Boolean) = settings.updateAccount { it.copy(showWeather = on) }

    /** Nome da app [pkg] no launcher; null (ou vazio) repõe o nome da app. */
    fun setCustomName(pkg: String, name: String?) = settings.updateDevice {
        val clean = name?.trim().orEmpty()
        it.copy(names = if (clean.isEmpty()) it.names - pkg else it.names + (pkg to clean))
    }

    /** Escolher um estilo também volta a ligar o "Continuar a ver" (se estava desligado). */
    fun setContinueStyle(style: String) = settings.updateAccount { it.copy(continueStyle = style, showContinueWatching = true) }

    fun setFocusEffect(effect: String) = settings.updateAccount { it.copy(focusEffect = effect) }

    fun setFocusAnimation(animation: String) = settings.updateAccount { it.copy(focusAnimation = animation) }

    /** AccountSettings.COLOR_AUTO ou "#RRGGBB". */
    fun setFocusColor(color: String) = settings.updateAccount { it.copy(focusColor = color) }

    /** AccountSettings.BG_GRAPHITE, BG_NONE ou "#RRGGBB". */
    fun setIconBackground(spec: String) = settings.updateAccount { it.copy(iconBackground = spec) }

    /** Disposição do ecrã inicial (AccountSettings.LAYOUT_*). A Apple TV não tem separador Apps. */
    fun setLayout(layout: String) = settings.updateAccount { it.copy(layout = layout) }

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
    fun setWallpaperFolder(dir: File) = setWallpaper(Wallpaper(Wallpaper.TYPE_FOLDER, dir.path))

    fun setWallpaperFromImage(source: Any) {
        viewModelScope.launch {
            val name = ImageImport.saveWallpaper(context, source)
            if (name == null) toast(strings.imageFailed) else setWallpaper(Wallpaper(Wallpaper.TYPE_FILE, name))
        }
    }

    fun setShowContinue(pkg: String, show: Boolean) = settings.updateDevice {
        it.copy(noContinue = if (show) it.noContinue - pkg else it.noContinue + pkg)
    }

    fun setHidden(pkg: String, hidden: Boolean) = settings.updateDevice {
        it.copy(hidden = if (hidden) it.hidden + pkg else it.hidden - pkg)
    }

    /**
     * Move [pkg] [delta] lugares na grelha das apps, tal como se vê no separador Apps e na Apple TV: os [favorites]
     * favoritos e depois todas as outras, também as escondidas. A app vai para o novo lugar e as do meio chegam-se
     * (descer um favorito traz para os favoritos a primeira app de baixo). Uma app escondida que entre nos
     * favoritos volta a aparecer no Início. Grava a ordem de todas as apps; só envia para a conta em [finishMove].
     */
    fun moveApp(pkg: String, delta: Int, favorites: Int) {
        val all = installed.value ?: return
        settings.updateDevice(push = false) { d ->
            val order = ordered(all, d.apps).map { it.packageName }
            val fav = order.filterNot { it in d.hidden }.take(favorites)
            val grid = (fav + order.filterNot { it in fav }).toMutableList()
            val from = grid.indexOf(pkg)
            if (from < 0) return@updateDevice d
            val to = (from + delta).coerceIn(grid.indices)
            if (to != from) {
                grid.removeAt(from)
                grid.add(to, pkg)
            }
            d.copy(apps = grid, hidden = d.hidden - grid.take(favorites).toSet())
        }
    }

    fun finishMove() {
        viewModelScope.launch { settings.pushPending() }
    }

    private companion object {
        const val YOUTUBE_MS = 30 * 60_000L
        /** Os cartões que se veem de uma vez numa fila; os outros carregam ao chegar lá. */
        const val PREFETCH_PER_APP = 8
        const val YOUTUBE_STALE_MS = 10 * 60_000L

        /** O arranque automático só conta nos primeiros minutos depois de ligar (não depois de atualizar a app). */
        const val BOOT_WINDOW_MS = 10 * 60_000L

        const val LANGUAGE_DELAY_MS = 400L

        /** Apps que abrem links do YouTube, por ordem de preferência. */
        val YOUTUBE_APPS = listOf(
            "io.gh.reisxd.tizentube.cobalt",
            "io.gh.reisxd.tizentube",
            "com.google.android.youtube.tv",
            "com.teamsmart.videomanager.tv",
            "com.liskovsoft.smarttubetv.beta",
        )

        /** Primeiro as apps pela ordem guardada; as que lá não estão vão para o fim (por nome). */
        fun ordered(all: List<App>, order: List<String>): List<App> {
            val byPkg = all.associateBy { it.packageName }
            val first = order.mapNotNull { byPkg[it] }
            val placed = first.mapTo(HashSet()) { it.packageName }
            return first + all.filterNot { it.packageName in placed }
        }
    }
}

