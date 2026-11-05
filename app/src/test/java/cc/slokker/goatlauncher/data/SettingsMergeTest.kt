package cc.slokker.goatlauncher.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMergeTest {
    private fun json(s: String): JsonObject = JsonParser.parseString(s).asJsonObject

    @Test
    fun diffSendsOnlyWhatChanged() {
        val base = json("""{"apps":["a","b"],"hidden":["x"],"names":{"a":"A","b":"B"},"autostart":"a","lock_settings":false}""")
        val local = json("""{"apps":["b","a"],"hidden":["x"],"names":{"a":"A2","b":"B"},"lock_settings":false}""")
        assertEquals(json("""{"apps":["b","a"],"names":{"a":"A2"},"autostart":null}"""), SettingsMerge.diff(base, local))
    }

    @Test
    fun rebaseKeepsSiteChangesAndTvOrder() {
        val base = json("""{"apps":["a","b","c"],"hidden":["x"],"locked":[]}""")
        // Na TV: reordenou e escondeu "y".
        val local = json("""{"apps":["c","a","b"],"hidden":["x","y"],"locked":[]}""")
        // No site, entretanto: escondeu "z", bloqueou "a" e pôs "b" primeiro.
        val server = json("""{"apps":["b","a","c"],"hidden":["x","z"],"locked":["a"]}""")
        val merged = SettingsMerge.rebase(server, base, local, ordered = setOf("apps"))
        assertEquals(json("""{"apps":["c","a","b"],"hidden":["x","z","y"],"locked":["a"]}"""), merged)
    }

    @Test
    fun applyFollowsRfc7396() {
        val base = json("""{"wallpaper":{"type":"builtin","value":"lake"},"clock_24h":true}""")
        val patch = json("""{"wallpaper":{"value":"aurora"},"clock_24h":null}""")
        assertEquals(json("""{"wallpaper":{"type":"builtin","value":"aurora"}}"""), SettingsMerge.apply(base, patch))
    }

    @Test
    fun linkingKeepsTheLocalParentalLockAndTakesTheRestFromTheAccount() {
        val local = DeviceSettings(pin = "salt:hash", locked = setOf("kids.blocked"), lockSettings = true, lockFrom = 20, lockTo = 8, apps = listOf("local"))
        val remote = json("""{"apps":["account"],"autostart":"account.app","locked":["other"],"lock_settings":false}""")

        val kept = DeviceSettings.fromJson(remote).withParentalOf(local)

        assertEquals(listOf("account"), kept.apps)
        assertEquals("account.app", kept.autostart)
        assertEquals("salt:hash", kept.pin)
        assertEquals(setOf("kids.blocked"), kept.locked)
        assertEquals(true, kept.lockSettings)
        assertEquals(20, kept.lockFrom)
        assertEquals(8, kept.lockTo)
        // E é isso que segue para a conta: o controlo parental desta TV.
        val patch = SettingsMerge.diff(remote, kept.toJson())
        assertEquals("salt:hash", patch.get("pin").asString)
        assertEquals(json("""{"l":["kids.blocked"]}""").get("l"), patch.get("locked"))
        assertEquals(false, patch.has("apps"))
    }
}

class PinAttemptsTest {
    private fun failures(n: Int, at: Long = 1_000L): PinAttempts {
        var attempts = PinAttempts()
        repeat(n) { attempts = attempts.failed(at) }
        return attempts
    }

    @Test
    fun theFirstFailuresDoNotWaitAndThenTheDelayGrows() {
        assertEquals(0L, failures(3).delayMs)
        val delays = (4..12).map { failures(it).delayMs }
        assertEquals(delays.sorted(), delays)
        assertEquals(true, delays.first() > 0)
        assertEquals(true, delays[1] > delays[0] && delays[5] > delays[4])
        // Fica no máximo, sem crescer para sempre.
        assertEquals(failures(100).delayMs, failures(10).delayMs)
    }

    @Test
    fun waitCountsDownFromTheLastFailure() {
        val attempts = failures(4, at = 10_000L)
        assertEquals(attempts.delayMs, attempts.remainingMs(10_000L))
        assertEquals(attempts.delayMs - 5_000, attempts.remainingMs(15_000L))
        assertEquals(0L, attempts.remainingMs(10_000L + attempts.delayMs))
        assertEquals(0L, attempts.remainingMs(Long.MAX_VALUE / 2))
    }

    @Test
    fun aRebootNeverShortensTheWait() {
        val attempts = failures(5, at = 3_600_000L)
        // O relógio recomeçou do zero: conta a espera inteira.
        assertEquals(attempts.delayMs, attempts.remainingMs(1_000L))
    }

    @Test
    fun aFailureAfterTheWaitAsksForMore() {
        val first = failures(4, at = 1_000L)
        val next = first.failed(1_000L + first.delayMs)
        assertEquals(true, next.delayMs > first.delayMs)
        assertEquals(next.delayMs, next.remainingMs(1_000L + first.delayMs))
    }
}
