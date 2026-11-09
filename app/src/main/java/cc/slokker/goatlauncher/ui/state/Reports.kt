package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.ClientInfo
import cc.slokker.goatlauncher.data.SessionStore
import cc.slokker.goatlauncher.data.api.BackendApi
import cc.slokker.goatlauncher.ui.Strings
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** O envio de um relatório (Definições → Enviar relatório). */
sealed interface ReportState {
    data object Idle : ReportState
    data object Sending : ReportState
    data class Sent(val id: String) : ReportState
    data class Failed(val message: String) : ReportState
}

/**
 * Envia um relatório para a zona admin do site (`POST api/reports`): o registo do launcher (sem tokens nem email) e o
 * que a pessoa escreveu. Com a TV ligada à conta, fica ligado a ela.
 *
 * [collectLog] junta o cabeçalho, o último crash e o logcat sem os segredos (BugReport.collect, no disco);
 * [youtubeSecrets] são os tokens do YouTube, que também não podem sair da TV.
 */
class ReportSender(
    private val scope: CoroutineScope,
    private val backend: BackendApi,
    private val client: ClientInfo,
    /** O aparelho (BugReport.device): Android, fabricante e modelo. */
    private val device: String,
    private val session: StateFlow<SessionStore.Session?>,
    /** A disposição do ecrã inicial, para o cabeçalho. */
    private val layout: () -> String,
    private val youtubeSecrets: () -> List<String>,
    private val strings: () -> Strings,
    private val collectLog: suspend (header: String, secrets: List<String>) -> String,
) {
    private val _state = MutableStateFlow<ReportState>(ReportState.Idle)
    val state: StateFlow<ReportState> = _state.asStateFlow()

    fun send(message: String) {
        if (_state.value == ReportState.Sending) return
        _state.value = ReportState.Sending
        scope.launch {
            _state.value = try {
                val session = session.value
                val header = "GoatLauncher ${client.version} · $device\n" +
                    "Account: ${if (session != null) "yes" else "no"} · layout: ${layout()}"
                val secrets = listOfNotNull(session?.token, session?.email) + youtubeSecrets()
                val log = collectLog(header, secrets)
                val body = JsonObject().apply {
                    addProperty("app", ClientInfo.APP)
                    addProperty("version", client.version)
                    addProperty("platform", client.platform)
                    addProperty("device", device)
                    message.trim().take(MAX_MESSAGE).takeIf { it.isNotEmpty() }?.let { addProperty("message", it) }
                    addProperty("log", log)
                }
                val res = backend.sendReport(body)
                val id = res.body()?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                when {
                    res.code() == 429 -> ReportState.Failed(strings().reportTooMany)
                    res.isSuccessful && id != null -> ReportState.Sent(id)
                    else -> ReportState.Failed(strings().reportFailed("HTTP ${res.code()}"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReportState.Failed(strings().reportFailed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private companion object {
        /** O máximo que segue do que a pessoa escreveu (caracteres). */
        const val MAX_MESSAGE = 2000
    }
}
