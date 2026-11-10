package cc.slokker.goatlauncher.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O formato das definições guardadas no disco e na conta (`account` e `device`): o backend e o site guardam e editam o
 * mesmo JSON, e as instalações antigas leem-no ao arrancar. Os ficheiros em `src/test/resources/formats/` têm todas as
 * chaves, cada uma com um valor diferente do defeito, mais uma chave que só o site conhece.
 *
 * Uma chave nova nas definições tem de entrar no ficheiro e em [ACCOUNT_KEYS] / [DEVICE_KEYS]; mudar o nome ou o valor
 * de uma chave existente parte as instalações antigas e as outras TVs da conta (não se faz).
 */
class SettingsFormatTest {
    private fun fixture(name: String): JsonObject =
        JsonParser.parseString(requireNotNull(javaClass.classLoader!!.getResource("formats/$name")) { name }.readText()).asJsonObject

    private fun json(s: String): JsonObject = JsonParser.parseString(s).asJsonObject
    private fun account(s: String) = AccountSettings.fromJson(json(s))
    private fun device(s: String) = DeviceSettings.fromJson(json(s))

    @Test
    fun everyAccountKeyRoundTrips() {
        val stored = fixture("account.json")
        assertEquals(stored, AccountSettings.fromJson(stored).toJson())
        // As chaves que esta versão escreve são exatamente as do contrato (mais as que vierem do site).
        assertEquals(ACCOUNT_KEYS, AccountSettings().toJson().keySet())
        assertEquals(ACCOUNT_KEYS + "site_only_key", stored.keySet())
        // Cada valor do ficheiro é diferente do defeito: uma chave que deixasse de ser lida não passava despercebida.
        val defaults = AccountSettings().toJson()
        for (key in ACCOUNT_KEYS) assertNotEquals(key, defaults.get(key), stored.get(key))
    }

    @Test
    fun everyDeviceKeyRoundTrips() {
        val stored = fixture("device.json")
        assertEquals(stored, DeviceSettings.fromJson(stored).toJson())
        assertEquals(DEVICE_KEYS + "site_only_key", stored.keySet())
        val read = DeviceSettings.fromJson(stored)
        assertEquals(DEVICE_KEYS, read.copy(raw = JsonObject()).toJson().keySet())
        // Sem valor, as opcionais nem aparecem (o patch manda null e o servidor apaga-as).
        assertEquals(DEVICE_KEYS - setOf("autostart", "pin", "lock_hours", "time_limit", "search_app", "location"), DeviceSettings().toJson().keySet())
        val defaults = DeviceSettings().toJson()
        for (key in DEVICE_KEYS) assertNotEquals(key, defaults.get(key), stored.get(key))
    }

    @Test
    fun theFixturesReadAsTheirValues() {
        val a = AccountSettings.fromJson(fixture("account.json"))
        assertEquals(Wallpaper(Wallpaper.TYPE_NASA, ApodFeed.ID), a.wallpaper)
        assertEquals(AccountSettings.LAYOUT_APPLE_TV, a.layout)
        assertEquals(AccountSettings.STYLE_CAROUSEL, a.continueStyle)
        assertEquals(3840, a.photoMinWidth)
        assertEquals(180, a.wallpaperMinutes)
        assertEquals(AccountSettings.FOCUS_AURORA, a.focusEffect)
        assertEquals("#38BDF8", a.focusColor)
        val d = DeviceSettings.fromJson(fixture("device.json"))
        assertEquals(listOf("com.netflix.ninja", "cc.slokker.itv", "com.google.android.youtube.tv"), d.apps)
        assertEquals(listOf(ButtonMap(199, 393, "com.netflix.ninja"), ButtonMap(172, 0, "cc.slokker.itv")), d.buttons)
        assertEquals(20, d.lockFrom)
        assertEquals(8, d.lockTo)
        assertEquals(Place("Lisboa, Lisboa, Portugal", 38.7223, -9.1393), d.location)
        assertEquals("pack:dev.pack/netflix", d.icons["com.netflix.ninja"])
    }

    @Test
    fun keysOnlyTheSiteKnowsSurviveAChangeOnTheTv() {
        val account = AccountSettings.fromJson(fixture("account.json")).copy(clock24h = true).toJson()
        assertEquals(fixture("account.json").get("site_only_key"), account.get("site_only_key"))
        val device = DeviceSettings.fromJson(fixture("device.json")).copy(autostart = null).toJson()
        assertEquals("kept", device.get("site_only_key").asString)
        assertFalse(device.has("autostart"))
    }

    @Test
    fun everyWallpaperTypeRoundTripsAndRedditReadsAsTheDefault() {
        for (wp in listOf(
            Wallpaper(Wallpaper.TYPE_BUILTIN, "aurora"),
            Wallpaper(Wallpaper.TYPE_URL, "https://images.example/a.jpg"),
            Wallpaper(Wallpaper.TYPE_FILE, "wall-1730000000000.jpg"),
            Wallpaper(Wallpaper.TYPE_FOLDER, "/storage/emulated/0/Pictures"),
            Wallpaper(Wallpaper.TYPE_WIKIMEDIA, WikimediaFeed.ID),
            Wallpaper(Wallpaper.TYPE_NASA, ApodFeed.ID),
        )) {
            val saved = AccountSettings(wallpaper = wp).toJson().getAsJsonObject("wallpaper")
            assertEquals(json("""{"type": "${wp.type}", "value": "${wp.value}"}"""), saved)
            assertEquals(wp, AccountSettings.fromJson(AccountSettings(wallpaper = wp).toJson()).wallpaper)
        }
        assertEquals(listOf("wikimedia", "potd", "nasa", "apod"), listOf(Wallpaper.TYPE_WIKIMEDIA, WikimediaFeed.ID, Wallpaper.TYPE_NASA, ApodFeed.ID))
        assertEquals(Wallpaper.DEFAULT, account("""{"wallpaper": {"type": "reddit", "value": "EarthPorn"}}""").wallpaper)
        assertEquals(Wallpaper(Wallpaper.TYPE_WIKIMEDIA, WikimediaFeed.ID), Wallpaper.DEFAULT)
    }

    @Test
    fun legacyValuesAreMigratedOnRead() {
        // O antigo botão Netflix da Shield passa a ser o primeiro botão; ao gravar, a chave antiga sai.
        val old = device("""{"netflix_button": "com.netflix.ninja", "kept": 1}""")
        assertEquals(listOf(ButtonMap(ButtonMap.SHIELD_NETFLIX_KEY, ButtonMap.SHIELD_NETFLIX_SCAN, "com.netflix.ninja")), old.buttons)
        assertEquals(listOf(199, 393), listOf(ButtonMap.SHIELD_NETFLIX_KEY, ButtonMap.SHIELD_NETFLIX_SCAN))
        val saved = old.toJson()
        assertFalse(saved.has("netflix_button"))
        assertEquals(1, saved.get("kept").asInt)
        assertEquals(JsonParser.parseString("""[{"key": 199, "scan": 393, "app": "com.netflix.ninja"}]"""), saved.get("buttons"))
        // Com "buttons" (mesmo vazio), o antigo não conta: a pessoa já tirou os botões.
        assertEquals(emptyList<ButtonMap>(), device("""{"netflix_button": "com.netflix.ninja", "buttons": []}""").buttons)
        // Um estilo guardado como disposição (versões de teste) passa a ser o estilo.
        val styled = account("""{"layout": "spotlight", "continue_style": "row"}""")
        assertEquals(AccountSettings.LAYOUT_ANDROID, styled.layout)
        assertEquals(AccountSettings.STYLE_SPOTLIGHT, styled.continueStyle)
    }

    @Test
    fun accountValuesOutsideTheAllowedOnesFallBack() {
        val bad = account(
            """{"clock_24h": "no", "layout": "tizen", "continue_style": "downunder", "reddit_min_width": -5,
               "wallpaper_minutes": 7, "idle_minutes": -1, "ambient_style": "matrix", "youtube_feed": "shorts",
               "focus_effect": "sparkle", "focus_animation": "spin", "focus_color": "red", "icon_background": "#12345",
               "language": 5, "wallpaper": {"type": "builtin"}}""",
        )
        val defaults = AccountSettings()
        assertEquals(defaults.clock24h, bad.clock24h)
        assertEquals(AccountSettings.LAYOUT_ANDROID, bad.layout)
        assertEquals(AccountSettings.STYLE_ROW, bad.continueStyle)
        assertEquals(0, bad.photoMinWidth)
        assertEquals(30, bad.wallpaperMinutes)
        assertEquals(0, bad.idleMinutes)
        assertEquals(AccountSettings.AMBIENT_PHOTOS, bad.ambientStyle)
        assertEquals(AccountSettings.YT_BOTH, bad.youtubeFeed)
        assertEquals(AccountSettings.FOCUS_BORDER, bad.focusEffect)
        assertEquals(AccountSettings.ANIM_NONE, bad.focusAnimation)
        assertEquals(AccountSettings.COLOR_AUTO, bad.focusColor)
        assertEquals(AccountSettings.BG_GRAPHITE, bad.iconBackground)
        assertEquals("auto", bad.language)
        assertEquals(Wallpaper.DEFAULT, bad.wallpaper)
        // Os valores escritos nunca mudam de nome.
        assertEquals(
            listOf("row", "shelf", "spotlight", "column", "carousel", "android", "appletv", "photos", "bounce", "both", "continue", "recommended"),
            AccountSettings.STYLES + listOf(
                AccountSettings.LAYOUT_ANDROID, AccountSettings.LAYOUT_APPLE_TV, AccountSettings.AMBIENT_PHOTOS, AccountSettings.AMBIENT_BOUNCE,
                AccountSettings.YT_BOTH, AccountSettings.YT_CONTINUE, AccountSettings.YT_RECOMMENDED,
            ),
        )
        assertEquals(
            listOf("border", "glow", "glass", "reflection", "aurora", "viewfinder", "underline", "ring", "dim", "shadow"),
            AccountSettings.FOCUS_EFFECTS,
        )
        assertEquals(
            listOf("none", "pulse", "breath", "shine", "auto", "graphite", "none"),
            listOf(
                AccountSettings.ANIM_NONE, AccountSettings.ANIM_PULSE, AccountSettings.ANIM_BREATH, AccountSettings.ANIM_SHINE,
                AccountSettings.COLOR_AUTO, AccountSettings.BG_GRAPHITE, AccountSettings.BG_NONE,
            ),
        )
        assertEquals("none", account("""{"icon_background": "none"}""").iconBackground)
    }

    @Test
    fun deviceValuesOutsideTheAllowedOnesFallBack() {
        val bad = device(
            """{"apps": ["a", "b", "a", 3], "hidden": "x", "icons": {"a": 1, "b": "pack:p/b"}, "autostart": " ",
               "autostart_delay": 500, "pin": "no-salt", "lock_hours": [25, -3], "location": {"name": "x", "lat": 91, "lon": 0},
               "buttons": [{"key": 1, "app": "a"}, {"app": "no.key"}, {"key": 2}, {"key": 3, "app": "c"}, {"key": 4, "app": "d"},
                           {"key": 5, "app": "e"}, {"key": 6, "app": "f"}, {"key": 7, "app": "g"}]}""",
        )
        assertEquals(listOf("a", "b"), bad.apps)
        assertEquals(emptySet<String>(), bad.hidden)
        assertEquals(mapOf("b" to "pack:p/b"), bad.icons)
        assertNull(bad.autostart)
        assertEquals(120, bad.autostartDelay)
        assertNull(bad.pin)
        assertEquals(23, bad.lockFrom)
        assertEquals(0, bad.lockTo)
        assertNull(bad.location)
        // Sem tecla ou sem app fica de fora; no máximo ButtonMap.MAX; o scan em falta é 0 (qualquer).
        assertEquals(5, ButtonMap.MAX)
        assertEquals(listOf(ButtonMap(1, 0, "a"), ButtonMap(3, 0, "c"), ButtonMap(4, 0, "d"), ButtonMap(5, 0, "e"), ButtonMap(6, 0, "f")), bad.buttons)
        assertNull(device("""{"lock_hours": [20]}""").lockFrom)
        assertEquals(0, device("""{"autostart_delay": -4}""").autostartDelay)
    }

    @Test
    fun setsAndMapsAreWrittenSortedSoTheDiffIsStable() {
        val d = DeviceSettings(
            apps = listOf("z", "a"),
            hidden = linkedSetOf("z", "a"),
            locked = linkedSetOf("y", "b"),
            noContinue = linkedSetOf("x", "c"),
            icons = linkedMapOf("z" to "file:z.png", "a" to "file:a.png"),
            names = linkedMapOf("z" to "Z", "a" to "A"),
        ).toJson()
        // A fila é a ordem da pessoa; o resto vai por ordem alfabética.
        assertEquals(JsonArray().apply { add("z"); add("a") }, d.get("apps"))
        assertEquals(JsonArray().apply { add("a"); add("z") }, d.get("hidden"))
        assertEquals(JsonArray().apply { add("b"); add("y") }, d.get("locked"))
        assertEquals(JsonArray().apply { add("c"); add("x") }, d.get("no_continue"))
        assertEquals(listOf("a", "z"), d.getAsJsonObject("icons").keySet().toList())
        assertEquals(listOf("a", "z"), d.getAsJsonObject("names").keySet().toList())
    }

    @Test
    fun aPatchFromTheDefaultsRebuildsTheFixtures() {
        // RFC 7396, como na GoatTV: o que a TV manda ao servidor chega para ele ter o mesmo JSON.
        for ((name, defaults) in listOf("account.json" to AccountSettings().toJson(), "device.json" to DeviceSettings().toJson())) {
            val stored = fixture(name)
            assertEquals(name, stored, SettingsMerge.apply(defaults, SettingsMerge.diff(defaults, stored)))
        }
        // Tirar o código manda null (o servidor apaga a chave).
        val withPin = DeviceSettings.fromJson(fixture("device.json")).toJson()
        val patch = SettingsMerge.diff(withPin, DeviceSettings.fromJson(fixture("device.json")).copy(pin = null).toJson())
        assertTrue(patch.get("pin").isJsonNull)
    }

    private companion object {
        val ACCOUNT_KEYS = setOf(
            "wallpaper", "clock_24h", "show_greeting", "show_continue_watching", "show_app_name", "layout", "continue_style",
            "show_weather", "reddit_min_width", "wallpaper_minutes", "idle_minutes", "idle_keep_awake", "ambient_style",
            "nav_sounds", "youtube_feed", "language", "night_mode", "night_auto", "focus_effect", "focus_animation",
            "focus_color", "icon_background",
        )
        val DEVICE_KEYS = setOf(
            "apps", "hidden", "icons", "names", "no_continue", "autostart", "autostart_wake", "autostart_delay", "buttons",
            "pin", "locked", "lock_settings", "lock_hours", "time_limit", "search_app", "location",
        )
    }
}
