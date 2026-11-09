package cc.slokker.goatlauncher.data

import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

/**
 * Definições do launcher: a última cópia fica em disco (o launcher funciona sempre, sem rede e sem conta)
 * e, com a TV ligada à conta, sincroniza-se com `GET/PATCH /api/launcher/settings`.
 *
 * - Uma alteração feita com a TV ligada fica marcada como pendente até o PATCH correr bem
 *   (sem rede, segue no próximo arranque ou regresso ao ecrã inicial).
 * - O que vem da conta substitui a cópia local, exceto o que ainda está pendente.
 * - Ao ligar a TV, a conta ganha se já tiver definições (é o que faz a ordem das apps e o fundo
 *   sobreviverem a reinstalar); se estiver vazia, recebe as desta TV. A exceção é o controlo parental:
 *   com um código nesta TV, fica o desta TV e a conta recebe-o.
 *
 * Até a cópia do disco estar lida ([init], só o LauncherApp o chama), as definições são as por defeito: quem decide
 * com elas espera por [awaitReady] (o controlo parental passa pelo ParentalGate), e uma alteração pedida antes disso
 * fica à espera e aplica-se por cima da cópia lida (nunca grava os valores por defeito por cima dela).
 *
 * A sessão (o token) é do [SessionStore]; aqui só se liga, renova e desliga.
 *
 * As alterações correm no thread principal (o [scope] é o da app); a rede e o disco são suspensos.
 */
class SettingsRepository internal constructor(
    private val disk: SettingsDisk,
    private val api: BackendApi,
    private val sessions: SessionStore,
    private val scope: CoroutineScope,
    /** O relógio do [syncIfStale] (elapsedRealtime; outro nos testes). */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    /** O ficheiro e as chaves no disco: as instalações antigas leem-nos, por isso nunca mudam (ver PersistedNamesTest). */
    internal object Keys {
        const val STORE = "settings"
        val ACCOUNT = stringPreferencesKey("account")
        val DEVICE = stringPreferencesKey("device")
        val ACCOUNT_DIRTY = booleanPreferencesKey("account_dirty")
        val DEVICE_DIRTY = booleanPreferencesKey("device_dirty")
        val KEEP_PARENTAL = booleanPreferencesKey("keep_parental")
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

    /** null = sem conta. */
    val session: StateFlow<SessionStore.Session?> = sessions.session

    /** true depois de ler a cópia do disco (antes disso, o ecrã mostraria o fundo por defeito por um instante). */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Espera até a cópia do disco estar lida (logo, se já estiver). "Por ler" nunca conta como "sem código" nem como
     * "por defeito": quem decide fora do ecrã (o controlo parental, o arranque automático) espera por isto.
     */
    suspend fun awaitReady() {
        if (!_ready.value) _ready.first { it }
    }

    /**
     * Muda a cada ligar ou desligar da conta: um sync que começou com outra sessão deita fora o que trouxer (a resposta
     * de uma conta que esta TV já não tem não pode voltar a ser gravada).
     */
    private var generation = 0

    /**
     * O texto de um blob que estava no disco mas não se conseguiu ler (estragado). Fica como estava até a pessoa ou a
     * conta darem um valor a sério: antes, o primeiro [persist] gravava os valores por defeito por cima dele.
     */
    private var unreadableAccount: String? = null
    private var unreadableDevice: String? = null

    private var accountDirty = false
    private var deviceDirty = false
    /** A TV foi ligada com um código: a primeira leitura da conta não mexe no controlo parental (ver [adopt]). */
    private var keepParental = false
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
    private val initMutex = Mutex()

    /** Lê a cópia do disco uma só vez; quem chama enquanto outro lê espera por essa leitura. Só o LauncherApp chama. */
    suspend fun init() {
        initMutex.withLock {
            if (_ready.value) return
            runCatching {
                val p = disk.load()
                unreadableAccount = p.account?.takeIf(::unreadable)
                unreadableDevice = p.device?.takeIf(::unreadable)
                if (unreadableAccount != null || unreadableDevice != null) {
                    Log.w(TAG, "Definições estragadas no disco: ficam até haver outras")
                }
                _account.value = AccountSettings.fromJson(parse(p.account))
                _device.value = DeviceSettings.fromJson(parse(p.device))
                accountDirty = p.accountDirty
                deviceDirty = p.deviceDirty
                keepParental = p.keepParental
                accountBase = parse(p.accountBase)
                deviceBase = parse(p.deviceBase)
                accountRev = p.accountRev
                deviceRev = p.deviceRev
                etag = p.etag
                sessions.load()
            }.onFailure { Log.w(TAG, "Cache das definições", it) }
            _ready.value = true
        }
    }

    /** Uma alteração pedida antes de as definições estarem lidas espera por elas (e aplica-se por cima). */
    fun updateAccount(transform: (AccountSettings) -> AccountSettings) {
        if (!_ready.value) return later { updateAccount(transform) }
        val new = transform(_account.value)
        if (new == _account.value) return
        _account.value = new
        unreadableAccount = null
        if (session.value != null) accountDirty = true
        scope.launch {
            persist()
            pushPending()
        }
    }

    /** [push] = false junta várias alterações seguidas (ex.: mover uma app) num só envio, feito com [pushPending]. */
    fun updateDevice(push: Boolean = true, transform: (DeviceSettings) -> DeviceSettings) {
        if (!_ready.value) return later { updateDevice(push, transform) }
        val new = transform(_device.value)
        if (new == _device.value) return
        _device.value = new
        unreadableDevice = null
        if (session.value != null) deviceDirty = true
        scope.launch {
            persist()
            if (push) pushPending()
        }
    }

    /** Hoje o ecrã só aparece com as definições lidas, por isso isto não devia acontecer; se acontecer, não se perde. */
    private fun later(update: () -> Unit) {
        Log.w(TAG, "Alteração antes de ler as definições: fica para depois")
        scope.launch {
            awaitReady()
            update()
        }
    }

    suspend fun pushPending() {
        var gen = generation
        guarded("PATCH", { gen }) {
            syncMutex.withLock {
                gen = generation
                pushLocked(gen)
            }
        }
    }

    /** Envia o pendente e lê a conta. [refreshToken]: renova o token (ao arrancar). */
    suspend fun sync(refreshToken: Boolean = false) {
        var gen = generation
        guarded("Sincronizar", { gen }) {
            syncMutex.withLock {
                gen = generation
                syncLocked(gen, refreshToken)
            }
        }
    }

    /** [gen]: a [generation] com que começou; se mudar a meio (ligar ou desligar), o resto não se faz. */
    private suspend fun syncLocked(gen: Int, refreshToken: Boolean) {
        if (session.value == null) return
        if (refreshToken) {
            val renewed = api.authRefresh().token
            // Desligada enquanto se esperava: o token novo já não é desta TV.
            if (gen != generation) return dropped("auth-refresh")
            renewed?.let { sessions.updateToken(it) }
        }
        pushLocked(gen)
        if (gen != generation) return
        val res = api.settings(etag.takeIf { accountBase != null || deviceBase != null })
        if (gen != generation) return dropped("GET")
        if (res.code() == 304) Log.i(TAG, "Definições: sem alterações (304)")
        if (res.code() != 304) {
            val remote = res.body() ?: throw HttpException(res)
            etag = res.headers()["ETag"]
            adopt(remote)
            // Conta ainda vazia: recebe as desta TV.
            if (remote.get("account").nonEmptyObject() == null) accountDirty = true
            if (remote.get("device").nonEmptyObject() == null) {
                deviceDirty = true
                keepParental = false
            }
            persist()
            pushLocked(gen)
        }
        lastSync = clock()
    }

    /** Ao voltar ao ecrã inicial: apanha o que mudou noutra TV, no máximo a cada [STALE_MS]. */
    suspend fun syncIfStale() {
        if (session.value != null && clock() - lastSync > STALE_MS) sync()
    }

    /** A TV acabou de ser aprovada na conta. */
    suspend fun link(newToken: String, email: String?, deviceName: String?) {
        generation++
        sessions.save(SessionStore.Session(newToken, email, deviceName))
        syncMutex.withLock {
            // A conta ganha se já tiver definições; vazia, recebe as desta TV (ver sync()).
            accountDirty = false
            deviceDirty = false
            keepParental = _device.value.pin != null
            forgetRemote()
            persist()
        }
        sync()
    }

    /** Esquece a conta nesta TV. As definições ficam como estão, só deixam de sincronizar. */
    suspend fun unlink() {
        // Já: o ecrã deixa de mostrar a conta, nenhum pedido novo leva o token e um sync a meio (à espera da rede)
        // deita fora o que trouxer. Antes ainda podia gravar o token renovado (e a TV arrancava ligada a uma conta que
        // já não a tem) ou a base, a revisão e o ETag da conta antiga.
        generation++
        sessions.clear()
        // O resto depois desse sync acabar.
        syncMutex.withLock {
            accountDirty = false
            deviceDirty = false
            keepParental = false
            forgetRemote()
            persist()
        }
    }

    private fun dropped(what: String) {
        Log.i(TAG, "$what: a conta mudou entretanto, resposta ignorada")
    }

    /**
     * Grava o que está pendente. Com revisões, manda só o que mudou desde a última leitura ([accountBase] /
     * [deviceBase]) em `account_merge` / `device_merge`; num 409 (o site ou outra TV gravou entretanto), põe as
     * alterações desta TV por cima do que a conta tem agora e tenta outra vez. Sem revisões, os objetos inteiros.
     */
    private suspend fun pushLocked(gen: Int) {
        repeat(MAX_ATTEMPTS) {
            if (gen != generation || session.value == null || (!accountDirty && !deviceDirty)) return
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
            if (gen != generation) return dropped("PATCH")
            val reply = res.body() ?: res.errorBody()?.string()?.let { parse(it) }
            when {
                res.isSuccessful -> {
                    Log.i(TAG, "Gravado: ${body.keySet()}")
                    // Só fica limpo se não houve outra alteração entretanto (essa segue no próximo envio).
                    if (_account.value == sentAccount) accountDirty = false
                    if (_device.value == sentDevice) deviceDirty = false
                    reply?.let { adopt(it) }
                    persist()
                    return
                }
                res.code() == 409 && reply != null -> {
                    Log.i(TAG, "409: a juntar com o que a conta tem e a tentar outra vez")
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
            unreadableAccount = null
            _account.value = AccountSettings.fromJson(
                if (accountDirty) SettingsMerge.rebase(acc, accountBase ?: JsonObject(), _account.value.toJson()) else acc,
            )
            accountBase = acc
            accountRev = remote.long("account_rev")
        }
        remote.get("device").nonEmptyObject()?.let { dev ->
            unreadableDevice = null
            val merged = DeviceSettings.fromJson(
                if (deviceDirty) {
                    SettingsMerge.rebase(dev, deviceBase ?: JsonObject(), _device.value.toJson(), ordered = setOf("apps"))
                } else {
                    dev
                },
            )
            _device.value = if (keepParental) merged.withParentalOf(_device.value) else merged
            deviceBase = dev
            deviceRev = remote.long("device_rev")
            if (keepParental) {
                // O controlo parental desta TV segue para a conta com o próximo envio.
                keepParental = false
                deviceDirty = true
            }
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
            disk.save(
                SettingsSnapshot(
                    // Um blob estragado fica como estava (ver [unreadableAccount]).
                    account = unreadableAccount ?: _account.value.toJson().toString(),
                    device = unreadableDevice ?: _device.value.toJson().toString(),
                    accountDirty = accountDirty,
                    deviceDirty = deviceDirty,
                    keepParental = keepParental,
                    accountBase = accountBase?.toString(),
                    deviceBase = deviceBase?.toString(),
                    accountRev = accountRev,
                    deviceRev = deviceRev,
                    etag = etag,
                ),
            )
        }.onFailure { Log.w(TAG, "Gravar definições", it) }
    }

    /**
     * Erros de rede nunca chegam ao ecrã inicial. 401/404: a TV foi desligada na conta, segue sem conta; mas só se
     * a sessão ainda for a do pedido ([gen]): a recusa de um token antigo não desliga a conta ligada entretanto.
     */
    private suspend fun guarded(what: String, gen: () -> Int, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Log.w(TAG, "$what: HTTP ${e.code()}")
            if (e.code() == 401 || e.code() == 404) {
                if (gen() == generation) unlink() else dropped(what)
            }
        } catch (e: Exception) {
            Log.w(TAG, "$what: ${e.message}")
        }
    }

    private fun parse(json: String?): JsonObject? =
        json?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }.nonEmptyObject()

    /** Texto que não é um objeto JSON (um blob estragado). `{}` lê-se bem: são as definições por defeito. */
    private fun unreadable(json: String): Boolean =
        runCatching { !JsonParser.parseString(json).isJsonObject }.getOrDefault(true)

    private companion object {
        const val TAG = "Settings"
        const val STALE_MS = 5 * 60_000L
        /** Tentativas de gravar quando o servidor responde 409 (igual à GoatTV). */
        const val MAX_ATTEMPTS = 3
    }
}
