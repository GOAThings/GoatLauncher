package cc.slokker.goatlauncher.ui.settings

import cc.slokker.goatlauncher.ButtonCapture
import java.io.File
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cc.slokker.goatlauncher.ui.common.slidingFocusHost
import cc.slokker.goatlauncher.ui.common.LocalSlidingFocus
import cc.slokker.goatlauncher.ui.common.SlidingFocus
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.BuildConfig
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.ImageBrowser
import cc.slokker.goatlauncher.ui.common.focusSoon
import cc.slokker.goatlauncher.ui.common.onBackKey
import cc.slokker.goatlauncher.ui.home.rememberWallpaperBlur
import cc.slokker.goatlauncher.ui.common.frostedGlass
import cc.slokker.goatlauncher.ui.theme.GoatColors
/**
 * Definições do launcher, no seu separador do ecrã inicial: um painel de vidro à direita, por cima do fundo. Voltar sobe
 * um nível; na página principal faz [onExit]. As páginas estão em Page; as linhas da maioria, em SettingsCatalog.
 */
@Composable
fun SettingsScreen(
    vm: LauncherViewModel,
    autoFocus: Boolean = true,
    onExit: () -> Unit,
) {
    val s = LocalStrings.current
    var page by remember { mutableStateOf(Page.Main) }
    // Pasta aberta em "Imagem da box" (null = lista de discos).
    var dir by remember { mutableStateOf<File?>(null) }
    // Botão do comando a configurar (keyCode, scanCode), entre "carrega no botão" e escolher a app.
    var buttonKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val args = PageArgs(buttonKey)
    // Ir para uma página; as imagens da box abrem sempre na lista de discos.
    val open: (Page) -> Unit = {
        if (it == Page.WallpaperFiles) dir = null
        page = it
    }
    val first = remember { FocusRequester() }

    // O navegador de imagens trata do seu foco (muda a cada pasta). A primeira página só leva o foco se
    // [autoFocus]: aberta por passar o foco no separador, o foco fica lá até se carregar em ↓.
    var firstPage by remember { mutableStateOf(true) }
    LaunchedEffect(page) {
        if (page != Page.WallpaperFiles && (autoFocus || !firstPage)) focusSoon(first)
        firstPage = false
    }

    // Controlo parental: com as definições protegidas, primeiro só aparece o pedido do código.
    val device by vm.device.collectAsStateWithLifecycle()
    val unlocks by vm.unlocks.collectAsStateWithLifecycle()
    val locked = remember(device, unlocks) { vm.settingsLocked() }
    // Fechado o pedido do código (certo ou cancelado), o foco volta para dentro das Definições.
    val pinOpen by vm.pinPrompt.collectAsStateWithLifecycle()
    var hadPin by remember { mutableStateOf(false) }
    LaunchedEffect(pinOpen) {
        if (pinOpen != null) hadPin = true else if (hadPin) {
            hadPin = false
            focusSoon(first)
        }
    }

    // O vidro do painel deixa ver o fundo desfocado por trás, como nas Definições da GoatTV.
    val account by vm.account.collectAsStateWithLifecycle()
    val wallpaperImage by vm.wallpaperImage.collectAsStateWithLifecycle()
    val blur = rememberWallpaperBlur(account.wallpaper, wallpaperImage)

    // Na Aparência, o ecrã divide-se: à esquerda a pré-visualização do ecrã inicial, à direita as definições.
    val split = page == Page.Appearance && !locked
    val panelWidth by animateDpAsState(if (split) SplitPanelWidth else PanelWidth, tween(300, easing = FastOutSlowInEasing), label = "panel")
    Box(
        Modifier
            .fillMaxSize()
            // Voltar sobe um nível (ex.: da Aparência para as Definições); na página principal volta ao Início.
            .onBackKey {
                val up = page.parent(hasPin = device.pin != null)
                if (up == null || locked) onExit() else page = up
                true
            },
    ) {
        AnimatedVisibility(
            visible = split,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(300, delayMillis = 80)) + slideInHorizontally(tween(320)) { -it / 8 },
            exit = fadeOut(tween(160)),
        ) {
            AppearancePreview(
                vm,
                Modifier
                    .padding(end = SplitPanelWidth + 28.dp)
                    .fillMaxWidth(),
            )
        }
        Column(
            Modifier
                // À direita, como nas Definições da GoatTV: o ecrã inicial fica à vista à esquerda.
                .align(Alignment.TopEnd)
                .padding(top = 16.dp)
                .width(panelWidth)
                .fillMaxHeight()
                .frostedGlass(blur, SettingsShape)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // Entre páginas, como nas Definições da GoatTV: a nova entra do lado para onde se vai, com um fade.
            AnimatedContent(
                targetState = page,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState.depth >= initialState.depth
                    (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
                },
                label = "settingsPage",
            ) { shown ->
                // A página que está a sair não pode receber o foco (senão ele salta ao terminar a animação). Só se
                // impede a entrada: canFocus aqui valeria para o grupo da lista, que passava a levar o foco ele próprio.
                val leaving = transition.targetState == EnterExitState.PostExit
                Column(
                    Modifier
                        .fillMaxSize()
                        .focusProperties { onEnter = { if (leaving) cancelFocusChange() } }
                        .focusGroup(),
                ) {
                // Fora da página principal, de onde se veio ("‹ Definições").
                if (shown != Page.Main && !locked) {
                    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(GoatIcons.ChevronLeft, contentDescription = null, tint = GoatColors.TextFaint, modifier = Modifier.size(16.dp))
                        Text((shown.parent(hasPin = device.pin != null) ?: Page.Main).title(s, args), style = MaterialTheme.typography.labelLarge, color = GoatColors.TextFaint)
                    }
                }
                Text(
                    shown.title(s, args),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                // As linhas desvanecem nas pontas em vez de ficarem cortadas a direito por baixo do título.
                // Um só destaque, que desliza de linha em linha (ver SlidingFocus).
                val sliding = remember { SlidingFocus() }
                CompositionLocalProvider(LocalSlidingFocus provides sliding) {
                Box(Modifier.weight(1f).fadeEdges().slidingFocusHost(sliding)) {
                if (locked) {
                    ProtectedPage(vm, first)
                } else when (shown) {
                    Page.Main -> SettingsList(vm, shown, first, open, spacing = 4.dp)
                    Page.Appearance, Page.Wallpaper, Page.Apps, Page.Ambient, Page.Startup, Page.General, Page.Parental ->
                        SettingsList(vm, shown, first, open)
                    Page.WallpaperFiles -> ImageBrowser(
                        dir = dir,
                        onDir = { dir = it },
                        onPick = {
                            vm.setWallpaperFromImage(it)
                            page = Page.Wallpaper
                        },
                        onPickFolder = {
                            vm.setWallpaperFolder(it)
                            page = Page.Wallpaper
                        },
                        first = first,
                    )
                    Page.HiddenApps -> HiddenAppsPage(vm, first)
                    Page.Account -> AccountPage(vm, first, onPage = open)
                    Page.YouTube -> YouTubePage(vm, first, onDone = { page = Page.Account })
                    Page.Autostart -> {
                        val device by vm.device.collectAsStateWithLifecycle()
                        AppPickerPage(vm, first, current = device.autostart, none = s.autostartNone) {
                            vm.setAutostart(it)
                            page = Page.Startup
                        }
                    }
                    Page.Language -> LanguagePage(vm, first)
                    Page.Location -> LocationPage(vm, first)
                    Page.Buttons -> ButtonsPage(
                        vm,
                        first,
                        onEdit = {
                            buttonKey = it
                            page = Page.ButtonApp
                        },
                        onAdd = { page = Page.ButtonCapture },
                    )
                    Page.ButtonCapture -> ButtonCapturePage(
                        first,
                        onCaptured = {
                            buttonKey = it
                            page = Page.ButtonApp
                        },
                        onCancel = { page = Page.Buttons },
                    )
                    Page.ButtonApp -> {
                        val device by vm.device.collectAsStateWithLifecycle()
                        val (k, sc) = buttonKey ?: (0 to 0)
                        AppPickerPage(vm, first, current = device.buttons.firstOrNull { it.keyCode == k && it.scanCode == sc }?.app, none = s.removeButton) {
                            vm.setButton(k, sc, it)
                            page = Page.Buttons
                        }
                    }
                    Page.PinSetup -> PinSetupPage(vm, onDone = { page = Page.Parental })
                    Page.LockedApps -> LockedAppsPage(vm, first)
                    Page.Report -> ReportPage(vm, first)
                }
                }
                }
                }
            }
            Text(
                "GoatLauncher ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = GoatColors.TextFaint,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp),
            )
        }
    }
}

private val SettingsShape = RoundedCornerShape(26.dp)

private val PanelWidth = 520.dp

/** Largura das definições quando o ecrã se divide com a pré-visualização. */
private val SplitPanelWidth = 400.dp
