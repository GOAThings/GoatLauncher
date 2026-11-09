package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.state.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** As listas de opções do ecrã inicial: estilos, efeitos de foco e fundos, com os ids guardados de sempre. */
class HomeStyleTest {
    private val s = TestStrings.create()

    @Test
    fun theAndroidStylesAreTheStoredOnesInSettingsOrder() {
        assertEquals(AccountSettings.STYLES, HomeStyle.ANDROID.map { it.continueStyle })
    }

    @Test
    fun everyStoredPairReadsAsItsStyle() {
        for (style in HomeStyle.ANDROID) {
            assertEquals(style, HomeStyle.of(AccountSettings.LAYOUT_ANDROID, style.continueStyle!!))
            // Na Apple TV o estilo guardado não conta.
            assertEquals(HomeStyle.AppleTv, HomeStyle.of(AccountSettings.LAYOUT_APPLE_TV, style.continueStyle!!))
        }
        assertEquals(HomeStyle.CardRow, HomeStyle.of(AccountSettings.LAYOUT_ANDROID, "downunder"))
        assertEquals(HomeStyle.Spotlight, HomeStyle.of(AccountSettings().copy(continueStyle = AccountSettings.STYLE_SPOTLIGHT)))
        assertEquals(HomeStyle.AppleTv, HomeStyle.of(AccountSettings().copy(layout = AccountSettings.LAYOUT_APPLE_TV)))
        assertEquals(HomeStyle.CardRow, HomeStyle.of(AccountSettings()))
    }

    @Test
    fun whatEachStyleShows() {
        // O que o ecrã inicial lia de seis booleanos.
        assertEquals(listOf(HomeStyle.Shelf, HomeStyle.Spotlight), HomeStyle.entries.filter { it.shelfHero })
        assertEquals(listOf(HomeStyle.Shelf, HomeStyle.Spotlight, HomeStyle.SideColumn), HomeStyle.entries.filter { it.heroBackdrop })
        assertEquals(listOf(HomeStyle.AppleTv), HomeStyle.entries.filterNot { it.hasAppsTab })
    }

    @Test
    fun labels() {
        assertEquals(HomeStyle.APPLE_TV_NAME, HomeStyle.AppleTv.label(s))
        assertEquals("Android TV · ${s.styleSpotlightNext}", HomeStyle.Spotlight.label(s))
        assertEquals("Android TV · ${s.styleRow}", HomeStyle.CardRow.label(s))
        assertEquals(
            listOf(s.styleRow, s.styleShelf, s.styleSpotlightNext, s.styleColumn, s.styleCarousel),
            HomeStyle.ANDROID.map { it.styleLabel(s) },
        )
    }

    @Test
    fun focusEffectsAreTheStoredOnesInSettingsOrder() {
        assertEquals(AccountSettings.FOCUS_EFFECTS, FocusEffectKind.entries.map { it.id })
        for (kind in FocusEffectKind.entries) assertEquals(kind, FocusEffectKind.of(kind.id))
        assertEquals(FocusEffectKind.BORDER, FocusEffectKind.of("sparkles"))
    }

    @Test
    fun focusEffectRules() {
        assertEquals(listOf(FocusEffectKind.DIM, FocusEffectKind.SHADOW), FocusEffectKind.entries.filterNot { it.takesColor })
        assertEquals(emptyList<String>(), FocusEffectKind.GLASS.animations)
        assertEquals(AccountSettings.FOCUS_ANIMATIONS, FocusEffectKind.AURORA.animations)
        // Uma animação que o efeito não aceita fica "nenhuma".
        assertEquals(AccountSettings.ANIM_NONE, FocusEffectKind.VIEWFINDER.animationOf(AccountSettings.ANIM_SHINE))
        assertEquals(AccountSettings.ANIM_PULSE, FocusEffectKind.VIEWFINDER.animationOf(AccountSettings.ANIM_PULSE))
        assertTrue(FocusEffectKind.GLASS.alwaysAnimated)
        assertFalse(FocusEffectKind.BORDER.alwaysAnimated)
        // Os tempos de sempre.
        assertEquals(FocusEffectKind.GLASS_PERIOD, FocusEffectKind.GLASS.period(AccountSettings.ANIM_NONE))
        assertEquals(1200, FocusEffectKind.RING.period(AccountSettings.ANIM_PULSE))
        assertEquals(3200, FocusEffectKind.AURORA.period(AccountSettings.ANIM_BREATH))
        assertEquals(4000, FocusEffectKind.AURORA.period(AccountSettings.ANIM_SHINE))
        assertEquals(2600, FocusEffectKind.BORDER.period(AccountSettings.ANIM_SHINE))
    }

    @Test
    fun focusEffectLabels() {
        assertEquals(s.animSpin, FocusEffectKind.AURORA.animationLabel(AccountSettings.ANIM_SHINE, s))
        assertEquals(s.animShine, FocusEffectKind.RING.animationLabel(AccountSettings.ANIM_SHINE, s))
        assertEquals(s.animNone, FocusEffectKind.RING.animationLabel(AccountSettings.ANIM_NONE, s))
        // Cada efeito com o seu nome (dez nomes diferentes).
        assertEquals(10, FocusEffectKind.entries.map { it.label(s) }.toSet().size)
    }

    @Test
    fun builtinWallpapers() {
        assertEquals(listOf("lake", "aurora", "sunset", "graphite"), Wallpapers.ALL.map { it.id })
        assertEquals(listOf(s.wpLake, s.wpAurora, s.wpSunset, s.wpGraphite), Wallpapers.ALL.map { it.name(s) })
        assertEquals("sunset", Wallpapers.builtinFor(Wallpaper(Wallpaper.TYPE_BUILTIN, "sunset")).id)
        assertEquals("lake", Wallpapers.builtinFor(Wallpaper(Wallpaper.TYPE_BUILTIN, "nebula")).id)
        assertEquals("lake", Wallpapers.builtinFor(Wallpaper(Wallpaper.TYPE_URL, "https://x/y.jpg")).id)
    }
}
