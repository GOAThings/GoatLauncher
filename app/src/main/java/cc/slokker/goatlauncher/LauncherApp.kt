package cc.slokker.goatlauncher

import android.app.Application
import cc.slokker.goatlauncher.data.BugReport

class LauncherApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        BugReport.installCrashHandler(this)
        container = AppContainer(this)
        container.phoneApps.start()
        AutoStart.watchWake(this)
    }
}
