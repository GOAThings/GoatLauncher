package cc.slokker.goatlauncher.ui

import java.util.Calendar
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import coil3.toBitmap
import coil3.request.allowHardware
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
import cc.slokker.goatlauncher.data.ButtonMap
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.AppRelease
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.data.NetworkKind
import cc.slokker.goatlauncher.data.networkKind
import cc.slokker.goatlauncher.data.RedditImage
import cc.slokker.goatlauncher.data.LauncherApp as App
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Updater
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.SunTimes
import java.io.File
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.data.YouTubeLogin
import cc.slokker.goatlauncher.data.api.BackendDevice
import cc.slokker.goatlauncher.data.api.BackendUser
import cc.slokker.goatlauncher.ui.home.CardHeight
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
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

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

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

    /** Todas as apps instaladas, por nome; null enquanto a primeira leitura não acaba. */
    private val installed = MutableStateFlow<List<App>?>(null)
    val allApps: StateFlow<List<App>?> = installed.asStateFlow()

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

    private val _reddit = MutableStateFlow<RedditImage?>(null)

    /** Com o fundo do Reddit: a imagem atual (já carregada na cache, para entrar com um fade sem saltos). */
    val redditImage: StateFlow<RedditImage?> = _reddit.asStateFlow()

    /** Com o fundo "pasta": a imagem dela que está a aparecer agora. */
    private val _folderImage = MutableStateFlow<File?>(null)
    val folderImage: StateFlow<File?> = _folderImage.asStateFlow()

    /** Imagens que não servem de fundo (ao alto, ou que não carregaram). */
    private val badReddit = mutableSetOf<String>()

    /** Largura original de cada foto do Reddit já carregada (para a "resolução mínima"). */
    private val redditWidths = mutableMapOf<String, Int>()

    /**
     * Se cada foto é clara (true) ou escura (false), para o modo dia/noite. Tem de ficar antes do init: a escolha
     * do fundo arranca lá e pode chegar aqui antes de o resto da classe estar inicializado (era o crash no onResume).
     */
    private val redditLight = mutableMapOf<String, Boolean>()

    private val _pinPrompt = MutableStateFlow<PinPrompt?>(null)
    val pinPrompt: StateFlow<PinPrompt?> = _pinPrompt.asStateFlow()

    /** Muda quando o código é acertado (os bloqueios dependem da hora, não de um estado que o ecrã observe). */
    private val _unlocks = MutableStateFlow(0)
    val unlocks: StateFlow<Int> = _unlocks.asStateFlow()

    private val _guardStuck = MutableStateFlow(false)

    /**
     * O serviço do comando ("Manter como ecrã inicial") está ligado nas definições mas o Android não o ligou:
     * no Android 11 fica assim depois de o sistema da Shield reiniciar, e só uma atualização da app o desprende.
     */
    val guardStuck: StateFlow<Boolean> = _guardStuck.asStateFlow()

    /** O launcher está à frente (para o arranque automático não abrir uma app por cima de outra). */
    private var resumed = false

    /** Wi-Fi, cabo ou sem rede (o ícone no canto superior direito). */
    val network: StateFlow<NetworkKind> = networkKind(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkKind.WIFI)

    private val _weather = MutableStateFlow(c.weather.cached())

    /** O tempo agora (null sem dados ou com a meteorologia desligada). */
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()

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

    /** O minuto do dia (desde a meia-noite), de minuto a minuto (para o dia/noite automático). */
    private val minuteOfDay: Flow<Int> = flow {
        while (true) {
            val now = Calendar.getInstance()
            emit(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    /** Onde está a box (pelo IP, a mesma do tempo); null até se saber. */
    private val location = MutableStateFlow(c.weather.cachedLocation())

    /** Nascer e pôr do sol de hoje aqui (minutos desde a meia-noite); null sem localização ou no verão/inverno polar. */
    val sunTimes: StateFlow<Pair<Int, Int>?> = combine(location, minuteOfDay.map { it / 60 }.distinctUntilChanged()) { loc, _ ->
        loc?.let { (lat, lon) -> SunTimes.of(lat, lon, System.currentTimeMillis()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Modo noite em vigor: o escolhido no botão, ou, com o automático ligado, do pôr ao nascer do sol (sem saber
     * onde a box está, das [AccountSettings.NIGHT_FROM] às [AccountSettings.NIGHT_TO]).
     */
    val night: StateFlow<Boolean> = combine(settings.account, minuteOfDay, sunTimes) { a, m, sun ->
        SunTimes.isNight(a, m, sun)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
            // Com o dia/noite automático, a localização para o nascer e o pôr do sol (uma vez por dia, como no tempo).
            settings.account.map { it.nightAuto }.distinctUntilChanged().collectLatest { on ->
                if (on) c.weather.location()?.let { location.value = it }
            }
        }
        viewModelScope.launch {
            // Fundo do Reddit: mostra uma imagem e troca-a a cada RedditWallpapers.ROTATE_MS.
            var lastNight: Boolean? = null
            combine(settings.account, night) { a, n -> Triple(a.wallpaper, a.redditMinWidth, n) }.distinctUntilChanged().collectLatest { (wp, minWidth, night) ->
                if (wp.type != Wallpaper.TYPE_REDDIT) {
                    _reddit.value = null
                    return@collectLatest
                }
                // Ao abrir: a mesma foto da última vez, já (está na cache); a escolha e as verificações vêm depois.
                if (_reddit.value == null) _reddit.value = c.reddit.lastShown(wp.value)
                // Trocar dia/noite pede logo uma foto do tom certo.
                var next = lastNight != null && lastNight != night
                lastNight = night
                while (true) {
                    showReddit(wp.value, next, minWidth, night)
                    delay(c.reddit.msUntilRotation(wp.value))
                    next = true
                }
            }
        }
        viewModelScope.launch {
            // Fundo de uma pasta: uma imagem ao acaso, trocada a cada FOLDER_ROTATE_MS (e ao passar de dia para noite),
            // de preferência do tom certo (clara de dia, escura à noite), como no Reddit.
            combine(settings.account.map { it.wallpaper }, night) { wp, n -> wp to n }.distinctUntilChanged().collectLatest { (wp, night) ->
                if (wp.type != Wallpaper.TYPE_FOLDER) {
                    _folderImage.value = null
                    return@collectLatest
                }
                val dir = File(wp.value)
                while (true) {
                    _folderImage.value = pickFolderImage(dir, night)
                    delay(FOLDER_ROTATE_MS)
                }
            }
        }
        viewModelScope.launch { autostartAfterBoot() }
        viewModelScope.launch {
            // A língua guardada na conta (ou só nesta TV): aplica-se quando as definições já foram lidas, para
            // um "auto" provisório do arranque não desfazer a escolha. Vinda de outra TV, aplica-se aqui também.
            settings.ready.first { it }
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
        viewModelScope.launch {
            // Tempo: ao abrir e de 30 em 30 minutos, enquanto estiver ligado nas definições.
            settings.account.map { it.showWeather }.distinctUntilChanged().collectLatest { on ->
                while (on) {
                    c.weather.current()?.let { _weather.value = it }
                    delay(WEATHER_MS)
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
        resumed = true
        checkGuard()
        reloadApps()
        reloadWatchNext()
        if (SystemClock.elapsedRealtime() - youtubeLoadedAt > YOUTUBE_STALE_MS) viewModelScope.launch { refreshYouTube() }
        viewModelScope.launch { settings.syncIfStale() }
    }

    /** Ver [guardStuck]. Só passado um bocado do arranque: ligar o serviço demora uns segundos. */
    private fun checkGuard() {
        viewModelScope.launch {
            val sinceStart = if (android.os.Build.VERSION.SDK_INT >= 24) {
                SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
            } else {
                GUARD_GRACE_MS
            }
            if (sinceStart < GUARD_GRACE_MS) delay(GUARD_GRACE_MS - sinceStart)
            val stuck = HomeGuardService.isEnabled(context) && HomeGuardService.instance == null
            _guardStuck.value = stuck
            if (!stuck) return@launch
            // Repara-se sozinho: aparece logo a confirmação da reinstalação (um OK; o Android 11 não deixa uma app
            // atualizar-se sem perguntar). Se a pessoa cancelar, só volta a pedir passada uma hora; sem permissão
            // para instalar, fica o aviso (e o OK nas Definições).
            val now = SystemClock.elapsedRealtime()
            if (c.updater.canInstall() && now - lastRepairPrompt > REPAIR_PROMPT_GAP_MS) {
                lastRepairPrompt = now
                toast(strings.guardRepairing)
                repairGuard()
            } else if (now - lastRepairPrompt > REPAIR_PROMPT_GAP_MS) {
                lastRepairPrompt = now
                toast(strings.guardStuckToast)
            }
        }
    }

    private var lastRepairPrompt = -REPAIR_PROMPT_GAP_MS

    /** Reinstala a própria app (a mesma versão): o Android trata-a como atualizada e volta a ligar o serviço. */
    fun repairGuard() {
        if (!c.updater.canInstall()) {
            c.updater.openInstallPermission()
            return
        }
        viewModelScope.launch {
            runCatching { c.updater.install(java.io.File(context.applicationInfo.sourceDir)) }
                .onFailure { toast(strings.updateFailed(it.message ?: it.javaClass.simpleName)) }
        }
    }

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

    fun reloadApps() {
        appRequests.trySend(Unit)
    }

    fun reloadWatchNext() {
        watchNextRequests.trySend(Unit)
    }

    /**
     * Escolhe a imagem do subreddit e só a mostra depois de carregada (deitada e com pelo menos [minWidth] px);
     * senão tenta outra. A atual também é verificada: a resolução mínima pode ter subido.
     */
    private suspend fun showReddit(subreddit: String, next: Boolean, minWidth: Int, night: Boolean) {
        // Uma que sirva mas seja do tom errado (dia/noite) fica de reserva, se não aparecer nenhuma do certo.
        var fallback: RedditImage? = null
        repeat(10) { attempt ->
            val small = redditWidths.filterValues { it < minWidth }.keys
            val wrongTone = redditLight.filterValues { light -> light == night }.keys
            val image = c.reddit.current(subreddit, next = next || attempt > 0, skip = badReddit + small + wrongTone)
                ?: return@repeat
            if (!preloadWallpaper(image.url)) {
                badReddit += image.url
                return@repeat
            }
            if ((redditWidths[image.url] ?: Int.MAX_VALUE) < minWidth) return@repeat
            val light = isLight(image.url)
            if (light != null && light == night) {
                if (fallback == null) fallback = image
                return@repeat
            }
            _reddit.value = image
            return
        }
        fallback?.let { if (it.url != _reddit.value?.url) _reddit.value = it }
    }


    /** Mede a luminosidade média numa cópia de 32×18 (barata); acima de 35% conta como clara. */
    private suspend fun isLight(url: String): Boolean? = isLight(url, url)

    /** Uma imagem da pasta, diferente da atual e do tom certo se houver; null se a pasta não tiver imagens. */
    private suspend fun pickFolderImage(dir: File, night: Boolean): File? {
        val all = ImageImport.images(dir)
        val others = (all - setOfNotNull(_folderImage.value)).shuffled().ifEmpty { all }
        // Mede no máximo umas quantas (cada uma é uma leitura pequena da imagem).
        return others.take(FOLDER_TONE_TRIES).firstOrNull { f -> isLight(f, f.path).let { it == null || it != night } }
            ?: others.firstOrNull()
    }

    private suspend fun isLight(data: Any, key: String): Boolean? = redditLight[key] ?: withContext(Dispatchers.IO) {
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(data).size(32, 18).allowHardware(false).build(),
        )
        val bmp = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        val luma = px.sumOf { c ->
            0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)
        } / (px.size * 255.0)
        luma > LIGHT_LUMA
    }?.also { redditLight[key] = it }

    /** O botão dia/noite: troca o que está agora e desliga o automático. */
    fun toggleNight() {
        val on = !night.value
        settings.updateAccount { it.copy(nightMode = on, nightAuto = false) }
        toast(if (on) strings.nightOn else strings.nightOff)
    }

    fun setNightAuto(on: Boolean) = settings.updateAccount { it.copy(nightAuto = on) }

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
        if (url !in redditWidths) originalWidth(url)?.let { redditWidths[url] = it }
        return image.width > image.height
    }

    /** A largura da foto original (a da cache em disco do Coil), sem a descodificar. */
    private suspend fun originalWidth(url: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            SingletonImageLoader.get(context).diskCache?.openSnapshot(url)?.use { snapshot ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(snapshot.data.toFile().path, options)
                options.outWidth.takeIf { it > 0 }
            }
        }.getOrNull()
    }

    /**
     * Arranque automático: logo depois de a TV ligar, abre a app escolhida (uma vez por arranque, e só se a
     * pessoa ainda estiver no ecrã inicial quando o tempo de espera acabar).
     */
    private suspend fun autostartAfterBoot() {
        settings.ready.first { it }
        val boot = AndroidSettings.Global.getInt(context.contentResolver, AndroidSettings.Global.BOOT_COUNT, -1)
        val prefs = context.getSharedPreferences("autostart", Context.MODE_PRIVATE)
        if (boot < 0 || prefs.getInt("boot", -1) == boot) return
        prefs.edit { putInt("boot", boot) }
        if (SystemClock.elapsedRealtime() > BOOT_WINDOW_MS) return
        val device = settings.device.value
        val pkg = device.autostart ?: return
        delay(device.autostartDelay * 1000L)
        if (resumed && _screen.value == Screen.Home) AutoStart.open(context, pkg)
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

    fun launchApp(activity: Context, pkg: String) = unlocked(pkg) {
        if (!SystemIntents.start(activity, c.apps.launchIntent(pkg))) toast(strings.cantOpen)
    }

    /** Abre o conteúdo; se o `intent_uri` falhar, abre pelo menos a app. */
    fun openWatchNext(activity: Context, item: WatchNextItem) = unlocked(item.packageName) {
        if (SystemIntents.start(activity, item.intent())) return@unlocked
        if (!SystemIntents.start(activity, c.apps.launchIntent(item.packageName))) toast(strings.cantOpen)
    }

    // ---- Controlo parental ----

    /** Uma app bloqueada abriu por outro caminho: volta-se ao Início a pedir o código, e depois abre-se. */
    fun requestUnlock(activity: Context, pkg: String) {
        _screen.value = Screen.Home
        launchApp(activity, pkg)
    }

    /** Corre [action] já, ou depois de acertar o código se [pkg] estiver bloqueada agora. */
    private fun unlocked(pkg: String, action: () -> Unit) {
        if (!ParentalLock.appLocked(settings.device.value, pkg)) return action()
        val label = allApps.value?.firstOrNull { it.packageName == pkg }?.label ?: pkg
        _pinPrompt.value = PinPrompt(strings.unlockApp(label), action)
    }

    /** Corre [action] já, ou depois do código se as Definições e a organização estiverem protegidas. */
    fun withSettingsUnlocked(action: () -> Unit) {
        if (!ParentalLock.settingsLocked(settings.device.value)) return action()
        _pinPrompt.value = PinPrompt(strings.unlockSettings, action)
    }

    /** Corre [action] depois do código (se houver): para entrar nas definições do controlo parental. */
    fun withParentalUnlocked(action: () -> Unit) {
        if (!ParentalLock.parentalLocked(settings.device.value)) return action()
        _pinPrompt.value = PinPrompt(strings.unlockSettings, action)
    }

    fun settingsLocked(): Boolean = ParentalLock.settingsLocked(settings.device.value)

    /** true se o código estiver certo (e aí corre o que estava à espera dele). */
    fun submitPin(pin: String): Boolean {
        val prompt = _pinPrompt.value ?: return false
        if (!ParentalLock.matches(settings.device.value.pin, pin)) return false
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

    fun setAmbientStyle(style: String) = settings.updateAccount { it.copy(ambientStyle = style) }

    fun setRedditMinWidth(px: Int) = settings.updateAccount { it.copy(redditMinWidth = px) }

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

    fun setShowWeather(on: Boolean) = settings.updateAccount { it.copy(showWeather = on) }

    /** Nome da app [pkg] no launcher; null (ou vazio) repõe o nome da app. */
    fun setCustomName(pkg: String, name: String?) = settings.updateDevice {
        val clean = name?.trim().orEmpty()
        it.copy(names = if (clean.isEmpty()) it.names - pkg else it.names + (pkg to clean))
    }

    fun setContinueStyle(style: String) = settings.updateAccount { it.copy(continueStyle = style) }

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
        /** De quanto em quanto tempo procurar versões novas. */
        const val UPDATE_CHECK_MS = 12 * 60 * 60_000L
        /** De quanto em quanto tempo o fundo "pasta" muda de imagem (como o do Reddit). */
        const val FOLDER_ROTATE_MS = 30 * 60_000L
        const val FOLDER_TONE_TRIES = 8
        const val WEATHER_MS = 30 * 60_000L
        const val YOUTUBE_MS = 30 * 60_000L
        const val YOUTUBE_STALE_MS = 10 * 60_000L

        /** O arranque automático só conta nos primeiros minutos depois de ligar (não depois de atualizar a app). */
        const val BOOT_WINDOW_MS = 10 * 60_000L

        const val GUARD_GRACE_MS = 20_000L

        const val LIGHT_LUMA = 0.35

        const val LANGUAGE_DELAY_MS = 400L

        const val REPAIR_PROMPT_GAP_MS = 60 * 60_000L

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
