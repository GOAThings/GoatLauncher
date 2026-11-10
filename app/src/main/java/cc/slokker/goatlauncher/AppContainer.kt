package cc.slokker.goatlauncher

import android.content.Context
import cc.slokker.goatlauncher.data.AppCatalog
import cc.slokker.goatlauncher.data.AppIndex
import cc.slokker.goatlauncher.data.ClientInfo
import cc.slokker.goatlauncher.data.CustomIcons
import cc.slokker.goatlauncher.data.DataStoreSettingsDisk
import cc.slokker.goatlauncher.data.ParentalGate
import cc.slokker.goatlauncher.data.ScreenTime
import cc.slokker.goatlauncher.data.PhoneApps
import cc.slokker.goatlauncher.data.PinAttemptStore
import cc.slokker.goatlauncher.data.OnlineWallpapers
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.SettingsRepository
import cc.slokker.goatlauncher.data.Updater
import cc.slokker.goatlauncher.data.WeatherSource
import cc.slokker.goatlauncher.data.YouTubeSource
import cc.slokker.goatlauncher.data.asChosen
import cc.slokker.goatlauncher.data.WatchNextSource
import cc.slokker.goatlauncher.data.api.BACKEND_URL
import cc.slokker.goatlauncher.data.api.BackendApi
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

/** O logout no servidor é só um aviso: não vale a pena esperar mais do que isto. */
private const val LOGOUT_TIMEOUT_MS = 5_000L

/** Dependências da app (injeção manual). */
class AppContainer(context: Context) {

    /** Vive tanto quanto o processo: gravações e envios à conta não param ao sair de um ecrã. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** A sessão na conta Slokker: o token que o interceptor junta a cada pedido vem daqui (ver SessionStore). */
    private val sessions = SessionStore(context)

    private val client = ClientInfo.current.goatClient

    private val backendHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Um pedido que já traz o seu token (o logout) fica com ele.
            val current = sessions.token.takeIf { chain.request().header("Authorization") == null }
            // Sem "Bearer": o backend espera o token tal como está. O X-Goat-Client diz ao site a app, a versão e o
            // tipo de TV (aparece em Dispositivos).
            val request = chain.request().newBuilder().header("X-Goat-Client", client)
            if (current != null) request.header("Authorization", current)
            chain.proceed(request.build())
        }
        .build()

    val backend: BackendApi = Retrofit.Builder()
        .baseUrl(BACKEND_URL)
        .client(backendHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create()

    val settings = SettingsRepository(DataStoreSettingsDisk(context), backend, sessions, scope)
    /** Se uma app, as Definições ou o controlo parental pedem o código (sempre com as definições lidas). */
    /** O tempo de ecrã de hoje (só nesta TV), para o limite por dia do controlo parental. */
    val screenTime = ScreenTime(context)
    val parental = ParentalGate(settings, screenTime::timeUp)
    val pinAttempts = PinAttemptStore(context)
    /** As apps instaladas, para o ecrã, o GoatRemote e o site (ver [start]). */
    val catalog = AppCatalog(context.applicationContext, scope)
    /** As apps desta TV para o site da conta. */
    val appIndex = AppIndex(context, backend, settings.session, catalog)
    val icons = CustomIcons(context)
    /** A lista de apps para o GoatRemote (o comando no telemóvel), na rede de casa, com os nomes e ícones escolhidos. */
    val phoneApps = PhoneApps(context.applicationContext, catalog, AutoStart.opener(context.applicationContext, catalog), scope) { apps ->
        val device = settings.device.value
        apps.asChosen(device.names, device.icons, icons::load)
    }
    val watchNext = WatchNextSource(context)

    /** Cliente sem token (pedidos fora da conta Slokker, ex.: as fotos do dia do fundo). */
    private val plainHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Avisa o servidor que esta TV se desligou da conta ([token]: o que ela tinha, lido antes de o apagar).
     * Sem esperar e sem repetir: qualquer resposta ou erro (sem rede, 404 num servidor antigo) é ignorado,
     * porque a TV fica sem conta na mesma.
     */
    fun logoutDevice(token: String) {
        scope.launch {
            try {
                withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { backend.deviceLogout(token) }
            } catch (e: Exception) {
                // Ignorado (ver acima).
            }
        }
    }

    val wallpapers = OnlineWallpapers(context, plainHttp, ClientInfo.current.publicUserAgent)

    /**
     * O que vive com o processo, com ou sem ecrã (chamado pelo LauncherApp): as mudanças nas apps instaladas, o índice
     * para o site (cada leitura das apps; só envia o que mudou) e a porta 6470.
     */
    fun start() {
        catalog.start()
        scope.launch { catalog.loads.collect { appIndex.upload(it) } }
        phoneApps.start()
    }

    /**
     * O cliente das imagens (Coil, ver LauncherApp). As imagens do Wikimedia recusam (403) a identificação genérica
     * do OkHttp, por isso os pedidos a esses servidores (e aos da NASA) levam a da app; os outros ficam como estavam.
     */
    val imageHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val photoHost = PHOTO_HOSTS.any { host == it || host.endsWith(".$it") }
            chain.proceed(if (photoHost) request.newBuilder().header("User-Agent", ClientInfo.current.publicUserAgent).build() else request)
        }
        .build()
    /** O resultado de cada instalação chega ao InstallResultReceiver (ver UpdateReceivers). */
    val updater = Updater(context, plainHttp, Gson(), InstallResultReceiver::class.java)
    val weather = WeatherSource(context, plainHttp)
    val youtube = YouTubeSource(context, plainHttp)
}

/** Os servidores das fotos do fundo que pedem uma identificação da app (ver [ClientInfo.publicUserAgent]). */
private val PHOTO_HOSTS = listOf("wikimedia.org", "nasa.gov")
