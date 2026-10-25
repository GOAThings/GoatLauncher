package cc.slokker.goatlauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import cc.slokker.goatlauncher.data.ParentalLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Mantém o GoatLauncher como ecrã inicial, como o "Override current launcher" do Projectivy. Opcional: a pessoa
 * liga-o nas definições de Acessibilidade do Android.
 *
 * Enquanto o GoatLauncher for o ecrã inicial do sistema não faz nada (o Home segue o caminho normal). O Android
 * esquece-o quando muda o conjunto de launchers instalados (ex.: instalar ou atualizar o Projectivy). Aí o Home
 * do comando abre o GoatLauncher, e outro launcher que chegue a abrir como ecrã inicial é logo substituído.
 * (As teclas enviadas por `adb shell input` não passam por aqui: só as do comando.)
 *
 * Também reforça o controlo parental: uma app bloqueada que abra por outro caminho (Assistente, Recentes…)
 * dá lugar ao launcher a pedir o código. E é por aqui que o AutoStart abre apps a partir do fundo.
 */
class HomeGuardService : AccessibilityService() {

    private var settingsJob: Job? = null

    override fun onServiceConnected() {
        instance = this
        val container = (application as LauncherApp).container
        settingsJob = container.scope.launch { container.settings.device.collect { updateKeyFilter() } }
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Só pede para ver as teclas quando faz falta: a escolher um botão, com botões que abrem apps, ou quando o
     * GoatLauncher deixou de ser o ecrã inicial (o Home do comando passa por aqui).
     *
     * Com as teclas pedidas, o Android 11 (SHIELD) passa cada uma pelo KeyboardInterceptor da acessibilidade, e
     * uma tecla de volume vinda de um comando no telemóvel (protocolo da app Google TV, sem dispositivo físico)
     * rebenta o system_server (NullPointerException em PhoneWindowManager.deviceSupportsVolumeMuteHotkey) e a box
     * reinicia. Sem as teclas pedidas, esse caminho não existe.
     */
    fun updateKeyFilter() {
        val info = serviceInfo ?: return
        val needed = ButtonCapture.listener != null ||
            (application as LauncherApp).container.settings.device.value.buttons.isNotEmpty() ||
            !isDefaultHome(this)
        val flags = if (needed) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        if (flags == info.flags) return
        info.flags = flags
        serviceInfo = info
        Log.i(TAG, if (needed) "A ver as teclas do comando" else "Sem ver as teclas do comando")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (ButtonCapture.allowed(event.keyCode)) {
            // Definições → "Adicionar botão": o primeiro botão que se possa usar fica escolhido (e não faz mais nada).
            val capture = ButtonCapture.listener
            if (capture != null) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    ButtonCapture.listener = null
                    swallowUp = event.keyCode
                    capture(event.keyCode, event.scanCode)
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && swallowUp == event.keyCode) {
                swallowUp = null
                return true
            }
            // Um botão com app escolhida: abre-a ao largar. O sistema só trata estes botões depois daqui (ex.: o
            // Netflix da Shield), por isso consumi-los chega para não abrirem também o que abriam antes.
            val button = (application as LauncherApp).container.settings.device.value.buttons
                .firstOrNull { it.matches(event.keyCode, event.scanCode) }
            if (button != null) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) AutoStart.open(this, button.app)
                return true
            }
        }
        if (event.keyCode != KeyEvent.KEYCODE_HOME || isDefaultHome(this)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        // O ecrã inicial do sistema pode ter mudado (outro launcher instalado): volta a ver se as teclas fazem falta.
        updateKeyFilter()
        val device = (application as LauncherApp).container.settings.device.value
        if (ParentalLock.appLocked(device, pkg)) {
            AutoStart.start(this, AutoStart.unlockIntent(this, pkg))
            return
        }
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

    /** O largar do botão acabado de capturar também não passa. */
    private var swallowUp: Int? = null

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

        /** O serviço ligado agora (null se a pessoa não o ligou nas definições de Acessibilidade). */
        @Volatile
        var instance: HomeGuardService? = null
            private set

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
