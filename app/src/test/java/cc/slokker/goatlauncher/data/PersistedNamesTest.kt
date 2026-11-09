package cc.slokker.goatlauncher.data

import cc.slokker.goatlauncher.AutoStart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Os nomes no disco: os ficheiros do DataStore e das SharedPreferences, as chaves de cada um e as pastas em `files`.
 * Uma instalação antiga só guarda as definições, a conta e as caches se a versão nova os ler pelo mesmo nome. Mudar um
 * destes valores é uma migração (nunca um arrumar).
 */
class PersistedNamesTest {
    @Test
    fun settingsDataStore() {
        assertEquals("settings", SettingsRepository.Keys.STORE)
        assertEquals(
            listOf(
                "account", "device", "account_dirty", "device_dirty", "keep_parental", "account_base", "device_base",
                "account_rev", "device_rev", "settings_etag",
            ),
            with(SettingsRepository.Keys) {
                listOf(ACCOUNT, DEVICE, ACCOUNT_DIRTY, DEVICE_DIRTY, KEEP_PARENTAL, ACCOUNT_BASE, DEVICE_BASE, ACCOUNT_REV, DEVICE_REV, ETAG)
                    .map { it.name }
            },
        )
    }

    @Test
    fun sessionDataStore() {
        assertEquals("session", SessionStore.Keys.STORE)
        assertEquals(listOf("token", "email", "device_name"), with(SessionStore.Keys) { listOf(TOKEN, EMAIL, DEVICE_NAME).map { it.name } })
    }

    @Test
    fun sharedPreferences() {
        assertEquals(listOf("autostart", "boot"), listOf(AutoStart.BOOT_PREFS, AutoStart.BOOT_KEY))
        assertEquals(listOf("pin_attempts", "failures", "failed_at"), listOf(PinAttemptStore.PREFS, PinAttemptStore.KEY_FAILURES, PinAttemptStore.KEY_FAILED_AT))
        assertEquals(listOf("app_index", "hash", "account"), listOf(AppIndex.Prefs.FILE, AppIndex.Prefs.HASH, AppIndex.Prefs.ACCOUNT))
        assertEquals(
            listOf("youtube", "refresh", "access", "access_until", "client_id", "client_secret", "client_at", "client_version", "device_id"),
            with(YouTubeSource.Prefs) { listOf(FILE, REFRESH, ACCESS, ACCESS_UNTIL, CLIENT_ID, CLIENT_SECRET, CLIENT_AT, CLIENT_VERSION, DEVICE_ID) },
        )
        assertEquals(
            listOf("weather", "temp", "code", "day", "lat", "lon", "city", "loc_at"),
            with(WeatherSource.Prefs) { listOf(FILE, TEMP, CODE, DAY, LAT, LON, CITY, LOC_AT) },
        )
        // As fotos do fundo: um ficheiro por fonte, chaves "<id>.<chave>" (o id é "potd" ou "apod").
        assertEquals(
            listOf("wikimedia_wallpapers", "nasa_wallpapers", "reddit_wallpapers"),
            listOf(OnlineWallpapers.WIKIMEDIA_PREFS, OnlineWallpapers.NASA_PREFS, OnlineWallpapers.OLD_REDDIT_PREFS),
        )
        assertEquals(
            listOf("shown_url", "shown_at", "list", "fetched_at"),
            listOf(PhotoPool.KEY_SHOWN_URL, PhotoPool.KEY_SHOWN_AT, PhotoPool.KEY_LIST, PhotoPool.KEY_FETCHED_AT),
        )
    }

    @Test
    fun filesReferencedFromTheSettings() {
        // "file:<nome>" nos ícones (device.icons) e o fundo "file" (account.wallpaper.value) apontam para estas pastas.
        assertEquals("icons", CustomIcons.ICONS_DIR)
        assertEquals(listOf("pack:", "file:"), listOf(CustomIcons.PACK, CustomIcons.FILE))
        assertEquals("wallpapers", ImageImport.WALLPAPERS_DIR)
        assertEquals("last_crash.txt", BugReport.CRASH_FILE)
    }
}
