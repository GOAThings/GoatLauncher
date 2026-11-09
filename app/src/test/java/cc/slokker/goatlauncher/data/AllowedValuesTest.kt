package cc.slokker.goatlauncher.data

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As listas de valores ao lado de cada campo (AccountSettings/DeviceSettings) são as mesmas para as Definições e para o
 * fromJson: cada opção que o OK escolhe lê-se igual depois de gravada, e o OK percorre-as e volta ao início.
 */
class AllowedValuesTest {
    private fun account(key: String, value: Any): AccountSettings = AccountSettings.fromJson(JsonObject().apply {
        when (value) {
            is String -> addProperty(key, value)
            is Number -> addProperty(key, value)
        }
    })

    private fun device(key: String, value: Int): DeviceSettings =
        DeviceSettings.fromJson(JsonObject().apply { addProperty(key, value) })

    @Test
    fun everyOptionTheSettingsOfferReadsBackTheSame() {
        AccountSettings.IDLE_MINUTES.forEach { assertEquals(it, account("idle_minutes", it).idleMinutes) }
        AccountSettings.PHOTO_MIN_WIDTHS.forEach { assertEquals(it, account("reddit_min_width", it).photoMinWidth) }
        AccountSettings.WALLPAPER_MINUTES.forEach { assertEquals(it, account("wallpaper_minutes", it).wallpaperMinutes) }
        AccountSettings.YOUTUBE_FEEDS.forEach { assertEquals(it, account("youtube_feed", it).youtubeFeed) }
        AccountSettings.AMBIENT_STYLES.forEach { assertEquals(it, account("ambient_style", it).ambientStyle) }
        AccountSettings.FOCUS_ANIMATIONS.forEach { assertEquals(it, account("focus_animation", it).focusAnimation) }
        AccountSettings.FOCUS_EFFECTS.forEach { assertEquals(it, account("focus_effect", it).focusEffect) }
        AccountSettings.STYLES.forEach { assertEquals(it, account("continue_style", it).continueStyle) }
        AccountSettings.LAYOUTS.forEach { assertEquals(it, account("layout", it).layout) }
        DeviceSettings.AUTOSTART_DELAYS.forEach { assertEquals(it, device("autostart_delay", it).autostartDelay) }
    }

    @Test
    fun eachEffectOffersOnlyStoredAnimations() {
        for (effect in AccountSettings.FOCUS_EFFECTS) {
            val offered = AccountSettings.focusAnimations(effect)
            assertTrue(effect, AccountSettings.FOCUS_ANIMATIONS.containsAll(offered))
            // Quem tem animações começa sempre por "nenhuma" (o valor por defeito).
            if (offered.isNotEmpty()) assertEquals(AccountSettings.ANIM_NONE, offered.first())
        }
        assertEquals(emptyList<String>(), AccountSettings.focusAnimations(AccountSettings.FOCUS_GLASS))
        assertEquals(
            listOf(AccountSettings.ANIM_NONE, AccountSettings.ANIM_PULSE, AccountSettings.ANIM_BREATH),
            AccountSettings.focusAnimations(AccountSettings.FOCUS_UNDERLINE),
        )
    }

    @Test
    fun okGoesToTheNextAndBackToTheFirst() {
        assertEquals(1, AccountSettings.IDLE_MINUTES.after(0))
        assertEquals(0, AccountSettings.IDLE_MINUTES.after(30))
        // Um valor que o site gravou e as Definições não têm (15 min): o OK passa à primeira opção.
        assertEquals(0, AccountSettings.IDLE_MINUTES.after(15))
        assertEquals(AccountSettings.YT_CONTINUE, AccountSettings.YOUTUBE_FEEDS.after(AccountSettings.YT_BOTH))
        assertEquals(AccountSettings.YT_BOTH, AccountSettings.YOUTUBE_FEEDS.after(AccountSettings.YT_RECOMMENDED))
        assertEquals(AccountSettings.AMBIENT_BOUNCE, AccountSettings.AMBIENT_STYLES.after(AccountSettings.AMBIENT_PHOTOS))
        assertEquals(AccountSettings.AMBIENT_PHOTOS, AccountSettings.AMBIENT_STYLES.after(AccountSettings.AMBIENT_BOUNCE))
    }

    @Test
    fun theSiteMayStoreOtherNumbersWithinRange() {
        // Os números não são listas fechadas no fromJson (só os limites): o site pode gravar outros.
        assertEquals(15, account("idle_minutes", 15).idleMinutes)
        assertEquals(1280, account("reddit_min_width", 1280).photoMinWidth)
        assertEquals(60, device("autostart_delay", 60).autostartDelay)
        assertEquals(DeviceSettings.AUTOSTART_DELAY_MAX, device("autostart_delay", 999).autostartDelay)
    }
}
