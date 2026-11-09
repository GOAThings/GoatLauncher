package cc.slokker.goatlauncher.ui.home

import androidx.compose.ui.graphics.Color
import cc.slokker.goatlauncher.data.LauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O que está por cima do Início e para onde o foco volta; a tabela das teclas do Início. */
class HomeStateTest {
    private fun app(pkg: String) = LauncherApp(pkg, pkg, null, null, Color.Black, null)
    private val a = app("a")
    private val b = app("b")

    @Test
    fun closingTheMenuFocusesItsApp() {
        val s = HomeState("b")
        s.open(Overlay.Menu(a))
        assertEquals(a, s.menu)
        val before = s.focusRequest
        s.closeMenu()
        assertNull(s.menu)
        assertNull(s.overlay)
        assertEquals("a", s.targetPkg)
        assertEquals(before + 1, s.focusRequest)
    }

    @Test
    fun theMenuOpenedByTheRightCodeKeepsTheFocus() {
        // Pressão longa com as definições protegidas: o código, e o código certo abre o menu antes de o código fechar.
        val s = HomeState("a")
        s.open(Overlay.Pin)
        s.open(Overlay.Menu(a))
        val before = s.focusRequest
        s.close(Overlay.Pin, onSettings = false) { "x" }
        assertEquals(before, s.focusRequest)
        assertEquals(a, s.menu)
    }

    @Test
    fun aCancelledCodeGivesTheFocusBackToTheLastApp() {
        val s = HomeState(null)
        s.focusedPkg = "b"
        s.open(Overlay.Pin)
        s.close(Overlay.Pin, onSettings = false) { "first" }
        assertEquals("b", s.targetPkg)
        // Sem app com foco ainda: o último favorito.
        val t = HomeState(null)
        t.open(Overlay.Pin)
        t.close(Overlay.Pin, onSettings = false) { "first" }
        assertEquals("first", t.targetPkg)
    }

    @Test
    fun aCodeOverTheSettingsGivesTheFocusBackToTheSettings() {
        val s = HomeState("a")
        s.open(Overlay.Pin)
        val request = s.focusRequest
        s.close(Overlay.Pin, onSettings = true)
        assertEquals(1, s.settingsRefocus)
        assertEquals(request, s.focusRequest)
    }

    @Test
    fun theLockFromTheMenu() {
        // Menu → "Bloquear com código": o menu fecha (foco ao banner) e o código abre; cancelado, o foco volta à app.
        val s = HomeState("a")
        s.open(Overlay.Menu(a))
        s.closeMenu()
        s.open(Overlay.Pin)
        s.close(Overlay.Pin) { null }
        assertEquals("a", s.targetPkg)
        assertNull(s.overlay)
    }

    @Test
    fun dialogsAreSeparateWindows() {
        val s = HomeState("a")
        s.open(Overlay.Menu(a))
        s.closeMenu()
        s.open(Overlay.Rename(a))
        assertTrue(s.dialogOpen)
        assertEquals(a, s.renaming)
        val request = s.focusRequest
        // A janela do Início volta a ter o foco onde estava: nada a pedir.
        s.close(Overlay.Rename(a))
        assertFalse(s.dialogOpen)
        assertEquals(request, s.focusRequest)
        s.open(Overlay.Icon(b))
        assertEquals(b, s.choosingIcon)
        assertTrue(s.dialogOpen)
        s.dismissForHome()
        assertNull(s.choosingIcon)
        // Fechar o que já não está aberto não faz nada.
        s.close(Overlay.Icon(b))
        assertEquals(request, s.focusRequest)
    }

    @Test
    fun theCodeHoldsTheFocusWhileTheViewModelShowsIt() {
        var shown = false
        val s = HomeState("a") { shown }
        assertFalse(s.holdsFocus)
        shown = true
        assertTrue(s.holdsFocus)
    }

    @Test
    fun theOkThatEndsAMoveDoesNotOpenTheApp() {
        val s = HomeState("a")
        assertFalse(s.endMove(1_000))
        s.startMove("a")
        assertEquals("a", s.moving)
        assertTrue(s.endMove(10_000))
        assertNull(s.moving)
        assertFalse(s.clickOpensApp(10_000 + HomeState.MOVE_CLICK_GUARD_MS))
        assertTrue(s.clickOpensApp(10_001 + HomeState.MOVE_CLICK_GUARD_MS))
    }

    @Test
    fun theNeighbourTakesTheFocusWhenAnAppLeavesTheRow() {
        val row = listOf("a", "b", "c")
        assertEquals("b", HomeState.neighbour(row, "a"))
        assertEquals("c", HomeState.neighbour(row, "b"))
        assertEquals("b", HomeState.neighbour(row, "c"))
        assertNull(HomeState.neighbour(listOf("a"), "a"))
    }

    // ---- Teclas ----

    private val home = HomeKeyContext(moving = false, pinOpen = false, menuOpen = false, onApps = false, onSettings = false, appleTv = false, scrolled = false)

    private fun both(key: HomeKey, c: HomeKeyContext) = homeKey(key, down = true, up = false, c) to homeKey(key, down = false, up = true, c)

    @Test
    fun movingOnHomeOnlyLeftAndRightInsideTheFavorites() {
        val c = home.copy(moving = true, movingIndex = 0, appCount = 8)
        assertEquals(HomeAction.Move(1, allowed = true), homeKey(HomeKey.Right, true, false, c))
        assertEquals(HomeAction.Move(-1, allowed = false), homeKey(HomeKey.Left, true, false, c))
        assertEquals(HomeAction.Move(APPS_COLUMNS, allowed = false), homeKey(HomeKey.Down, true, false, c))
        // A quinta não passa para lá dos favoritos.
        assertEquals(HomeAction.Move(1, allowed = false), homeKey(HomeKey.Right, true, false, c.copy(movingIndex = FavoriteCount - 1)))
        // Nas Apps e na Apple TV anda-se pela lista toda, também ↑ ↓.
        assertEquals(HomeAction.Move(-APPS_COLUMNS, allowed = true), homeKey(HomeKey.Up, true, false, c.copy(onApps = true)))
        assertEquals(HomeAction.Move(-1, allowed = true), homeKey(HomeKey.Left, true, false, c.copy(appleTv = true)))
        // A outra metade da tecla fica aqui.
        assertEquals(HomeAction.Swallow, homeKey(HomeKey.Right, false, true, c))
    }

    @Test
    fun okOrBackEndsTheMoveWhenReleased() {
        val c = home.copy(moving = true)
        assertEquals(HomeAction.Swallow to HomeAction.EndMove, both(HomeKey.Ok, c))
        assertEquals(HomeAction.Swallow to HomeAction.EndMove, both(HomeKey.Back, c))
        assertEquals(HomeAction.Pass to HomeAction.Pass, both(HomeKey.Other, c))
    }

    @Test
    fun backClosesWhatIsOnTop() {
        assertEquals(HomeAction.Swallow to HomeAction.CancelPin, both(HomeKey.Back, home.copy(pinOpen = true, menuOpen = true)))
        assertEquals(HomeAction.Swallow to HomeAction.CloseMenu, both(HomeKey.Back, home.copy(menuOpen = true)))
        // As Definições tratam o seu Voltar.
        assertEquals(HomeAction.Pass to HomeAction.Pass, both(HomeKey.Back, home.copy(onSettings = true)))
        // O código fica por cima das Definições.
        assertEquals(HomeAction.Swallow to HomeAction.CancelPin, both(HomeKey.Back, home.copy(onSettings = true, pinOpen = true)))
    }

    @Test
    fun backNeverLeavesTheHomeScreen() {
        assertEquals(HomeAction.Swallow to HomeAction.Swallow, both(HomeKey.Back, home))
        assertEquals(HomeAction.Swallow to HomeAction.BackToHome, both(HomeKey.Back, home.copy(onApps = true)))
        assertEquals(HomeAction.Swallow to HomeAction.BackToDock, both(HomeKey.Back, home.copy(appleTv = true, scrolled = true)))
        assertEquals(HomeAction.Swallow to HomeAction.Swallow, both(HomeKey.Back, home.copy(appleTv = true)))
    }

    @Test
    fun otherKeysGoToWhatHasTheFocus() {
        for (key in listOf(HomeKey.Left, HomeKey.Right, HomeKey.Up, HomeKey.Down, HomeKey.Ok, HomeKey.Other)) {
            assertEquals(key.name, HomeAction.Pass to HomeAction.Pass, both(key, home.copy(menuOpen = true, pinOpen = true)))
        }
    }
}
