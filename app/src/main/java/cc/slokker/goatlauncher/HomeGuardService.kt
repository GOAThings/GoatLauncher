package cc.slokker.goatlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Mantém o GoatLauncher como ecrã inicial, como o "Override current launcher" do Projectivy. Opcional: a pessoa
 * liga-o nas definições de Acessibilidade do Android.
 *
 * Enquanto o GoatLauncher for o ecrã inicial do sistema não faz nada (o Home segue o caminho normal). O Android
 * esquece-o quando muda o conjunto de launchers instalados (ex.: instalar ou atualizar o Projectivy). Aí o Home
 * do comando abre o GoatLauncher, e outro launcher que chegue a abrir como ecrã inicial é logo substituído.
 * (As teclas enviadas por `adb shell input` não passam por aqui: só as do comando.)
 */
class HomeGuardService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || isDefaultHome(this)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val activity = ComponentName(pkg, event.className?.toString() ?: return)
        if (activity in homeActivities() && !isDefaultHome(this)) openLauncher()
    }

    /** O ecrã inicial dos outros launchers (o de origem, o Projectivy…): só esse é substituído, não a app toda. */
    private fun homeActivities(): Set<ComponentName> {
        val now = System.currentTimeMillis()
        if (now - homesAt > HOMES_TTL_MS) {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            homes = packageManager.queryIntentActivities(home, 0)
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .filter { it.packageName != packageName && it.packageName != "android" && !it.className.contains("FallbackHome") }
                .toSet()
            homesAt = now
        }
        return homes
    }

    private var homes = emptySet<ComponentName>()
    private var homesAt = 0L

    override fun onInterrupt() = Unit

    private fun openLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "Não deu para abrir o launcher: ${it.message}") }
    }

    companion object {
        private const val TAG = "HomeGuard"

        private const val HOMES_TTL_MS = 60_000L

        fun isDefaultHome(context: Context): Boolean {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName
        }

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeGuardService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
