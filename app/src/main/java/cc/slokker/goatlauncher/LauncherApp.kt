package cc.slokker.goatlauncher

import android.app.Application
import cc.slokker.goatlauncher.data.BugReport
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.launch

class LauncherApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        BugReport.installCrashHandler(this)
        container = AppContainer(this)
        // O controlo parental precisa das definições também num processo sem ecrã (só com o HomeGuardService).
        container.scope.launch { container.settings.init() }
        container.phoneApps.start()
        AutoStart.watchWake(this)
    }

    /** O carregador de imagens de sempre, só com o cliente HTTP da app (ver [AppContainer.imageHttp]). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.imageHttp })) }
        .build()
}
