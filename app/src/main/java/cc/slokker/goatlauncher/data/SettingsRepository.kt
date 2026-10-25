package cc.slokker.goatlauncher.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        /** O que a conta tinha da última vez (a base dos patches) e as revisões e o ETag dessa leitura. */
        val ACCOUNT_BASE = stringPreferencesKey("account_base")
        val DEVICE_BASE = stringPreferencesKey("device_base")
        val ACCOUNT_REV = longPreferencesKey("account_rev")
        val DEVICE_REV = longPreferencesKey("device_rev")
        val ETAG = stringPreferencesKey("settings_etag")
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

    /** O que a conta tinha da última leitura ou gravação: a base de onde se calcula o que mudou nesta TV. */
    private var accountBase: JsonObject? = null
    private var deviceBase: JsonObject? = null
    /** As revisões da conta; null num servidor sem revisões (aí gravam-se os objetos inteiros, como antes). */
    private var accountRev: Long? = null
    private var deviceRev: Long? = null
    private var etag: String? = null

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
            accountBase = parse(p[Keys.ACCOUNT_BASE])
            deviceBase = parse(p[Keys.DEVICE_BASE])
            accountRev = p[Keys.ACCOUNT_REV]
            deviceRev = p[Keys.DEVICE_REV]
            etag = p[Keys.ETAG]
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
            val res = api.settings(etag.takeIf { accountBase != null || deviceBase != null })
            if (res.code() != 304) {
                val remote = res.body() ?: throw HttpException(res)
                etag = res.headers()["ETag"]
                adopt(remote)
                // Conta ainda vazia: recebe as desta TV.
                if (remote.get("account").nonEmptyObject() == null) accountDirty = true
                if (remote.get("device").nonEmptyObject() == null) deviceDirty = true
                persist()
                pushLocked()
            }
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
        forgetRemote()
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
        forgetRemote()
        persist()
    }

    /**
     * Grava o que está pendente. Com revisões, manda só o que mudou desde a última leitura ([accountBase] /
     * [deviceBase]) em `account_merge` / `device_merge`; num 409 (o site ou outra TV gravou entretanto), põe as
     * alterações desta TV por cima do que a conta tem agora e tenta outra vez. Sem revisões, os objetos inteiros.
     */
    private suspend fun pushLocked() {
        repeat(MAX_ATTEMPTS) {
            if (_session.value == null || (!accountDirty && !deviceDirty)) return
            val sentAccount = _account.value
            val sentDevice = _device.value
            val body = JsonObject()
            part(body, "account", accountDirty, accountBase, accountRev, sentAccount.toJson())
            part(body, "device", deviceDirty, deviceBase, deviceRev, sentDevice.toJson())
            if (body.size() == 0) {
                // Nada mudou em relação à conta.
                if (_account.value == sentAccount) accountDirty = false
                if (_device.value == sentDevice) deviceDirty = false
                persist()
                return
            }
            val res = api.updateSettings(body)
            val reply = res.body() ?: res.errorBody()?.string()?.let { parse(it) }
            when {
                res.isSuccessful -> {
                    // Só fica limpo se não houve outra alteração entretanto (essa segue no próximo envio).
                    if (_account.value == sentAccount) accountDirty = false
                    if (_device.value == sentDevice) deviceDirty = false
                    reply?.let { adopt(it) }
                    persist()
                    return
                }
                res.code() == 409 && reply != null -> {
                    adopt(reply)
                    persist()
                }
                else -> throw HttpException(res)
            }
        }
    }

    /** Junta ao corpo do PATCH a parte [name]: o patch com a revisão, ou (sem revisões) o objeto inteiro. */
    private fun part(body: JsonObject, name: String, dirty: Boolean, base: JsonObject?, rev: Long?, local: JsonObject) {
        if (!dirty) return
        if (rev == null || base == null) {
            body.add(name, local)
            return
        }
        val patch = SettingsMerge.diff(base, local)
        if (patch.size() == 0) return
        body.add("${name}_merge", patch)
        body.addProperty("${name}_rev", rev)
    }

    /**
     * O estado da conta (de um GET, ou da resposta a um PATCH, incluindo um 409) passa a ser a base. O que não está
     * pendente nesta TV fica igual à conta; o que está pendente é posto por cima dela (os conjuntos juntam-se valor
     * a valor; a ordem das apps é uma lista ordenada e ganha a desta TV).
     */
    private fun adopt(remote: JsonObject) {
        remote.get("account").nonEmptyObject()?.let { acc ->
            _account.value = AccountSettings.fromJson(
                if (accountDirty) SettingsMerge.rebase(acc, accountBase ?: JsonObject(), _account.value.toJson()) else acc,
            )
            accountBase = acc
            accountRev = remote.long("account_rev")
        }
        remote.get("device").nonEmptyObject()?.let { dev ->
            _device.value = DeviceSettings.fromJson(
                if (deviceDirty) {
                    SettingsMerge.rebase(dev, deviceBase ?: JsonObject(), _device.value.toJson(), ordered = setOf("apps"))
                } else {
                    dev
                },
            )
            deviceBase = dev
            deviceRev = remote.long("device_rev")
        }
    }

    /** Outra conta (ou nenhuma): esquece a base, as revisões e o ETag da anterior. */
    private fun forgetRemote() {
        accountBase = null
        deviceBase = null
        accountRev = null
        deviceRev = null
        etag = null
    }

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private suspend fun persist() = diskMutex.withLock {
        runCatching {
            context.settingsStore.edit {
                it[Keys.ACCOUNT] = _account.value.toJson().toString()
                it[Keys.DEVICE] = _device.value.toJson().toString()
                it[Keys.ACCOUNT_DIRTY] = accountDirty
                it[Keys.DEVICE_DIRTY] = deviceDirty
                accountBase?.let { b -> it[Keys.ACCOUNT_BASE] = b.toString() } ?: it.remove(Keys.ACCOUNT_BASE)
                deviceBase?.let { b -> it[Keys.DEVICE_BASE] = b.toString() } ?: it.remove(Keys.DEVICE_BASE)
                accountRev?.let { r -> it[Keys.ACCOUNT_REV] = r } ?: it.remove(Keys.ACCOUNT_REV)
                deviceRev?.let { r -> it[Keys.DEVICE_REV] = r } ?: it.remove(Keys.DEVICE_REV)
                etag?.let { e -> it[Keys.ETAG] = e } ?: it.remove(Keys.ETAG)
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
        /** Tentativas de gravar quando o servidor responde 409 (igual à GoatTV). */
        const val MAX_ATTEMPTS = 3
    }
}
