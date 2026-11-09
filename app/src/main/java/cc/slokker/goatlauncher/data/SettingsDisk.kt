package cc.slokker.goatlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import cc.slokker.goatlauncher.data.SettingsRepository.Keys
import kotlinx.coroutines.flow.first

private val Context.settingsStore by preferencesDataStore(Keys.STORE)

/**
 * Tudo o que o [SettingsRepository] guarda no disco, tal como está lá: os dois blobs em texto JSON, as marcas de
 * pendente, a base dos patches, as revisões e o ETag (as chaves em [SettingsRepository.Keys]).
 */
internal data class SettingsSnapshot(
    val account: String? = null,
    val device: String? = null,
    val accountDirty: Boolean = false,
    val deviceDirty: Boolean = false,
    val keepParental: Boolean = false,
    val accountBase: String? = null,
    val deviceBase: String? = null,
    val accountRev: Long? = null,
    val deviceRev: Long? = null,
    val etag: String? = null,
)

/** Onde o [SettingsRepository] lê e grava o [SettingsSnapshot]: o DataStore na app, uma memória nos testes. */
internal interface SettingsDisk {
    suspend fun load(): SettingsSnapshot

    /** Grava tudo de uma vez. Um valor null tira a chave (a base, as revisões, o ETag); um blob null fica como está. */
    suspend fun save(snapshot: SettingsSnapshot)
}

/** O DataStore `settings` de sempre, com as mesmas chaves e valores (ver PersistedNamesTest e SettingsDiskTest). */
internal class DataStoreSettingsDisk(private val store: DataStore<Preferences>) : SettingsDisk {
    constructor(context: Context) : this(context.settingsStore)

    override suspend fun load(): SettingsSnapshot {
        val p = store.data.first()
        return SettingsSnapshot(
            account = p[Keys.ACCOUNT],
            device = p[Keys.DEVICE],
            accountDirty = p[Keys.ACCOUNT_DIRTY] ?: false,
            deviceDirty = p[Keys.DEVICE_DIRTY] ?: false,
            keepParental = p[Keys.KEEP_PARENTAL] ?: false,
            accountBase = p[Keys.ACCOUNT_BASE],
            deviceBase = p[Keys.DEVICE_BASE],
            accountRev = p[Keys.ACCOUNT_REV],
            deviceRev = p[Keys.DEVICE_REV],
            etag = p[Keys.ETAG],
        )
    }

    override suspend fun save(snapshot: SettingsSnapshot) {
        store.edit {
            snapshot.account?.let { v -> it[Keys.ACCOUNT] = v }
            snapshot.device?.let { v -> it[Keys.DEVICE] = v }
            it[Keys.ACCOUNT_DIRTY] = snapshot.accountDirty
            it[Keys.DEVICE_DIRTY] = snapshot.deviceDirty
            it[Keys.KEEP_PARENTAL] = snapshot.keepParental
            snapshot.accountBase?.let { b -> it[Keys.ACCOUNT_BASE] = b } ?: it.remove(Keys.ACCOUNT_BASE)
            snapshot.deviceBase?.let { b -> it[Keys.DEVICE_BASE] = b } ?: it.remove(Keys.DEVICE_BASE)
            snapshot.accountRev?.let { r -> it[Keys.ACCOUNT_REV] = r } ?: it.remove(Keys.ACCOUNT_REV)
            snapshot.deviceRev?.let { r -> it[Keys.DEVICE_REV] = r } ?: it.remove(Keys.DEVICE_REV)
            snapshot.etag?.let { e -> it[Keys.ETAG] = e } ?: it.remove(Keys.ETAG)
        }
    }
}
