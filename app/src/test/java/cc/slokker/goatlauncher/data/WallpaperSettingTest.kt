package cc.slokker.goatlauncher.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** O fundo guardado na conta: o defeito e as fontes de fotos online. */
class WallpaperSettingTest {
    private fun read(json: String) = AccountSettings.fromJson(JsonParser.parseString(json).asJsonObject)

    @Test
    fun aFreshInstallGetsWikimedia() {
        assertEquals(Wallpaper(Wallpaper.TYPE_WIKIMEDIA, WikimediaFeed.ID), AccountSettings().wallpaper)
        assertEquals(Wallpaper.DEFAULT, AccountSettings.fromJson(null).wallpaper)
        // Uma conta ainda vazia, ou sem fundo, ou com um fundo que não se lê.
        assertEquals(Wallpaper.DEFAULT, AccountSettings.fromJson(JsonObject()).wallpaper)
        assertEquals(Wallpaper.DEFAULT, read("""{"wallpaper": {"type": "builtin", "value": ""}}""").wallpaper)
    }

    @Test
    fun onlineSourcesSurviveARoundTrip() {
        listOf(
            Wallpaper(Wallpaper.TYPE_WIKIMEDIA, WikimediaFeed.ID),
            Wallpaper(Wallpaper.TYPE_NASA, ApodFeed.ID),
        ).forEach { wp ->
            val saved = AccountSettings(wallpaper = wp).toJson()
            assertEquals(wp.type, saved.getAsJsonObject("wallpaper").get("type").asString)
            assertEquals(wp.value, saved.getAsJsonObject("wallpaper").get("value").asString)
            assertEquals(wp, AccountSettings.fromJson(saved).wallpaper)
        }
    }

    @Test
    fun anyRedditWallpaperReadsAsWikimedia() {
        // O antigo defeito, um subreddit sugerido e um escrito à mão, guardados nesta TV.
        listOf("EarthPorn", "SkyPorn", "earthporn", "my_sub").forEach { sub ->
            assertEquals(sub, Wallpaper.DEFAULT, read("""{"wallpaper": {"type": "reddit", "value": "$sub"}}""").wallpaper)
        }
        // Vindo de outra TV da conta (o GET das definições passa pelo mesmo fromJson): o resto fica.
        val remote = read("""{"wallpaper": {"type": "reddit", "value": "CityPorn"}, "reddit_min_width": 3840, "clock_24h": false}""")
        assertEquals(Wallpaper.DEFAULT, remote.wallpaper)
        assertEquals(3840, remote.photoMinWidth)
        assertEquals(false, remote.clock24h)
        // Na próxima gravação sai o fundo novo; as chaves continuam as mesmas.
        val saved = remote.toJson()
        assertEquals("wikimedia", saved.getAsJsonObject("wallpaper").get("type").asString)
        assertEquals(3840, saved.get("reddit_min_width").asInt)
    }

    @Test
    fun otherChoicesAreKept() {
        assertEquals(Wallpaper(Wallpaper.TYPE_BUILTIN, "aurora"), read("""{"wallpaper": {"type": "builtin", "value": "aurora"}}""").wallpaper)
        assertEquals(Wallpaper(Wallpaper.TYPE_FOLDER, "/storage/x"), read("""{"wallpaper": {"type": "folder", "value": "/storage/x"}}""").wallpaper)
    }
}
