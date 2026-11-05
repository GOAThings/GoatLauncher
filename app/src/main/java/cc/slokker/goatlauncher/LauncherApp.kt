package cc.slokker.goatlauncher

import android.app.Application
import cc.slokker.goatlauncher.data.BugReport
import kotlinx.coroutines.launch

class LauncherApp : Application() {

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
}
