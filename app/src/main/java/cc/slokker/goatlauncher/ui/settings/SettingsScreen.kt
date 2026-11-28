package cc.slokker.goatlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.os.SystemClock
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.BuildConfig
import cc.slokker.goatlauncher.data.api.DELETE_ACCOUNT_URL
import cc.slokker.goatlauncher.data.api.PRIVACY_URL
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.ImageBrowser
import cc.slokker.goatlauncher.ui.common.LocalSlidingFocus
import cc.slokker.goatlauncher.ui.common.SlidingFocus
import cc.slokker.goatlauncher.ui.common.focusSoon
import cc.slokker.goatlauncher.ui.common.onBackKey
import cc.slokker.goatlauncher.ui.common.requestFocusSafely
import cc.slokker.goatlauncher.ui.common.slidingFocusHost
import cc.slokker.goatlauncher.ui.home.BuildThenFade
import cc.slokker.goatlauncher.ui.home.LocalFadeSlotActive
import cc.slokker.goatlauncher.ui.theme.GoatColors
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * As Definições, em página inteira (a barra de cima esconde-se): à esquerda as categorias (a página principal do
 * catálogo), à direita, num painel de vidro, a página da categoria com o foco. Andar pelas categorias mostra a
 * página delas ao fim de um instante; OK ou → entra no painel. Voltar no painel sobe um nível e, na página da
 * categoria, volta às categorias; nas categorias faz [onExit]. As páginas estão em Page; as linhas, em SettingsCatalog.
 */
@Composable
fun SettingsScreen(
    vm: LauncherViewModel,
    autoFocus: Boolean = true,
    /** Muda quando o código se fecha por cima das Definições: o foco volta para dentro delas (ver HomeState). */
    refocus: Int = 0,
    onExit: () -> Unit,
) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    val unlocks by vm.unlocks.collectAsStateWithLifecycle()
    val locked = remember(device, unlocks) { vm.settingsLocked() }
    val hasPin = device.pin != null

    // A página no painel da direita (uma categoria ou uma página dentro dela).
    var page by remember { mutableStateOf(Page.Appearance) }
    // Pasta aberta em "Imagem da box" (null = lista de discos).
    var dir by remember { mutableStateOf<File?>(null) }
    // Botão do comando a configurar (keyCode, scanCode), entre "carrega no botão" e escolher a app.
    var buttonKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val args = PageArgs(buttonKey)

    val leftFirst = remember { FocusRequester() }
    val leftGroup = remember { FocusRequester() }
    // A linha de cada categoria na coluna da esquerda (Voltar no painel regressa à dela).
    val navRequesters = remember { mutableMapOf<Page, FocusRequester>() }
    val navRequester: (Page) -> FocusRequester = { p -> navRequesters.getOrPut(p) { FocusRequester() } }
    // O primeiro elemento de cada página construída no painel (durante o fade há duas).
    val paneFirsts = remember { mutableMapOf<Page, FocusRequester>() }
    var paneFocused by remember { mutableStateOf(false) }
    // Sobe a cada pedido para pôr o foco no painel (abrir uma página com OK, ou dentro do painel).
    var paneFocusRequest by remember { mutableIntStateOf(0) }
    // Sobe para as listas do painel voltarem ao início (ver LocalPaneTop).
    var paneTop by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var followJob by remember { mutableStateOf<Job?>(null) }
    // A hora da última tecla (as categorias só se constroem antes, invisíveis, com o comando parado).
    val lastKeyAt = remember { longArrayOf(0L) }

    // Abrir uma página no painel e levar o foco para lá. As imagens da box abrem sempre na lista de discos.
    val open: (Page) -> Unit = {
        followJob?.cancel()
        if (it == Page.WallpaperFiles) dir = null
        page = it
        paneFocusRequest++
    }
    // O foco parou numa categoria: o painel mostra-a ao fim de um instante (de passagem para outra, não se constrói).
    val follow: (Page) -> Unit = { target ->
        followJob?.cancel()
        followJob = scope.launch {
            delay(PANE_FOLLOW_MS)
            // Só se o foco ficou nas categorias: se caiu lá por um instante (ex.: a linha com foco do painel
            // desapareceu) e o painel o voltou a pedir, a página não muda.
            if (!paneFocused && page.category(hasPin) != target) page = target
        }
    }

    // As Definições ficam construídas depois de abertas (ver TabCrossfade): cada vez que voltam a aparecer, o foco
    // vai para a categoria que estava aberta.
    val active = LocalFadeSlotActive.current
    LaunchedEffect(active) {
        if (!active) {
            followJob?.cancel()
            return@LaunchedEffect
        }
        // As categorias só se constroem antes depois de as Definições estarem abertas e paradas um bocado.
        lastKeyAt[0] = SystemClock.uptimeMillis()
        if (autoFocus) (navRequesters[page.category(hasPin)] ?: leftFirst).let { if (!it.focusSoon()) leftFirst.focusSoon() }
    }
    LaunchedEffect(paneFocusRequest) {
        if (paneFocusRequest == 0 || page == Page.WallpaperFiles) return@LaunchedEffect
        // A página nova constrói-se primeiro (ver BuildThenFade): espera que o primeiro elemento dela exista.
        repeat(FOCUS_WAIT_FRAMES) { frame ->
            withFrameNanos { }
            if (paneFirsts[page]?.enter() == true) return@LaunchedEffect
            // Ainda não deu: se a lista da página desceu (ex.: voltar de uma subpágina), a primeira linha não está
            // composta. A página volta ao início e tenta-se outra vez.
            if (frame == SCROLL_TOP_AFTER_FRAMES) paneTop++
        }
    }
    // Fechado o pedido do código (certo ou cancelado), o foco volta para dentro das Definições. Só quando [refocus]
    // muda com elas abertas (o valor com que abrem é de outra vez).
    var seenRefocus by remember { mutableIntStateOf(refocus) }
    LaunchedEffect(refocus) {
        if (refocus != seenRefocus) {
            seenRefocus = refocus
            leftFirst.focusSoon()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                lastKeyAt[0] = SystemClock.uptimeMillis()
                false
            }
            .onBackKey {
                when {
                    locked || !paneFocused -> onExit()
                    else -> {
                        val up = page.parent(hasPin)
                        if (up != null && up != Page.Main) {
                            open(up)
                        } else {
                            // Na página da categoria: volta à coluna das categorias, na linha dela.
                            if (navRequesters[page.category(hasPin)]?.requestFocusSafely() != true) leftGroup.requestFocusSafely()
                        }
                    }
                }
                true
            },
    ) {
        if (locked) {
            // Definições protegidas: só o pedido do código, ao centro.
            Box(Modifier.align(Alignment.Center).width(PaneMinWidth).background(GoatColors.ControlFill, PaneShape).padding(24.dp)) {
                ProtectedPage(vm, leftFirst)
            }
            return@Box
        }
        Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
            // ---- As categorias ----
            Column(Modifier.width(CategoriesWidth).fillMaxHeight()) {
                Text(
                    s.settings,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
                )
                val sliding = remember { SlidingFocus() }
                CompositionLocalProvider(LocalSlidingFocus provides sliding) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fadeEdges()
                            .slidingFocusHost(sliding)
                            .focusRequester(leftGroup)
                            // ← do painel volta à linha da categoria aberta (a procura pelo lado caía noutra).
                            .focusProperties {
                                onEnter = {
                                    if (requestedFocusDirection == FocusDirection.Left) navRequesters[page.category(hasPin)]?.requestFocus()
                                }
                            }
                            .focusGroup(),
                    ) {
                        SettingsList(vm, Page.Main, leftFirst, open, spacing = 4.dp, onFocusPage = follow, navRequester = navRequester)
                    }
                }
                Text(
                    s.version(BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = GoatColors.TextFaint,
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            // ---- O painel da categoria ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(GoatColors.ControlFill, PaneShape)
                    .onFocusChanged { paneFocused = it.hasFocus }
                    // → das categorias entra sempre na primeira linha da página (a procura pelo lado caía a meio).
                    // Se a lista tinha descido (numa visita anterior), a primeira linha já não está composta: a página
                    // volta ao início e o foco vai para ela quando reaparecer (senão caía na linha visível mais perto).
                    .focusProperties {
                        onEnter = {
                            if (requestedFocusDirection == FocusDirection.Right && paneFirsts[page]?.enter() != true) {
                                cancelFocusChange()
                                paneTop++
                                paneFocusRequest++
                            }
                        }
                    }
                    .focusGroup()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                BuildThenFade(
                    target = page,
                    keyOf = { it },
                    outMs = PANE_OUT_MS,
                    inMs = PANE_IN_MS,
                    inDelayMs = 0,
                    modifier = Modifier.fillMaxSize(),
                    slide = PaneSlide,
                    // As páginas por onde se passou ficam construídas: voltar a elas é só o fade.
                    keepAlive = PANE_KEEP,
                    // Com as Definições à vista e sem teclas há um bocado, as outras categorias constroem-se
                    // invisíveis: nem a primeira passagem por elas tem o frame comprido.
                    prebuild = PanePrebuild,
                    canPrebuild = { active && SystemClock.uptimeMillis() - lastKeyAt[0] > PANE_PREBUILD_IDLE_MS },
                    // Sem desenhar antes: na SHIELD isso parava a linha principal centenas de ms (os shaders das linhas já
                    // são os da coluna das categorias).
                    warmPrebuilt = false,
                ) { shown ->
                    val first = remember { FocusRequester() }
                    LaunchedEffect(Unit) { paneFirsts[shown] = first }
                    CompositionLocalProvider(LocalPaneTop provides paneTop) {
                        PaneContent(
                            vm,
                            shown,
                            category = shown.category(hasPin),
                            args = args,
                            first = first,
                            open = open,
                            dir = dir,
                            onDir = { dir = it },
                            onButtonKey = { buttonKey = it },
                            hasPin = hasPin,
                        )
                    }
                }
            }
        }
    }
}

/** Uma página no painel: de onde se veio ("‹ Categoria"), o título e as linhas (com a pré-visualização ao lado). */
@Composable
private fun PaneContent(
    vm: LauncherViewModel,
    page: Page,
    category: Page,
    args: PageArgs,
    first: FocusRequester,
    open: (Page) -> Unit,
    dir: File?,
    onDir: (File?) -> Unit,
    onButtonKey: (Pair<Int, Int>?) -> Unit,
    hasPin: Boolean,
) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        if (page != category) {
            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(GoatIcons.ChevronLeft, contentDescription = null, tint = GoatColors.TextFaint, modifier = Modifier.size(16.dp))
                Text((page.parent(hasPin) ?: category).title(s, args), style = MaterialTheme.typography.labelLarge, color = GoatColors.TextFaint)
            }
        }
        Text(page.title(s, args), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // As linhas desvanecem nas pontas em vez de ficarem cortadas a direito por baixo do título.
            // Um só destaque, que desliza de linha em linha (ver SlidingFocus).
            val sliding = remember { SlidingFocus() }
            CompositionLocalProvider(LocalSlidingFocus provides sliding) {
                Box(Modifier.weight(1f).fillMaxHeight().fadeEdges().slidingFocusHost(sliding)) {
                    PageBody(vm, page, first, open, dir, onDir, onButtonKey, args.buttonKey)
                }
            }
            // Ao lado das linhas, o que elas mudam: o ecrã inicial na Aparência. (O fundo já está por trás das
            // Definições; uma cópia dele aqui custava ~0,5 s na primeira visita à página Fundo, na SHIELD.)
            when (page) {
                Page.Appearance -> AppearancePreview(vm, Modifier.width(PreviewWidth).padding(top = 8.dp))
                else -> Unit
            }
        }
    }
}

/** As linhas de cada página (o catálogo, ou uma página à parte). */
@Composable
private fun PageBody(
    vm: LauncherViewModel,
    page: Page,
    first: FocusRequester,
    open: (Page) -> Unit,
    dir: File?,
    onDir: (File?) -> Unit,
    onButtonKey: (Pair<Int, Int>?) -> Unit,
    buttonKey: Pair<Int, Int>?,
) {
    val s = LocalStrings.current
    when (page) {
        Page.Main -> SettingsList(vm, page, first, open, spacing = 4.dp)
        Page.Appearance, Page.Wallpaper, Page.Apps, Page.Ambient, Page.Startup, Page.General, Page.Parental ->
            SettingsList(vm, page, first, open)
        Page.WallpaperFiles -> ImageBrowser(
            dir = dir,
            onDir = onDir,
            onPick = {
                vm.setWallpaperFromImage(it)
                open(Page.Wallpaper)
            },
            onPickFolder = {
                vm.setWallpaperFolder(it)
                open(Page.Wallpaper)
            },
            first = first,
        )
        Page.HiddenApps -> HiddenAppsPage(vm, first)
        Page.Account -> AccountPage(vm, first, onPage = open)
        Page.YouTube -> YouTubePage(vm, first, onDone = { open(Page.Account) })
        Page.Privacy -> SiteLinkPage(vm, PRIVACY_URL, first)
        Page.DeleteAccount -> SiteLinkPage(vm, DELETE_ACCOUNT_URL, first)
        Page.Autostart -> {
            val device by vm.device.collectAsStateWithLifecycle()
            AppPickerPage(vm, first, current = device.autostart, none = s.autostartNone) {
                vm.setAutostart(it)
                open(Page.Startup)
            }
        }
        Page.Language -> LanguagePage(vm, first)
        Page.Location -> LocationPage(vm, first)
        Page.Buttons -> ButtonsPage(
            vm,
            first,
            onEdit = {
                onButtonKey(it)
                open(Page.ButtonApp)
            },
            onAdd = { open(Page.ButtonCapture) },
        )
        Page.ButtonCapture -> ButtonCapturePage(
            first,
            onCaptured = {
                onButtonKey(it)
                open(Page.ButtonApp)
            },
            onCancel = { open(Page.Buttons) },
        )
        Page.ButtonApp -> {
            val device by vm.device.collectAsStateWithLifecycle()
            val (k, sc) = buttonKey ?: (0 to 0)
            ButtonTargetPage(
                vm,
                first,
                current = device.buttons.firstOrNull { it.keyCode == k && it.scanCode == sc },
                onAction = {
                    vm.setButtonAction(k, sc, it)
                    open(Page.Buttons)
                },
                onApp = {
                    vm.setButton(k, sc, it)
                    open(Page.Buttons)
                },
            )
        }
        Page.PinSetup -> PinSetupPage(vm, onDone = { open(Page.Parental) })
        Page.LockedApps -> LockedAppsPage(vm, first)
        Page.Report -> ReportPage(vm, first)
    }
}

/** A categoria (a página logo abaixo da principal) a que pertence esta página. */
internal fun Page.category(hasPin: Boolean): Page =
    generateSequence(this) { it.parent(hasPin) }.takeWhile { it != Page.Main }.lastOrNull() ?: this

private val PaneShape = RoundedCornerShape(26.dp)

/** A coluna das categorias. */
private val CategoriesWidth = 300.dp

/** A pré-visualização ao lado das linhas (Aparência, Fundo). */
private val PreviewWidth = 220.dp
private val PaneMinWidth = 520.dp
private val PaneSlide = 24.dp

/** Quanto o foco tem de ficar numa categoria para o painel a mostrar (de passagem, não se constrói). */
private const val PANE_FOLLOW_MS = 220L
private const val PANE_OUT_MS = 90
private const val PANE_IN_MS = 160
private const val FOCUS_WAIT_FRAMES = 30
/** Ao fim destes frames sem conseguir pôr o foco na primeira linha, a página volta ao início (ver LocalPaneTop). */
private const val SCROLL_TOP_AFTER_FRAMES = 4
private const val PANE_KEEP = 12

/** As categorias a construir antes, invisíveis (o Controlo parental pede o código: fica de fora). */
private val PanePrebuild = listOf(Page.Appearance, Page.Wallpaper, Page.Apps, Page.Ambient, Page.Startup, Page.Buttons, Page.General, Page.Account)
private const val PANE_PREBUILD_IDLE_MS = 1_200L

/** Pede o foco e diz se o conseguiu (false se o elemento não estiver composto, ex.: fora do ecrã numa lista). */
private fun FocusRequester.enter(): Boolean = try {
    requestFocus(FocusDirection.Enter)
} catch (e: IllegalStateException) {
    false
}
