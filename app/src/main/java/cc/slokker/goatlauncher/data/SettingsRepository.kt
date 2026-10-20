package cc.slokker.goatlauncher.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.slokker.goatlauncher.data.api.BackendApi
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

private val Context.settingsStore by preferencesDataStore("settings")

/** Token atual em memória; o interceptor do AppContainer junta-o a cada pedido ao backend. */
class AuthToken {
    @Volatile
    var value: String? = null
}

/**
 * Definições do launcher: a última cópia fica em disco (o launcher funciona sempre, sem rede e sem conta)
 * e, com a TV ligada à conta, sincroniza-se com `GET/PATCH /api/launcher/settings`.
 *
 * - Uma alteração feita com a TV ligada fica marcada como pendente até o PATCH correr bem
 *   (sem rede, segue no próximo arranque ou regresso ao ecrã inicial).
 * - O que vem da conta substitui a cópia local, exceto o que ainda está pendente.
 * - Ao ligar a TV, a conta ganha se já tiver definições (é o que faz a ordem das apps e o fundo
 *   sobreviverem a reinstalar); se estiver vazia, recebe as desta TV.
 *
 * As alterações correm no thread principal (o [scope] é o da app); a rede e o disco são suspensos.
 */
class SettingsRepository(
    private val context: Context,
    private val api: BackendApi,
    private val sessions: SessionStore,
    private val token: AuthToken,
    private val scope: CoroutineScope,
) {
    private object Keys {
        val ACCOUNT = stringPreferencesKey("account")
        val DEVICE = stringPreferencesKey("device")
        val ACCOUNT_DIRTY = booleanPreferencesKey("account_dirty")
        val DEVICE_DIRTY = booleanPreferencesKey("device_dirty")
    }

    private val _account = MutableStateFlow(AccountSettings())
    val account: StateFlow<AccountSettings> = _account.asStateFlow()

    private val _device = MutableStateFlow(DeviceSettings())
    val device: StateFlow<DeviceSettings> = _device.asStateFlow()

    private val _session = MutableStateFlow<SessionStore.Session?>(null)

    /** null = sem conta. */
    val session: StateFlow<SessionStore.Session?> = _session.asStateFlow()

    /** true depois de ler a cópia do disco (antes disso, o ecrã mostraria o fundo por defeito por um instante). */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var accountDirty = false
    private var deviceDirty = false
    private var lastSync = 0L

    private val diskMutex = Mutex()
    private val syncMutex = Mutex()

    suspend fun init() {
        if (_ready.value) return
        runCatching {
            val p = context.settingsStore.data.first()
            _account.value = AccountSettings.fromJson(parse(p[Keys.ACCOUNT]))
            _device.value = DeviceSettings.fromJson(parse(p[Keys.DEVICE]))
            accountDirty = p[Keys.ACCOUNT_DIRTY] ?: false
            deviceDirty = p[Keys.DEVICE_DIRTY] ?: false
            val s = sessions.load()
            token.value = s?.token
            _session.value = s
        }.onFailure { Log.w(TAG, "Cache das definições", it) }
        _ready.value = true
    }

    fun updateAccount(transform: (AccountSettings) -> AccountSettings) {
        val new = transform(_account.value)
        if (new == _account.value) return
        _account.value = new
        if (_session.value != null) accountDirty = true
        scope.launch {
            persist()
            pushPending()
        }
    }

    /** [push] = false junta várias alterações seguidas (ex.: mover uma app) num só envio, feito com [pushPending]. */
    fun updateDevice(push: Boolean = true, transform: (DeviceSettings) -> DeviceSettings) {
        val new = transform(_device.value)
        if (new == _device.value) return
        _device.value = new
        if (_session.value != null) deviceDirty = true
        scope.launch {
            persist()
            if (push) pushPending()
        }
    }

    suspend fun pushPending() = guarded("PATCH") { syncMutex.withLock { pushLocked() } }

    /** Envia o pendente e lê a conta. [refreshToken]: renova o token (ao arrancar). */
    suspend fun sync(refreshToken: Boolean = false) = guarded("Sincronizar") {
        syncMutex.withLock {
            if (_session.value == null) return@withLock
            if (refreshToken) {
                api.authRefresh().token?.let {
                    token.value = it
                    sessions.updateToken(it)
                    _session.value = _session.value?.copy(token = it)
                }
            }
            pushLocked()
            val remote = api.settings()
            val acc = remote.account.nonEmptyObject()
            val dev = remote.device.nonEmptyObject()
            if (acc == null) accountDirty = true else if (!accountDirty) _account.value = AccountSettings.fromJson(acc)
            if (dev == null) deviceDirty = true else if (!deviceDirty) _device.value = DeviceSettings.fromJson(dev)
            persist()
            pushLocked()
            lastSync = SystemClock.elapsedRealtime()
        }
    }

    /** Ao voltar ao ecrã inicial: apanha o que mudou noutra TV, no máximo a cada [STALE_MS]. */
    suspend fun syncIfStale() {
        if (_session.value != null && SystemClock.elapsedRealtime() - lastSync > STALE_MS) sync()
    }

    /** A TV acabou de ser aprovada na conta. */
    suspend fun link(newToken: String, email: String?, deviceName: String?) {
        val s = SessionStore.Session(newToken, email, deviceName)
        sessions.save(s)
        token.value = newToken
        _session.value = s
        // A conta ganha se já tiver definições; vazia, recebe as desta TV (ver sync()).
        accountDirty = false
        deviceDirty = false
        persist()
        sync()
    }

    /** Esquece a conta nesta TV. As definições ficam como estão, só deixam de sincronizar. */
    suspend fun unlink() {
        sessions.clear()
        token.value = null
        _session.value = null
        accountDirty = false
        deviceDirty = false
        persist()
    }

    private suspend fun pushLocked() {
        if (_session.value == null || (!accountDirty && !deviceDirty)) return
        val a = _account.value.takeIf { accountDirty }
        val d = _device.value.takeIf { deviceDirty }
        val body = JsonObject().apply {
            a?.let { add("account", it.toJson()) }
            d?.let { add("device", it.toJson()) }
        }
        api.updateSettings(body)
        // Só fica limpo se não houve outra alteração entretanto (essa segue no próximo envio).
        if (a != null && _account.value == a) accountDirty = false
        if (d != null && _device.value == d) deviceDirty = false
        persist()
    }

    private suspend fun persist() = diskMutex.withLock {
        runCatching {
            context.settingsStore.edit {
                it[Keys.ACCOUNT] = _account.value.toJson().toString()
                it[Keys.DEVICE] = _device.value.toJson().toString()
                it[Keys.ACCOUNT_DIRTY] = accountDirty
                it[Keys.DEVICE_DIRTY] = deviceDirty
            }
        }.onFailure { Log.w(TAG, "Gravar definições", it) }
    }

    /** Erros de rede nunca chegam ao ecrã inicial. 401/404: a TV foi desligada na conta, segue sem conta. */
    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Log.w(TAG, "$what: HTTP ${e.code()}")
            if (e.code() == 401 || e.code() == 404) unlink()
        } catch (e: Exception) {
            Log.w(TAG, "$what: ${e.message}")
        }
    }

    private fun parse(json: String?): JsonObject? =
        json?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }.nonEmptyObject()

    private companion object {
        const val TAG = "Settings"
        const val STALE_MS = 5 * 60_000L
    }
}
