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
    fun rotationIntervalDefaultsTo30Minutes() {
        assertEquals(30, AccountSettings().wallpaperMinutes)
        // Uma conta de antes desta definição.
        assertEquals(30, read("""{"clock_24h": true}""").wallpaperMinutes)
        // Valores fora da lista (ou de outro tipo) voltam aos 30 minutos.
        assertEquals(30, read("""{"wallpaper_minutes": 45}""").wallpaperMinutes)
        assertEquals(30, read("""{"wallpaper_minutes": "60"}""").wallpaperMinutes)
    }

    @Test
    fun rotationIntervalSurvivesARoundTrip() {
        assertEquals(listOf(5, 15, 30, 60, 180, 1440), AccountSettings.WALLPAPER_MINUTES)
        AccountSettings.WALLPAPER_MINUTES.forEach { minutes ->
            val saved = AccountSettings(wallpaperMinutes = minutes).toJson()
            assertEquals(minutes, saved.get("wallpaper_minutes").asInt)
            assertEquals(minutes, AccountSettings.fromJson(saved).wallpaperMinutes)
        }
    }

    @Test
    fun anIntervalChangeIsASyncedPatch() {
        // A sincronização manda só o que mudou (RFC 7396), como nas outras chaves da conta.
        val before = AccountSettings().toJson()
        val after = AccountSettings(wallpaperMinutes = 180).toJson()
        val patch = SettingsMerge.diff(before, after)
        assertEquals(setOf("wallpaper_minutes"), patch.keySet())
        assertEquals(180, AccountSettings.fromJson(SettingsMerge.apply(before, patch)).wallpaperMinutes)
    }

    @Test
    fun otherChoicesAreKept() {
        assertEquals(Wallpaper(Wallpaper.TYPE_BUILTIN, "aurora"), read("""{"wallpaper": {"type": "builtin", "value": "aurora"}}""").wallpaper)
        assertEquals(Wallpaper(Wallpaper.TYPE_FOLDER, "/storage/x"), read("""{"wallpaper": {"type": "folder", "value": "/storage/x"}}""").wallpaper)
    }
}
