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
}
