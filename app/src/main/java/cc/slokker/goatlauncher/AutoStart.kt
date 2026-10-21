package cc.slokker.goatlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import cc.slokker.goatlauncher.data.ParentalLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Abrir uma app sem a pessoa carregar em nada: a app escolhida ao ligar a TV ou ao acordar do standby, e o
 * pedido de código quando uma app bloqueada abre por outro caminho (ver HomeGuardService).
 *
 * Fora do ecrã inicial, o Android só deixa abrir apps a quem tem um serviço de acessibilidade ligado: sem ele,
 * ao acordar só funciona se o launcher estiver à frente.
 */
object AutoStart {
    /** Extra do MainActivity: pede o código e, se acertar, abre esta app. */
    const val EXTRA_UNLOCK = "cc.slokker.goatlauncher.UNLOCK"

    private var wakeJob: Job? = null

    /** Ao acordar do standby (ecrã ligado), abre a app escolhida, se a opção estiver ligada. */
    fun watchWake(app: LauncherApp) {
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = app.container.settings.device.value
                val pkg = device.autostart?.takeIf { device.autostartWake } ?: return
                wakeJob?.cancel()
                wakeJob = app.container.scope.launch {
                    delay(device.autostartDelay * 1000L)
                    open(app, pkg)
                }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    /** Abre [pkg]; se estiver bloqueada, abre antes o launcher a pedir o código. */
    fun open(context: Context, pkg: String) {
        val device = (context.applicationContext as LauncherApp).container.settings.device.value
        val intent = if (ParentalLock.appLocked(device, pkg)) {
            unlockIntent(context, pkg)
        } else {
            (context.applicationContext as LauncherApp).container.apps.launchIntent(pkg) ?: return
        }
        start(context, intent)
    }

    fun unlockIntent(context: Context, pkg: String): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_UNLOCK, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Pelo serviço de acessibilidade quando está ligado (pode abrir apps a partir do fundo); senão, diretamente. */
    fun start(context: Context, intent: Intent) {
        val starter: Context = HomeGuardService.instance ?: context
        runCatching { starter.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w("AutoStart", "Não deu para abrir: ${it.message}") }
    }
}
