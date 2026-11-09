package cc.slokker.goatlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import cc.slokker.goatlauncher.data.AppCatalog
import cc.slokker.goatlauncher.data.AppOpener
import cc.slokker.goatlauncher.data.OpenResult
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

    /** Onde fica o arranque da TV em que a app escolhida já abriu (ver LauncherViewModel.autostartAfterBoot). */
    const val BOOT_PREFS = "autostart"
    const val BOOT_KEY = "boot"

    private var wakeJob: Job? = null

    /**
     * Ao acordar do standby (ecrã ligado), abre a app escolhida, se a opção estiver ligada. Decide com as definições
     * lidas: um processo reiniciado em standby pode acordar antes de as ler, e as por defeito diriam "não abrir".
     */
    fun watchWake(app: LauncherApp) {
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val settings = app.container.settings
                wakeJob?.cancel()
                wakeJob = app.container.scope.launch {
                    settings.awaitReady()
                    val device = settings.device.value
                    val pkg = device.autostart?.takeIf { device.autostartWake } ?: return@launch
                    delay(device.autostartDelay * 1000L)
                    open(app, pkg)
                }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    /**
     * Abre [pkg]; se estiver bloqueada, abre antes o launcher a pedir o código. Só decide com as definições lidas
     * (ver ParentalGate): até lá não se sabe se há código, e nada abre.
     */
    fun open(context: Context, pkg: String) {
        val container = (context.applicationContext as LauncherApp).container
        container.scope.launch {
            val intent = if (container.parental.appLocked(pkg)) {
                unlockIntent(context, pkg)
            } else {
                container.catalog.launchIntent(pkg) ?: return@launch
            }
            start(context, intent)
        }
    }

    /**
     * Abrir apps a pedido do GoatRemote (`POST /open/<pacote>` na porta 6470, ver PhoneRoutes): pelo [open], com o
     * controlo parental. Sem o serviço de acessibilidade, o Android não deixa abrir a partir do fundo.
     */
    fun opener(context: Context, catalog: AppCatalog) = AppOpener { pkg ->
        when {
            catalog.launchIntent(pkg) == null -> OpenResult.NOT_FOUND
            HomeGuardService.instance == null -> OpenResult.NO_BACKGROUND_START
            else -> OpenResult.OPENED.also { open(context, pkg) }
        }
    }

    fun unlockIntent(context: Context, pkg: String): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_UNLOCK, pkg)
            .putExtra(EXTRA_UNLOCK_AT, SystemClock.elapsedRealtime())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /**
     * O pedido do [unlockIntent] é de agora? O sistema guarda o intent que abriu o ecrã e volta a dá-lo quando o recria
     * depois de fechar o processo: esse já foi tratado. Sem a hora (um `am start` à mão) conta como novo.
     */
    fun isFresh(intent: Intent, now: Long): Boolean = isFresh(intent.getLongExtra(EXTRA_UNLOCK_AT, -1L), now)

    internal fun isFresh(createdAt: Long, now: Long): Boolean = createdAt < 0 || now - createdAt in 0..UNLOCK_FRESH_MS

    /** Quando o [unlockIntent] foi feito (elapsedRealtime). */
    private const val EXTRA_UNLOCK_AT = "cc.slokker.goatlauncher.UNLOCK_AT"

    /** Do pedido até o ecrã abrir: um arranque a frio numa SHIELD ocupada leva uns segundos, nunca um minuto. */
    private const val UNLOCK_FRESH_MS = 60_000L

    /** Pelo serviço de acessibilidade quando está ligado (pode abrir apps a partir do fundo); senão, diretamente. */
    fun start(context: Context, intent: Intent) {
        val starter: Context = HomeGuardService.instance ?: context
        runCatching { starter.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w("AutoStart", "Não deu para abrir: ${it.message}") }
    }
}
