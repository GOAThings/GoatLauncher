package cc.slokker.goatlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.sessionStore by preferencesDataStore(SessionStore.Keys.STORE)

/**
 * O login do dispositivo na conta Slokker (nunca uma password), no disco e em memória. É o único dono do token: o
 * interceptor do AppContainer lê-o daqui a cada pedido, e o SettingsRepository muda-o aqui (ligar, renovar, desligar),
 * uma vez por mudança. Antes havia três cópias (o disco, o AuthToken do interceptor e a do repositório), mantidas a par
 * à mão.
 */
class SessionStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.sessionStore)

    data class Session(val token: String, val email: String?, val deviceName: String?)

    /** O ficheiro e as chaves no disco: nunca mudam (ver PersistedNamesTest). */
    internal object Keys {
        const val STORE = "session"
        val TOKEN = stringPreferencesKey("token")
        val EMAIL = stringPreferencesKey("email")
        val DEVICE_NAME = stringPreferencesKey("device_name")
    }

    private val _session = MutableStateFlow<Session?>(null)

    /** null = sem conta (ou ainda por ler: ver [load]). */
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** O token para o próximo pedido ao backend (null sem conta). */
    val token: String? get() = _session.value?.token

    /** Lê a sessão do disco (uma vez, ao arrancar: ver SettingsRepository.init). */
    suspend fun load(): Session? {
        val p = store.data.first()
        val s = p[Keys.TOKEN]?.let { Session(it, p[Keys.EMAIL], p[Keys.DEVICE_NAME]) }
        _session.value = s
        return s
    }

    /** A TV acabou de ser ligada a uma conta. */
    suspend fun save(session: Session) {
        _session.value = session
        store.edit {
            it[Keys.TOKEN] = session.token
            if (session.email != null) it[Keys.EMAIL] = session.email else it.remove(Keys.EMAIL)
            if (session.deviceName != null) it[Keys.DEVICE_NAME] = session.deviceName else it.remove(Keys.DEVICE_NAME)
        }
    }

    /** O token renovado. Sem sessão (desligada entretanto) não faz nada: nunca volta a escrever um token apagado. */
    suspend fun updateToken(token: String) {
        val current = _session.value ?: return
        _session.value = current.copy(token = token)
        store.edit { it[Keys.TOKEN] = token }
    }

    /** Esquece a conta: em memória já (o ecrã e o próximo pedido deixam de a ver) e depois no disco. */
    suspend fun clear() {
        _session.value = null
        store.edit { it.clear() }
    }
}
