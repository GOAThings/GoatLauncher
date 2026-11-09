package cc.slokker.goatlauncher.ui.state

import android.os.SystemClock
import cc.slokker.goatlauncher.data.AppRelease
import cc.slokker.goatlauncher.data.AppUpdates
import cc.slokker.goatlauncher.data.Updater
import cc.slokker.goatlauncher.ui.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState

    /**
     * O estado depois de a pessoa cancelar a confirmação do Android: a versão volta a estar disponível. Ficar em
     * [Installing] deixava as Definições em "A instalar…" e parava as verificações automáticas.
     */
    fun cancelled(): UpdateState = if (this is Installing) Available(release) else this
}

/**
 * Definições → Atualizações e a reparação do serviço do comando: procurar versões novas (pouco depois de abrir e de 12
 * em 12 horas), descarregar e entregar ao instalador do Android, e reinstalar a própria app quando o Android deixou o
 * serviço de acessibilidade preso ([guardStuck]).
 *
 * [isGuardStuck]: o serviço está ligado nas definições do Android mas não arrancou. [sinceProcessStart]: há quanto
 * tempo o processo arrancou (ms). [ownApk]: o APK instalado desta app (o que a reparação reinstala).
 */
class UpdatesController(
    private val scope: CoroutineScope,
    private val updater: AppUpdates,
    private val strings: () -> Strings,
    private val toast: (String) -> Unit,
    private val isGuardStuck: () -> Boolean,
    private val sinceProcessStart: () -> Long,
    private val ownApk: () -> File,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = updater.currentVersion

    private val _guardStuck = MutableStateFlow(false)

    /**
     * O serviço do comando ("Manter como ecrã inicial") está ligado nas definições mas o Android não o ligou:
     * no Android 11 fica assim depois de o sistema da Shield reiniciar, e só uma atualização da app o desprende.
     */
    val guardStuck: StateFlow<Boolean> = _guardStuck.asStateFlow()

    private var lastRepairPrompt = -REPAIR_PROMPT_GAP_MS

    /** As respostas do instalador e as verificações automáticas (chamado uma vez, ao arrancar o ecrã). */
    fun start() {
        scope.launch {
            updater.installFailures.collect { _state.value = UpdateState.Failed(it) }
        }
        scope.launch {
            updater.installCancels.collect { _state.value = _state.value.cancelled() }
        }
        scope.launch {
            // Procura versões novas pouco depois de abrir e de 12 em 12 horas (o launcher fica aberto dias a fio).
            wait(FIRST_CHECK_MS)
            while (true) {
                check(manual = false)
                wait(UPDATE_CHECK_MS)
            }
        }
    }

    fun check(manual: Boolean) {
        if (!updater.enabled) {
            if (manual) toast(strings().updatesOnlyRelease)
            return
        }
        val state = _state.value
        if (state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing) return
        if (manual) _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val latest = updater.latest()
                if (latest != null && Updater.isNewer(latest.version, updater.currentVersion)) {
                    if (!manual && state !is UpdateState.Available) toast(strings().updateAvailable(latest.version))
                    UpdateState.Available(latest)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) UpdateState.Failed(e.message ?: e.javaClass.simpleName) else state
            }
        }
    }

    /** Descarrega e entrega ao instalador do Android a versão encontrada (ou procura, se ainda não houver). */
    fun install() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return check(manual = true)
        if (!updater.canInstall()) {
            toast(strings().allowInstall)
            updater.openInstallPermission()
            return
        }
        scope.launch {
            try {
                _state.value = UpdateState.Downloading(release, 0f)
                val apk = updater.download(release) { p -> _state.value = UpdateState.Downloading(release, p) }
                _state.value = UpdateState.Installing(release)
                updater.install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Ver [guardStuck]. Só passado um bocado do arranque: ligar o serviço demora uns segundos. */
    fun checkGuard() {
        scope.launch {
            val sinceStart = sinceProcessStart()
            if (sinceStart < GUARD_GRACE_MS) wait(GUARD_GRACE_MS - sinceStart)
            val stuck = isGuardStuck()
            _guardStuck.value = stuck
            if (!stuck) return@launch
            // Repara-se sozinho: aparece logo a confirmação da reinstalação (um OK; o Android 11 não deixa uma app
            // atualizar-se sem perguntar). Se a pessoa cancelar, só volta a pedir passada uma hora; sem permissão
            // para instalar, fica o aviso (e o OK nas Definições).
            val now = clock()
            if (updater.canInstall() && now - lastRepairPrompt > REPAIR_PROMPT_GAP_MS) {
                lastRepairPrompt = now
                toast(strings().guardRepairing)
                repairGuard()
            } else if (now - lastRepairPrompt > REPAIR_PROMPT_GAP_MS) {
                lastRepairPrompt = now
                toast(strings().guardStuckToast)
            }
        }
    }

    /** Reinstala a própria app (a mesma versão): o Android trata-a como atualizada e volta a ligar o serviço. */
    fun repairGuard() {
        if (!updater.canInstall()) {
            updater.openInstallPermission()
            return
        }
        scope.launch {
            runCatching { updater.install(ownApk()) }
                .onFailure { toast(strings().updateFailed(it.message ?: it.javaClass.simpleName)) }
        }
    }

    companion object {
        /** A primeira verificação, depois de abrir. */
        const val FIRST_CHECK_MS = 20_000L

        /** De quanto em quanto tempo procurar versões novas. */
        const val UPDATE_CHECK_MS = 12 * 60 * 60_000L

        /** Ligar o serviço de acessibilidade depois de o processo arrancar demora uns segundos. */
        const val GUARD_GRACE_MS = 20_000L

        const val REPAIR_PROMPT_GAP_MS = 60 * 60_000L
    }
}
