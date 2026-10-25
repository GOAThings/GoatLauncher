package cc.slokker.goatlauncher

import android.app.Application

class LauncherApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.phoneApps.start()
        AutoStart.watchWake(this)
    }
}
