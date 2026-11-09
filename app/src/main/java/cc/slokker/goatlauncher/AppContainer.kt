package cc.slokker.goatlauncher

import android.content.Context
import android.os.Build
import cc.slokker.goatlauncher.data.AppIndex
import cc.slokker.goatlauncher.data.AuthToken
import cc.slokker.goatlauncher.data.CustomIcons
import cc.slokker.goatlauncher.data.InstalledApps
import cc.slokker.goatlauncher.data.PhoneApps
import cc.slokker.goatlauncher.data.PinAttemptStore
import cc.slokker.goatlauncher.data.OnlineWallpapers
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.SettingsRepository
import cc.slokker.goatlauncher.data.Updater
import cc.slokker.goatlauncher.data.WeatherSource
import cc.slokker.goatlauncher.data.YouTubeSource
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

    private val token = AuthToken()

    private val client = goatClient()

    private val backendHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Um pedido que já traz o seu token (o logout) fica com ele.
            val current = token.value.takeIf { chain.request().header("Authorization") == null }
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

    val settings = SettingsRepository(context, backend, SessionStore(context), token, scope)
    val pinAttempts = PinAttemptStore(context)
    val apps = InstalledApps(context)
    /** As apps desta TV para o site da conta. */
    val appIndex = AppIndex(context, backend, settings)
    /** A lista de apps para o GoatRemote (o comando no telemóvel), na rede de casa. */
    val phoneApps = PhoneApps(context.applicationContext, apps, scope)
    val watchNext = WatchNextSource(context)
    val icons = CustomIcons(context)

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

    val wallpapers = OnlineWallpapers(context, plainHttp, publicUserAgent())

    /**
     * O cliente das imagens (Coil, ver LauncherApp). As imagens do Wikimedia recusam (403) a identificação genérica
     * do OkHttp, por isso os pedidos a esses servidores (e aos da NASA) levam a da app; os outros ficam como estavam.
     */
    val imageHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val photoHost = PHOTO_HOSTS.any { host == it || host.endsWith(".$it") }
            chain.proceed(if (photoHost) request.newBuilder().header("User-Agent", publicUserAgent()).build() else request)
        }
        .build()
    val updater = Updater(context, plainHttp, Gson())
    val weather = WeatherSource(context, plainHttp)
    val youtube = YouTubeSource(context, plainHttp)
}

/** Os servidores das fotos do fundo que pedem uma identificação da app (ver [publicUserAgent]). */
private val PHOTO_HOSTS = listOf("wikimedia.org", "nasa.gov")

private fun platform(): String = if (Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) "fire-tv" else "android-tv"

/** `goatlauncher/<versão> (<plataforma>)`, por exemplo `goatlauncher/0.8.9 (android-tv)`. */
private fun goatClient(): String = "goatlauncher/${BuildConfig.VERSION_NAME} (${platform()})"

/**
 * Para serviços públicos que pedem uma identificação com contacto (a política do Wikimedia): a de [goatClient] com a
 * página do projeto, por exemplo `goatlauncher/0.9.11 (android-tv; +https://github.com/GOAThings/GoatLauncher)`.
 */
private fun publicUserAgent(): String =
    "goatlauncher/${BuildConfig.VERSION_NAME} (${platform()}; +https://github.com/${Updater.RELEASES_REPO})"
