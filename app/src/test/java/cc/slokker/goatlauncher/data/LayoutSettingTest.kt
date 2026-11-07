package cc.slokker.goatlauncher.data

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** A disposição guardada na conta: as cinco conhecidas ficam, outra qualquer (de uma versão mais nova) volta à Android. */
class LayoutSettingTest {
    private fun layoutOf(value: String) = AccountSettings.fromJson(JsonObject().apply { addProperty("layout", value) }).layout

    @Test
    fun knownLayoutsAreKept() {
        AccountSettings.LAYOUTS.forEach { assertEquals(it, layoutOf(it)) }
    }

    @Test
    fun unknownLayoutFallsBackToAndroid() {
        assertEquals(AccountSettings.LAYOUT_ANDROID, layoutOf("downunder"))
    }

    @Test
    fun layoutSurvivesARoundTrip() {
        val saved = AccountSettings(layout = AccountSettings.LAYOUT_CAROUSEL).toJson()
        assertEquals(AccountSettings.LAYOUT_CAROUSEL, AccountSettings.fromJson(saved).layout)
    }
}
