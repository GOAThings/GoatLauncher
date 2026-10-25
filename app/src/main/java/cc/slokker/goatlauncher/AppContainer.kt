package cc.slokker.goatlauncher

import android.content.Context
import android.os.Build
import cc.slokker.goatlauncher.data.AppIndex
import cc.slokker.goatlauncher.data.AuthToken
import cc.slokker.goatlauncher.data.CustomIcons
import cc.slokker.goatlauncher.data.InstalledApps
import cc.slokker.goatlauncher.data.PhoneApps
import cc.slokker.goatlauncher.data.RedditWallpapers
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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

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
            val current = token.value
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
    val apps = InstalledApps(context)
    /** As apps desta TV para o site da conta. */
    val appIndex = AppIndex(context, backend, settings)
    /** A lista de apps para o GoatRemote (o comando no telemóvel), na rede de casa. */
    val phoneApps = PhoneApps(context.applicationContext, apps, scope)
    val watchNext = WatchNextSource(context)
    val icons = CustomIcons(context)

    /** Cliente sem token (pedidos fora da conta Slokker, ex.: o feed do Reddit). */
    private val plainHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val reddit = RedditWallpapers(context, plainHttp)
    val updater = Updater(context, plainHttp, Gson())
    val weather = WeatherSource(context, plainHttp)
    val youtube = YouTubeSource(context, plainHttp)
}

/** `goatlauncher/<versão> (<plataforma>)`, por exemplo `goatlauncher/0.8.9 (android-tv)`. */
private fun goatClient(): String {
    val platform = if (Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) "fire-tv" else "android-tv"
    return "goatlauncher/${BuildConfig.VERSION_NAME} ($platform)"
}
