package cc.slokker.goatlauncher.ui.settings

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.ButtonMap
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.ui.state.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As páginas das Definições (de onde se chega a cada uma, os títulos) e as linhas do catálogo. */
class SettingsCatalogTest {
    private val s = TestStrings.create()
    private val defaults = SettingsValues(AccountSettings(), DeviceSettings())

    // ---- Páginas ----

    @Test
    fun everyPageLeadsBackToMain() {
        for (page in Page.entries) for (hasPin in listOf(false, true)) {
            val chain = generateSequence(page) { it.parent(hasPin) }.take(10).toList()
            assertEquals("$page", Page.Main, chain.last())
            assertTrue("$page tem um ciclo", chain.size < 10)
        }
        assertNull(Page.Main.parent(hasPin = true))
    }

    @Test
    fun parentsAndDepth() {
        assertEquals(Page.Apps, Page.HiddenApps.parent(false))
        assertEquals(Page.General, Page.Location.parent(false))
        assertEquals(Page.Buttons, Page.ButtonApp.parent(false))
        // O primeiro código liga o controlo parental a partir da página principal; mudá-lo, a partir do Controlo parental.
        assertEquals(Page.Main, Page.PinSetup.parent(hasPin = false))
        assertEquals(Page.Parental, Page.PinSetup.parent(hasPin = true))
        assertEquals(0, Page.Main.depth)
        assertEquals(1, Page.Appearance.depth)
        assertEquals(2, Page.WallpaperFiles.depth)
        assertEquals(2, Page.LockedApps.depth)
        assertEquals(2, Page.PinSetup.depth)
    }

    @Test
    fun titles() {
        val titles = Page.entries.associateWith { it.title(s) }
        assertTrue(titles.values.all { it.isNotEmpty() })
        // O código tem o título do Controlo parental, e o botão sem botão escolhido o dos Botões; o resto é único.
        assertEquals(titles[Page.Parental], titles[Page.PinSetup])
        assertEquals(titles[Page.Buttons], titles[Page.ButtonApp])
        assertEquals(Page.entries.size - 2, titles.values.toSet().size)
        assertEquals(s.buttons, Page.ButtonApp.title(s))
        val netflix = PageArgs(ButtonMap.SHIELD_NETFLIX_KEY to ButtonMap.SHIELD_NETFLIX_SCAN)
        assertEquals(s.buttonOpens(s.buttonNetflix), Page.ButtonApp.title(s, netflix))
    }

    // ---- Catálogo ----

    private fun all(list: List<Setting> = SettingsCatalog.all): List<Setting> =
        list.flatMap { if (it is Setting.Group) listOf(it) + all(it.rows) else listOf(it) }

    @Test
    fun catalogPagesAndKeys() {
        assertEquals(
            setOf(Page.Main, Page.Appearance, Page.Wallpaper, Page.Apps, Page.Ambient, Page.Startup, Page.General, Page.Parental),
            SettingsCatalog.all.map { it.page }.toSet(),
        )
        // As chaves são as das linhas da lista: não se podem repetir numa página (nem dentro de um cartão).
        val lists = Page.entries.map { SettingsCatalog.on(it) } + all().filterIsInstance<Setting.Group>().map { it.rows }
        for (list in lists) {
            val keys = list.map { it.key }
            assertEquals("$keys", keys.size, keys.toSet().size)
        }
        // Cada página do catálogo tem uma linha para o foco ao abrir.
        for (page in SettingsCatalog.all.map { it.page }.toSet()) {
            assertNotNull("$page", SettingsCatalog.on(page).firstOrNull { it.visible(defaults) && it.focusable })
        }
    }

    @Test
    fun everyToggleReadsWhatItWrites() {
        val toggles = all().filterIsInstance<Setting.Toggle>()
        assertTrue(toggles.size >= 10)
        for (t in toggles) for (values in listOf(defaults, flipped(defaults, t))) {
            val now = t.read(values)
            assertEquals(t.key, !now, t.read(t.write.apply(values, !now)))
        }
    }

    private fun flipped(values: SettingsValues, t: Setting.Toggle) = t.write.apply(values, !t.read(values))

    @Test
    fun everyCycleReadsEachOfItsOptions() {
        val cycles = all().filterIsInstance<Setting.Cycle<*>>()
        assertTrue(cycles.size >= 7)
        for (c in cycles) checkCycle(c)
    }

    private fun <T> checkCycle(c: Setting.Cycle<T>) {
        val options = c.options(defaults)
        // O valor por defeito é uma das opções (senão o OK saltava para a primeira).
        assertTrue(c.key, c.read(defaults) in options)
        for (option in options) {
            val values = c.write.apply(defaults, option)
            assertEquals(c.key, option, c.read(values))
            assertTrue(c.key, c.label(values, option, s).isNotEmpty())
        }
    }

    @Test
    fun everyChoiceIsSelectedAfterPickingIt() {
        val choices = all().filterIsInstance<Setting.Choice>()
        for (c in choices) {
            val start = SettingsValues(AccountSettings(showContinueWatching = false), DeviceSettings())
            assertTrue(c.key, c.selected(c.write.apply(start, Unit)))
        }
        // Escolher um estilo volta a ligar o "Continuar a ver".
        val shelf = choices.single { it.key == AccountSettings.STYLE_SHELF }
        val off = SettingsValues(AccountSettings(showContinueWatching = false), DeviceSettings())
        assertTrue(shelf.write.apply(off, Unit).account.showContinueWatching)
    }

    @Test
    fun rowsThatDependOnOtherSettings() {
        fun shown(page: Page, values: SettingsValues) = SettingsCatalog.on(page).filter { it.visible(values) }.map { it.key }
        val apple = defaults.copy(account = AccountSettings(layout = AccountSettings.LAYOUT_APPLE_TV))
        assertTrue("row" in shown(Page.Appearance, defaults))
        assertFalse("continue_watching" in shown(Page.Appearance, defaults))
        assertFalse("row" in shown(Page.Appearance, apple))
        assertTrue("continue_watching" in shown(Page.Appearance, apple))
        // O vidro não tem animações; só sombra e escurecer não têm cor.
        val glass = defaults.copy(account = AccountSettings(focusEffect = AccountSettings.FOCUS_GLASS))
        assertFalse("focus_animation" in shown(Page.Appearance, glass))
        assertTrue("focus_color" in shown(Page.Appearance, glass))
        val dim = defaults.copy(account = AccountSettings(focusEffect = AccountSettings.FOCUS_DIM))
        assertFalse("focus_color" in shown(Page.Appearance, dim))
        assertFalse("idle_keep_awake" in shown(Page.Ambient, defaults))
        assertTrue("idle_keep_awake" in shown(Page.Ambient, defaults.copy(account = AccountSettings(idleMinutes = 5))))
        assertEquals(listOf("autostart"), shown(Page.Startup, defaults))
        assertEquals(
            listOf("autostart", "autostart_wake", "autostart_delay"),
            shown(Page.Startup, defaults.copy(device = DeviceSettings(autostart = "com.example"))),
        )
        assertFalse("lock_from" in shown(Page.Parental, defaults))
        assertTrue("lock_to" in shown(Page.Parental, defaults.copy(device = DeviceSettings(lockFrom = 20, lockTo = 8))))
    }

    @Test
    fun lockHoursStartAtEightPm() {
        val hours = all().filterIsInstance<Setting.Toggle>().single { it.key == "lock_hours" }
        val on = hours.write.apply(defaults, true)
        assertEquals(20, on.device.lockFrom)
        assertEquals(8, on.device.lockTo)
        val off = hours.write.apply(on, false)
        assertNull(off.device.lockFrom)
        assertNull(off.device.lockTo)
    }
}
