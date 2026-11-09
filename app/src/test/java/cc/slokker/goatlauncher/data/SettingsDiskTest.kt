package cc.slokker.goatlauncher.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O DataStore `settings` e o `session` como as instalações antigas os têm: as mesmas chaves, do mesmo tipo, com os mesmos
 * valores. O SettingsDisk só mudou quem escreve, não o que fica no disco.
 */
class SettingsDiskTest {
    @Test
    fun anOldInstallReadsAndWritesTheSameKeys() = runBlocking {
        val store = MemoryDataStore()
        // Como uma versão anterior deixou o disco (as chaves escritas à mão, pelo nome e tipo).
        store.edit {
            it[stringPreferencesKey("account")] = """{"clock_24h":false}"""
            it[stringPreferencesKey("device")] = """{"apps":["a"]}"""
            it[booleanPreferencesKey("account_dirty")] = true
            it[booleanPreferencesKey("device_dirty")] = false
            it[booleanPreferencesKey("keep_parental")] = true
            it[stringPreferencesKey("account_base")] = """{"clock_24h":true}"""
            it[longPreferencesKey("account_rev")] = 12L
            it[stringPreferencesKey("settings_etag")] = "\"e7\""
        }
        val disk = DataStoreSettingsDisk(store)
        val read = disk.load()
        assertEquals(
            SettingsSnapshot(
                account = """{"clock_24h":false}""", device = """{"apps":["a"]}""", accountDirty = true, keepParental = true,
                accountBase = """{"clock_24h":true}""", accountRev = 12L, etag = "\"e7\"",
            ),
            read,
        )
        // Gravar o que se leu deixa o disco igual.
        val before = store.value
        disk.save(read)
        assertEquals(before.asMap().mapKeys { it.key.name }, store.value.asMap().mapKeys { it.key.name })
        // A base, a revisão e o ETag a null saem do disco (outra conta, ou nenhuma).
        disk.save(read.copy(accountBase = null, accountRev = null, etag = null))
        assertEquals(
            setOf("account", "device", "account_dirty", "device_dirty", "keep_parental"),
            store.value.asMap().keys.map { it.name }.toSet(),
        )
    }

    @Test
    fun theSessionStaysInItsThreeKeys() = runBlocking {
        val store = MemoryDataStore()
        val sessions = SessionStore(store)
        sessions.save(SessionStore.Session("t1", "pessoa@example.com", null))
        assertEquals(mapOf("token" to "t1", "email" to "pessoa@example.com"), store.value.asMap().mapKeys { it.key.name })
        sessions.updateToken("t2")
        assertEquals("t2", store.value[SessionStore.Keys.TOKEN])
        // Uma instalação antiga já ligada arranca ligada.
        val again = SessionStore(store)
        assertEquals(SessionStore.Session("t2", "pessoa@example.com", null), again.load())
        assertEquals("t2", again.token)
        sessions.clear()
        assertEquals(emptyMap<String, Any>(), store.value.asMap())
        // Sem sessão, um token renovado que chegue tarde não volta a ser escrito.
        sessions.updateToken("t3")
        assertNull(store.value[SessionStore.Keys.TOKEN])
        assertNull(sessions.session.value)
    }
}
