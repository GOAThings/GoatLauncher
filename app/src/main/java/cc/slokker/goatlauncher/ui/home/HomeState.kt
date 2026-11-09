package cc.slokker.goatlauncher.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.slokker.goatlauncher.data.LauncherApp
import kotlin.math.abs

/** O que pode estar por cima do Início, com o foco, enquanto está aberto. */
sealed interface Overlay {
    /** O menu de uma app (pressão longa), desenhado por cima do ecrã. */
    data class Menu(val app: LauncherApp) : Overlay

    /** Mudar o nome (uma janela à parte). */
    data class Rename(val app: LauncherApp) : Overlay

    /** Mudar o ícone (uma janela à parte). */
    data class Icon(val app: LauncherApp) : Overlay

    /** O pedido do código do controlo parental (aberto pelo ViewModel). */
    data object Pin : Overlay
}

/**
 * O estado do ecrã inicial que não é do ViewModel: a app com foco, para onde o foco tem de ir, o "mover" e o que está
 * por cima (menu, mudar o nome, mudar o ícone, o código). Uma só regra para o foco: quem abre algo por cima não mexe no
 * foco; ao fechar, o foco volta para onde estava (ver [close]). Enquanto o código está aberto, os pedidos de foco do
 * Início esperam ([holdsFocus]).
 * [pinShown] diz se o código está à vista agora (o ViewModel decide; lê-se já, sem esperar pela composição).
 */
@Stable
class HomeState(initialFocus: String?, private val pinShown: () -> Boolean = { false }) {
    /** A app que tem (ou teve por último) o foco. */
    var focusedPkg by mutableStateOf(initialFocus)

    /** A app para onde o foco vai no próximo pedido ([focusRequest] muda a cada [focusApp]). */
    var targetPkg by mutableStateOf(initialFocus)
    var focusRequest by mutableIntStateOf(0)
        private set

    /** A app a mudar de lugar (null fora do "mover"). */
    var moving by mutableStateOf<String?>(null)
        private set

    /** Quando acabou o último "mover" (uptime em ms). */
    private var moveEndedAt by mutableLongStateOf(0L)

    /** As Definições voltam a pôr o foco na primeira linha quando isto muda (o código fechou-se por cima delas). */
    var settingsRefocus by mutableIntStateOf(0)
        private set

    private val overlays = mutableStateListOf<Overlay>()

    /** O que está por cima agora (o último aberto). */
    val overlay: Overlay? get() = overlays.lastOrNull()

    val menu: LauncherApp? get() = overlays.lastOrNull { it is Overlay.Menu }?.let { (it as Overlay.Menu).app }
    val renaming: LauncherApp? get() = overlays.lastOrNull { it is Overlay.Rename }?.let { (it as Overlay.Rename).app }
    val choosingIcon: LauncherApp? get() = overlays.lastOrNull { it is Overlay.Icon }?.let { (it as Overlay.Icon).app }

    /** Uma janela à parte (mudar o nome ou o ícone) está aberta: as teclas dela não passam pelo Início. */
    val dialogOpen: Boolean get() = overlays.any { it is Overlay.Rename || it is Overlay.Icon }

    /** O código tem o foco: um pedido de foco do Início espera que feche (e é ele que o devolve). */
    val holdsFocus: Boolean get() = pinShown()

    /** Põe o foco na app [pkg] (no banner dela), assim que puder. */
    fun focusApp(pkg: String?) {
        targetPkg = pkg
        focusRequest++
    }

    /** Abre [overlay] por cima (o foco passa para ele; quem o abre não mexe no foco). */
    fun open(overlay: Overlay) {
        if (overlay !in overlays) overlays += overlay
    }

    /**
     * Fecha [overlay] e devolve o foco para onde estava: o menu ao banner da app; o código ao que estava por baixo dele
     * (as Definições, se estiverem abertas, ou a última app com foco). Mudar o nome e o ícone são janelas à parte: ao
     * fecharem, a janela do Início volta a ter o foco onde o tinha (no banner), por isso não se pede nada. Se outra
     * coisa ficou por cima (ex.: o código certo abriu o menu), é essa que tem o foco, e nada muda.
     */
    fun close(overlay: Overlay, onSettings: Boolean = false, lastFavorite: () -> String? = { null }) {
        if (!overlays.remove(overlay)) return
        if (overlays.isNotEmpty()) return
        when (overlay) {
            is Overlay.Menu -> focusApp(overlay.app.packageName)
            is Overlay.Rename, is Overlay.Icon -> Unit
            Overlay.Pin -> if (onSettings) settingsRefocus++ else focusApp(focusedPkg ?: lastFavorite())
        }
    }

    /** Fecha o menu (Voltar, ou uma das suas ações): o foco volta ao banner da app. */
    fun closeMenu() {
        overlays.lastOrNull { it is Overlay.Menu }?.let { close(it) }
    }

    /** O botão Home: fecha o menu e o ícone sem devolver o foco (o Início põe-no no primeiro favorito). */
    fun dismissForHome() {
        overlays.removeAll { it is Overlay.Menu || it is Overlay.Icon }
    }

    fun startMove(pkg: String) {
        moving = pkg
    }

    /** Acaba o "mover" (se estava a mover). Devolve true se acabou agora (para o ViewModel guardar a ordem). */
    fun endMove(now: Long): Boolean {
        if (moving == null) return false
        moving = null
        moveEndedAt = now
        return true
    }

    /** O OK que confirma a posição ao acabar de mover não pode também abrir a app ([MOVE_CLICK_GUARD_MS]). */
    fun clickOpensApp(now: Long): Boolean = now - moveEndedAt > MOVE_CLICK_GUARD_MS

    companion object {
        /** Depois de acabar de mover, um clique na app durante este tempo não a abre (é o OK que confirmou a posição). */
        const val MOVE_CLICK_GUARD_MS = 600L

        /** A app que fica com o foco quando [pkg] sai da fila dos [favorites]: a seguinte, ou a anterior. */
        fun neighbour(favorites: List<String>, pkg: String): String? {
            val i = favorites.indexOf(pkg)
            return favorites.getOrNull(i + 1) ?: favorites.getOrNull(i - 1)
        }
    }
}

// ---- As teclas do ecrã inicial ----

/** As teclas que o Início trata (as outras seguem para quem tem o foco). */
enum class HomeKey {
    Left, Right, Up, Down,

    /** OK (também Enter e o A/Select do comando de jogos). */
    Ok,
    Back,
    Other,
}

/** O que fazer com uma tecla no Início. [consumed]: a tecla fica aqui (não segue para quem tem o foco). */
sealed class HomeAction(val consumed: Boolean) {
    /** Segue para quem tem o foco. */
    data object Pass : HomeAction(false)

    /** Fica aqui, sem fazer nada (ex.: a outra metade de uma tecla tratada). */
    data object Swallow : HomeAction(true)

    /** A mover: a app anda [delta] lugares (se [allowed]) e o foco vai com ela. */
    data class Move(val delta: Int, val allowed: Boolean) : HomeAction(true)
    data object EndMove : HomeAction(true)
    data object CancelPin : HomeAction(true)
    data object CloseMenu : HomeAction(true)

    /** Voltar nas Apps: volta ao Início, no último favorito. */
    data object BackToHome : HomeAction(true)

    /** Voltar na grelha de baixo da Apple TV: sobe para a doca. */
    data object BackToDock : HomeAction(true)
}

/** O que o Início está a mostrar, para decidir as teclas. */
data class HomeKeyContext(
    val moving: Boolean,
    val pinOpen: Boolean,
    val menuOpen: Boolean,
    val onApps: Boolean,
    val onSettings: Boolean,
    val appleTv: Boolean,
    /** Apple TV: o ecrã desceu para a grelha de baixo. */
    val scrolled: Boolean,
    /** A mover: a posição da app na lista das apps, e quantas há. */
    val movingIndex: Int = -1,
    val appCount: Int = 0,
)

/**
 * A tabela das teclas do ecrã inicial (antes de chegarem a quem tem o foco). [down]/[up]: a metade da tecla. As ações
 * correm numa das metades e a outra é engolida, para nada a seguir receber metade de uma tecla.
 */
fun homeKey(key: HomeKey, down: Boolean, up: Boolean, c: HomeKeyContext): HomeAction = when {
    // A mover: no Início só ‹ › (a app fica nos favoritos); nas Apps também ↑ ↓ (uma linha inteira).
    c.moving -> when (key) {
        HomeKey.Left, HomeKey.Right, HomeKey.Up, HomeKey.Down -> if (!down) HomeAction.Swallow else {
            val delta = when (key) {
                HomeKey.Left -> -1
                HomeKey.Right -> 1
                HomeKey.Up -> -APPS_COLUMNS
                else -> APPS_COLUMNS
            }
            // Na Apple TV a doca e a grelha de baixo são uma só lista: anda-se por todas.
            val allowed = c.onApps || c.appleTv || (abs(delta) == 1 && c.movingIndex + delta in 0 until minOf(FavoriteCount, c.appCount))
            HomeAction.Move(delta, allowed)
        }
        HomeKey.Ok, HomeKey.Back -> if (up) HomeAction.EndMove else HomeAction.Swallow
        HomeKey.Other -> HomeAction.Pass
    }
    c.pinOpen && key == HomeKey.Back -> if (up) HomeAction.CancelPin else HomeAction.Swallow
    c.menuOpen && key == HomeKey.Back -> if (up) HomeAction.CloseMenu else HomeAction.Swallow
    // As Definições tratam o seu Voltar (páginas); se o foco estiver nos separadores, o ecrã trata-o depois.
    key == HomeKey.Back && c.onSettings -> HomeAction.Pass
    // É o ecrã inicial: Voltar não sai daqui. Nas Apps, volta ao Início; na grelha da Apple TV, à doca.
    key == HomeKey.Back -> when {
        up && c.onApps -> HomeAction.BackToHome
        up && c.appleTv && c.scrolled -> HomeAction.BackToDock
        else -> HomeAction.Swallow
    }
    else -> HomeAction.Pass
}

/** Colunas da grelha do separador Apps (a primeira linha são os favoritos, alinhada com o Início). */
const val APPS_COLUMNS = 5
