package cc.slokker.goatlauncher.data

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** A disposição e o estilo do "Continuar a ver" guardados na conta. */
class LayoutSettingTest {
    private fun read(vararg pairs: Pair<String, String>) =
        AccountSettings.fromJson(JsonObject().apply { pairs.forEach { (k, v) -> addProperty(k, v) } })

    @Test
    fun knownStylesAreKept() {
        AccountSettings.STYLES.forEach { assertEquals(it, read("continue_style" to it).continueStyle) }
    }

    @Test
    fun unknownValuesFallBack() {
        val settings = read("layout" to "somethingNew", "continue_style" to "downunder")
        assertEquals(AccountSettings.LAYOUT_ANDROID, settings.layout)
        assertEquals(AccountSettings.STYLE_ROW, settings.continueStyle)
    }

    @Test
    fun aStyleSavedAsALayoutBecomesTheStyle() {
        val settings = read("layout" to "carousel", "continue_style" to "shelf")
        assertEquals(AccountSettings.LAYOUT_ANDROID, settings.layout)
        assertEquals(AccountSettings.STYLE_CAROUSEL, settings.continueStyle)
    }

    @Test
    fun styleSurvivesARoundTrip() {
        val saved = AccountSettings(continueStyle = AccountSettings.STYLE_COLUMN).toJson()
        assertEquals(AccountSettings.STYLE_COLUMN, AccountSettings.fromJson(saved).continueStyle)
    }
}
