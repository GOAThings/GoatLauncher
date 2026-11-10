package cc.slokker.goatlauncher.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonActionTest {
    private fun device(s: String) = DeviceSettings.fromJson(JsonParser.parseString(s).asJsonObject)

    @Test
    fun osIdsGravadosNuncaMudam() {
        assertEquals(
            listOf("home", "previous_app", "search", "android_settings", "sleep"),
            ButtonAction.entries.map { it.id },
        )
    }

    @Test
    fun umBotaoComAcaoLeEGravaSoAAcao() {
        val d = device("""{"buttons":[{"key":199,"scan":393,"action":"previous_app"},{"key":172,"scan":0,"app":"cc.slokker.itv"}]}""")
        assertEquals(
            listOf(ButtonMap(199, 393, "", ButtonAction.PREVIOUS_APP), ButtonMap(172, 0, "cc.slokker.itv")),
            d.buttons,
        )
        val written = d.toJson().getAsJsonArray("buttons")
        assertEquals("""{"key":199,"scan":393,"action":"previous_app"}""", written[0].toString())
        // Os botões com app gravam-se como sempre.
        assertEquals("""{"key":172,"scan":0,"app":"cc.slokker.itv"}""", written[1].toString())
        assertEquals(d.buttons, DeviceSettings.fromJson(d.toJson()).buttons)
    }

    @Test
    fun umaAcaoDesconhecidaSemAppFicaDeFora() {
        // Uma versão mais nova pode gravar uma ação que esta não conhece: o botão é ignorado, como um sem app.
        assertEquals(emptyList<ButtonMap>(), device("""{"buttons":[{"key":1,"scan":0,"action":"teleport"}]}""").buttons)
        assertEquals(listOf(ButtonMap(1, 0, "x")), device("""{"buttons":[{"key":1,"scan":0,"action":"teleport","app":"x"}]}""").buttons)
    }

    @Test
    fun desligarOEcraSoNoAndroid9() {
        assertFalse(ButtonAction.SLEEP in ButtonAction.available(27))
        assertTrue(ButtonAction.SLEEP in ButtonAction.available(28))
        assertEquals(ButtonAction.entries.size - 1, ButtonAction.available(23).size)
    }
}
