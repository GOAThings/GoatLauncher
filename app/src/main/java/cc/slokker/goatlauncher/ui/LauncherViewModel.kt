package cc.slokker.goatlauncher.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.slokker.goatlauncher.AutoStart
import cc.slokker.goatlauncher.BuildConfig
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.LauncherApp
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.BugReport
import cc.slokker.goatlauncher.data.ButtonAction
import cc.slokker.goatlauncher.data.ButtonMap
import cc.slokker.goatlauncher.data.ClientInfo
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.data.LauncherApp as App
import cc.slokker.goatlauncher.data.NetworkKind
import cc.slokker.goatlauncher.data.OnlinePhoto
import cc.slokker.goatlauncher.data.ParentalLock
import cc.slokker.goatlauncher.data.Place
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.data.YouTubeLogin
import cc.slokker.goatlauncher.data.AppGrid
import cc.slokker.goatlauncher.data.api.BackendDevice
import cc.slokker.goatlauncher.data.api.BackendUser
import cc.slokker.goatlauncher.data.networkKind
import cc.slokker.goatlauncher.ui.home.HomeMetrics
import cc.slokker.goatlauncher.ui.state.CoilCardPrefetch
import cc.slokker.goatlauncher.ui.state.CoilWallpaperImages
import cc.slokker.goatlauncher.ui.state.ContinueWatching
import cc.slokker.goatlauncher.ui.state.LocalWeather
import cc.slokker.goatlauncher.ui.state.NightClock
import cc.slokker.goatlauncher.ui.state.ReportSender
import cc.slokker.goatlauncher.ui.state.ReportState
import cc.slokker.goatlauncher.ui.state.UpdateState
import cc.slokker.goatlauncher.ui.state.UpdatesController
import cc.slokker.goatlauncher.ui.state.WallpaperRotation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val continueWatching = ContinueWatching(
        scope = viewModelScope,
        source = c.watchNext,
        youtube = c.youtube,
        installed = installed,
        device = settings.device,
        homeApps = { apps.value.orEmpty() },
        youtubeFeed = { settings.account.value.youtubeFeed },
        prefetch = CoilCardPrefetch(context, HomeMetrics.CardHeight.value),
        strings = { strings },
        toast = ::toast,
        demoAllowed = BuildConfig.DEBUG,
    )

    /** O Watch Next de todas as apps, mais as recomendações do YouTube (ver [ContinueWatching]). */
    val watchNext: StateFlow<Map<String, List<WatchNextItem>>> = continueWatching.watchNext
    val youtubeLinked: StateFlow<Boolean> = continueWatching.youtubeLinked

    /** Ligação ao YouTube em curso: o código a mostrar (ou null enquanto se pede). */
    val youtubeLogin: StateFlow<YouTubeLogin?> = continueWatching.youtubeLogin

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

    /** A imagem que roda agora no fundo: a foto online, ou a da pasta (null nos outros fundos). */
    val wallpaperImage: StateFlow<Any?> = combine(onlinePhoto, folderImage) { photo, folder -> photo?.url ?: folder }
        .stateIn(viewModelScope, SharingStarted.Eagerly, onlinePhoto.value?.url ?: folderImage.value)

    override fun onCleared() {
        continueWatching.stop()
    }

    fun onResume() {
        resumed = true
        updates.checkGuard()
        reloadApps()
        continueWatching.onResume()
        // Só sincroniza se já passou o intervalo; espera pela do arranque do processo (ver syncIfStale).
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
        continueWatching.refreshYouTube()
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

    fun reloadWatchNext() = continueWatching.reload()

    /** O botão dia/noite: troca o que está agora e desliga o automático. */
    fun toggleNight() {
        val on = !night.value
        settings.updateAccount { it.copy(nightMode = on, nightAuto = false) }
    }

    /** Só em debug (`am start … --ez demo_watch_next true`): "Continuar a ver" de exemplo nos 2 primeiros favoritos. */
    fun enableDemoWatchNext() = continueWatching.enableDemo()

    // ---- YouTube ----

    /** Pede um código e espera que a pessoa o confirme em google.com/device (pede outro se expirar). */
    fun startYouTubeLogin() = continueWatching.startYouTubeLogin()

    fun cancelYouTubeLogin() = continueWatching.cancelYouTubeLogin()

    fun unlinkYouTube() = continueWatching.unlinkYouTube()

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
        // Acabado o tempo de ecrã de hoje, o código é para qualquer app: diz-se porquê.
        if (c.screenTime.timeUp(settings.device.value)) strings.timeUp
        else strings.unlockApp(allApps.value?.firstOrNull { it.packageName == pkg }?.label ?: pkg)
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
            it.copy(pin = null, locked = emptySet(), lockSettings = false, lockFrom = null, lockTo = null, dailyLimit = null)
        } else {
            it.copy(pin = ParentalLock.encode(pin))
        }
    }

    fun setLocked(pkg: String, on: Boolean) = settings.updateDevice {
        it.copy(locked = if (on) it.locked + pkg else it.locked - pkg)
    }

    // ---- Arranque automático e modo ambiente ----

    fun setAutostart(pkg: String?) = settings.updateDevice { it.copy(autostart = pkg) }

    /** O botão ([keyCode], [scanCode]) passa a abrir [app]; null tira-o. No máximo ButtonMap.MAX. */
    fun setButton(keyCode: Int, scanCode: Int, app: String?) =
        setButton(keyCode, scanCode, app?.let { ButtonMap(keyCode, scanCode, it) })

    /** O botão ([keyCode], [scanCode]) passa a fazer [action]. */
    fun setButtonAction(keyCode: Int, scanCode: Int, action: ButtonAction) =
        setButton(keyCode, scanCode, ButtonMap(keyCode, scanCode, "", action))

    private fun setButton(keyCode: Int, scanCode: Int, button: ButtonMap?) = settings.updateDevice { d ->
        val rest = d.buttons.filterNot { it.keyCode == keyCode && it.scanCode == scanCode }
        val index = d.buttons.indexOfFirst { it.keyCode == keyCode && it.scanCode == scanCode }
        val buttons = when {
            button == null -> rest
            index >= 0 -> d.buttons.toMutableList().also { it[index] = button }
            d.buttons.size >= ButtonMap.MAX -> d.buttons
            else -> d.buttons + button
        }
        d.copy(buttons = buttons)
    }

    fun setLocation(place: Place?) = settings.updateDevice { it.copy(location = place) }

    suspend fun searchPlaces(query: String): List<Place> = c.weather.searchPlaces(query, Strings.normalize(account.value.language).let { if (it == "auto") java.util.Locale.getDefault().language else it.substringBefore('-') })

    /** A cidade que o IP diz (para mostrar em "Automática"). */
    fun ipCity(): String? = c.weather.ipCity()

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

    /**
     * Muda as definições da conta (iguais em todas as TVs, sincronizadas). As linhas das Definições escrevem por aqui
     * (ver SettingsCatalog): uma definição nova não precisa de um setter seu.
     */
    fun updateAccount(change: (AccountSettings) -> AccountSettings) = settings.updateAccount(change)

    /** Muda as definições só desta TV (ver [updateAccount]). */
    fun updateDevice(change: (DeviceSettings) -> DeviceSettings) = settings.updateDevice(transform = change)

    /** Nome da app [pkg] no launcher; null (ou vazio) repõe o nome da app. */
    fun setCustomName(pkg: String, name: String?) = settings.updateDevice {
        val clean = name?.trim().orEmpty()
        it.copy(names = if (clean.isEmpty()) it.names - pkg else it.names + (pkg to clean))
    }

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
     * Move [pkg] [delta] lugares na grelha das apps ([AppGrid]: os favoritos e depois todas as outras, também as
     * escondidas). Grava a ordem de todas as apps; só envia para a conta em [finishMove].
     */
    fun moveApp(pkg: String, delta: Int) = changeGrid { order, hidden -> AppGrid.move(order, hidden, pkg, delta) }

    /** Tira [pkg] dos favoritos (a primeira app de baixo sobe para o lugar) e envia logo. */
    fun unfavorite(pkg: String) {
        changeGrid { order, hidden -> AppGrid.unfavorite(order, hidden, pkg) }
        finishMove()
    }

    /** Põe [pkg] no último lugar dos favoritos; o ecrã fica a mover a app e envia no fim ([finishMove]). */
    fun favorite(pkg: String) = changeGrid { order, hidden -> AppGrid.favorite(order, hidden, pkg) }

    private fun changeGrid(change: (order: List<String>, hidden: Set<String>) -> Pair<List<String>, Set<String>>?) {
        val all = installed.value ?: return
        settings.updateDevice(push = false) { d ->
            val (apps, hidden) = change(ordered(all, d.apps).map { it.packageName }, d.hidden) ?: return@updateDevice d
            d.copy(apps = apps, hidden = hidden)
        }
    }

    fun finishMove() {
        viewModelScope.launch { settings.pushPending() }
    }

    /**
     * O arranque do ecrã, por esta ordem: as definições (esperar pela sincronização do arranque do processo e, já com
     * o token renovado, o índice das apps para o site), o "Continuar a ver", o tempo, o fundo, o arranque
     * automático, a língua, as atualizações e as apps instaladas.
     */
    private fun start() {
        viewModelScope.launch {
            // A sincronização do arranque é do processo (LauncherApp): um ecrã novo não faz outra.
            settings.startSync().join()
            // Para o site da conta, já com o token renovado (só envia quando a lista mudou). Cada leitura das apps
            // também envia (ver AppContainer.start).
            c.appIndex.upload(c.catalog.current())
        }
        continueWatching.start()
        localWeather.start()
        wallpaper.start()
        viewModelScope.launch { AutoStart.afterBoot(context) { resumed && _screen.value == Screen.Home } }
        viewModelScope.launch {
            // A língua guardada na conta (ou só nesta TV): aplica-se quando as definições já foram lidas, para
            // um "auto" provisório do arranque não desfazer a escolha. Vinda de outra TV, aplica-se aqui também.
            settings.awaitReady()
            settings.account.map { it.language }.distinctUntilChanged().collect { Strings.apply(it) }
        }
        updates.start()
        reloadApps()
    }

    /**
     * Fica depois de todos os campos, de propósito: o viewModelScope corre já (Main.immediate) o que se lança, até à
     * primeira espera, e um caminho sem espera (ex.: uma foto já na cache) chegava a campos ainda por inicializar (o
     * crash do f3b4c2b). Ao serem construídos, os holders só preparam estado derivado dos campos anteriores (stateIn);
     * o que corre lança-se no start().
     */
    init {
        start()
    }

    private companion object {

        const val LANGUAGE_DELAY_MS = 400L

        /** Primeiro as apps pela ordem guardada; as que lá não estão vão para o fim (por nome). */
        fun ordered(all: List<App>, order: List<String>): List<App> {
            val byPkg = all.associateBy { it.packageName }
            val first = order.mapNotNull { byPkg[it] }
            val placed = first.mapTo(HashSet()) { it.packageName }
            return first + all.filterNot { it.packageName in placed }
        }
    }
}

