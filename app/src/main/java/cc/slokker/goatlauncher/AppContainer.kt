package cc.slokker.goatlauncher

import android.content.Context
import cc.slokker.goatlauncher.data.AuthToken
import cc.slokker.goatlauncher.data.CustomIcons
import cc.slokker.goatlauncher.data.InstalledApps
import cc.slokker.goatlauncher.data.RedditWallpapers
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.SettingsRepository
import cc.slokker.goatlauncher.data.WatchNextSource
import cc.slokker.goatlauncher.data.api.BACKEND_URL
import cc.slokker.goatlauncher.data.api.BackendApi
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

    private val backendHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val current = token.value
            // Sem "Bearer": o backend espera o token tal como está.
            val request = if (current != null) chain.request().newBuilder().header("Authorization", current).build() else chain.request()
            chain.proceed(request)
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
    val watchNext = WatchNextSource(context)
    val icons = CustomIcons(context)

    /** Cliente sem token (pedidos fora da conta Slokker, ex.: o feed do Reddit). */
    private val plainHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val reddit = RedditWallpapers(context, plainHttp)
}
