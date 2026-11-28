package cc.slokker.goatlauncher.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** A categoria (a linha da coluna da esquerda) a que pertence cada página do painel das Definições. */
class SettingsCategoryTest {
    @Test
    fun aCategoryIsItsOwnCategory() {
        for (p in listOf(Page.Appearance, Page.Wallpaper, Page.Apps, Page.Ambient, Page.Startup, Page.Buttons, Page.General, Page.Account, Page.Report)) {
            assertEquals(p, p.category(hasPin = true))
        }
    }

    @Test
    fun pagesInsideACategoryBelongToIt() {
        assertEquals(Page.Wallpaper, Page.WallpaperFiles.category(hasPin = false))
        assertEquals(Page.Apps, Page.HiddenApps.category(hasPin = false))
        assertEquals(Page.General, Page.Language.category(hasPin = false))
        assertEquals(Page.Buttons, Page.ButtonApp.category(hasPin = false))
        assertEquals(Page.Account, Page.YouTube.category(hasPin = false))
        assertEquals(Page.Parental, Page.LockedApps.category(hasPin = true))
    }

    @Test
    fun theFirstCodeIsItsOwnCategoryAndChangingItBelongsToParental() {
        // Sem código, o primeiro abre-se a partir da página principal; com código, muda-se no Controlo parental.
        assertEquals(Page.PinSetup, Page.PinSetup.category(hasPin = false))
        assertEquals(Page.Parental, Page.PinSetup.category(hasPin = true))
    }

    @Test
    fun theMainPageHasNoCategoryAbove() {
        assertEquals(Page.Main, Page.Main.category(hasPin = false))
    }
}
