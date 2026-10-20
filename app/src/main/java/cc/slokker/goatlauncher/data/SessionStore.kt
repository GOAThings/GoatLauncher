package cc.slokker.goatlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("session")

/** Guarda localmente só o login do dispositivo na conta Slokker (nunca uma password). */
class SessionStore(private val context: Context) {

    data class Session(val token: String, val email: String?, val deviceName: String?)

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val EMAIL = stringPreferencesKey("email")
        val DEVICE_NAME = stringPreferencesKey("device_name")
    }

    private suspend fun prefs(): Preferences = context.dataStore.data.first()

    suspend fun load(): Session? {
        val p = prefs()
        val token = p[Keys.TOKEN] ?: return null
        return Session(token, p[Keys.EMAIL], p[Keys.DEVICE_NAME])
    }

    suspend fun save(session: Session) {
        context.dataStore.edit {
            it[Keys.TOKEN] = session.token
            if (session.email != null) it[Keys.EMAIL] = session.email else it.remove(Keys.EMAIL)
            if (session.deviceName != null) it[Keys.DEVICE_NAME] = session.deviceName else it.remove(Keys.DEVICE_NAME)
        }
    }

    suspend fun updateToken(token: String) {
        context.dataStore.edit { it[Keys.TOKEN] = token }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
