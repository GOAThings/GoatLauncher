package cc.slokker.goatlauncher.ui.settings

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.runtime.rememberUpdatedState
import cc.slokker.goatlauncher.data.ButtonMap
import cc.slokker.goatlauncher.ButtonCapture
import cc.slokker.goatlauncher.ui.Strings
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.text.style.TextAlign
import cc.slokker.goatlauncher.ui.common.PinPad
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import cc.slokker.goatlauncher.ui.home.FocusColors
import cc.slokker.goatlauncher.ui.home.GraphiteBackground
import androidx.compose.ui.geometry.Offset
import cc.slokker.goatlauncher.ui.home.colorSpec
import cc.slokker.goatlauncher.ui.home.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cc.slokker.goatlauncher.ui.common.slidingFocusHost
import cc.slokker.goatlauncher.ui.common.LocalSlidingFocus
import cc.slokker.goatlauncher.ui.common.SlidingFocus
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.BuildConfig
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.after
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import cc.slokker.goatlauncher.data.Place
import cc.slokker.goatlauncher.data.ApodFeed
import cc.slokker.goatlauncher.data.WikimediaFeed
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.state.UpdateState
import cc.slokker.goatlauncher.ui.state.ReportState
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassButton
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.SectionLabel
import cc.slokker.goatlauncher.ui.common.Toggle
import cc.slokker.goatlauncher.ui.common.TvTextField
import cc.slokker.goatlauncher.ui.common.ImageBrowser
import cc.slokker.goatlauncher.ui.common.focusSoon
import cc.slokker.goatlauncher.ui.common.glass
import cc.slokker.goatlauncher.ui.common.onBackKey
import cc.slokker.goatlauncher.ui.common.requestFocusSafely
import cc.slokker.goatlauncher.ui.home.Wallpapers
import cc.slokker.goatlauncher.ui.home.clockText
import java.util.Calendar
import cc.slokker.goatlauncher.ui.home.rememberWallpaperBlur
import cc.slokker.goatlauncher.ui.common.frostedGlass
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
internal enum class Page {
    Main, Appearance, Wallpaper, WallpaperFiles, Apps, HiddenApps, Ambient, Startup, Autostart, Buttons, ButtonCapture, ButtonApp,
    Parental, PinSetup, LockedApps, General, Language, Location, Account, YouTube, Report,
}

/** Definições do launcher: um painel de vidro à direita, por cima do fundo. Voltar sobe um nível. */
@Composable
fun SettingsScreen(
    vm: LauncherViewModel,
    embedded: Boolean = false,
    autoFocus: Boolean = true,
    onExit: () -> Unit = vm::closeSettings,
) {
    val s = LocalStrings.current
    var page by remember { mutableStateOf(Page.Main) }
    // Pasta aberta em "Imagem da box" (null = lista de discos).
    var dir by remember { mutableStateOf<File?>(null) }
    // Botão do comando a configurar (keyCode, scanCode), entre "carrega no botão" e escolher a app.
    var buttonKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
    val photo by vm.onlinePhoto.collectAsStateWithLifecycle()
    val folderImage by vm.folderImage.collectAsStateWithLifecycle()
    val blur = rememberWallpaperBlur(account.wallpaper, photo?.url ?: folderImage)

    // Na Aparência, o ecrã divide-se: à esquerda a pré-visualização do ecrã inicial, à direita as definições.
    val split = embedded && page == Page.Appearance && !locked
    val panelWidth by animateDpAsState(if (split) SplitPanelWidth else PanelWidth, tween(300, easing = FastOutSlowInEasing), label = "panel")
    Box(
        Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.background(Color.Black.copy(alpha = 0.35f)).padding(TvSafePadding))
            // Voltar sobe um nível (ex.: da Aparência para as Definições); na página principal volta ao Início.
            .onBackKey {
                val up = parentPage(page, hasPin = device.pin != null)
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
                .align(if (embedded) Alignment.TopEnd else Alignment.CenterEnd)
                .then(if (embedded) Modifier.padding(top = 16.dp) else Modifier)
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
                    val forward = depth(targetState) >= depth(initialState)
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
                        Text(pageTitle(parentPage(shown, hasPin = device.pin != null) ?: Page.Main, s, buttonKey), style = MaterialTheme.typography.labelLarge, color = GoatColors.TextFaint)
                    }
                }
                Text(
                    pageTitle(shown, s, buttonKey),
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
                    Page.Main -> MainPage(vm, first, onPage = { page = it })
                    Page.Appearance -> AppearancePage(vm, first, onPage = { page = it })
                    Page.Wallpaper -> WallpaperPage(vm, first, onBoxImages = {
                        dir = null
                        page = Page.WallpaperFiles
                    })
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
                    Page.Apps -> AppsPage(vm, first, onPage = { page = it })
                    Page.HiddenApps -> HiddenAppsPage(vm, first)
                    Page.Ambient -> AmbientPage(vm, first)
                    Page.Startup -> StartupPage(vm, first, onPage = { page = it })
                    Page.General -> GeneralPage(vm, first, onPage = { page = it })
                    Page.Account -> AccountPage(vm, first, onPage = { page = it })
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
                    Page.Parental -> ParentalPage(vm, first, onPage = { page = it })
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

/** Quantos níveis abaixo da página principal está [page] (para a transição saber se vai ou volta). */
private fun depth(page: Page): Int = generateSequence(page) { parentPage(it, hasPin = true) }.count() - 1

/** O título de [page]. */
private fun pageTitle(page: Page, s: Strings, buttonKey: Pair<Int, Int>?): String = when (page) {
    Page.Main -> s.settings
    Page.Appearance -> s.appearance
    Page.Wallpaper -> s.wallpaper
    Page.WallpaperFiles -> s.boxImages
    Page.Apps -> s.apps
    Page.HiddenApps -> s.hiddenApps
    Page.Ambient -> s.idleMode
    Page.Startup -> s.startup
    Page.General -> s.general
    Page.Account -> s.account
    Page.YouTube -> s.youtube
    Page.Autostart -> s.autostart
    Page.Buttons -> s.buttons
    Page.Language -> s.language
    Page.Location -> s.location
    Page.ButtonCapture -> s.addButton
    Page.ButtonApp -> buttonKey?.let { (k, sc) -> s.buttonOpens(buttonName(k, sc, s)) } ?: s.buttons
    Page.Parental, Page.PinSetup -> s.parental
    Page.LockedApps -> s.lockedApps
    Page.Report -> s.sendReport
}

private val SettingsShape = RoundedCornerShape(26.dp)

private val PanelWidth = 520.dp

/** A página de onde se chega a [page] (para onde Voltar leva); null na página principal. */
private fun parentPage(page: Page, hasPin: Boolean): Page? = when (page) {
    Page.Main -> null
    Page.Appearance, Page.Wallpaper, Page.Apps, Page.Ambient, Page.Startup, Page.Buttons, Page.Parental, Page.General, Page.Account,
    Page.Report -> Page.Main
    Page.HiddenApps -> Page.Apps
    Page.Autostart -> Page.Startup
    Page.Language, Page.Location -> Page.General
    Page.YouTube -> Page.Account
    Page.WallpaperFiles -> Page.Wallpaper
    Page.ButtonCapture, Page.ButtonApp -> Page.Buttons
    Page.LockedApps -> Page.Parental
    // O primeiro código liga o controlo parental a partir da página principal; mudá-lo, a partir do Controlo parental.
    Page.PinSetup -> if (hasPin) Page.Parental else Page.Main
}

@Composable
private fun MainPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val account by vm.account.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val apps by vm.allApps.collectAsStateWithLifecycle()

    // Cada secção numa página sua: aqui só a lista delas, cada uma com o que está escolhido.
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Group {
                GlassRow(
                    title = s.appearance,
                    subtitle = layoutName(account, s),
                    leading = { IconTile(GoatIcons.Palette, accent = true) },
                    onClick = { onPage(Page.Appearance) },
                    trailing = { Chevron() },
                    modifier = Modifier.focusRequester(first),
                )
            }
        }
        item { SectionLabel(s.homeScreen) }
        item {
            Group {
                GlassRow(
                    title = s.wallpaper,
                    subtitle = wallpaperName(account.wallpaper, s),
                    leading = { IconTile(GoatIcons.Image) },
                    onClick = { onPage(Page.Wallpaper) },
                    trailing = { Chevron() },
                )
                GlassRow(
                    title = s.apps,
                    subtitle = s.hiddenCount(device.hidden.size),
                    leading = { IconTile(GoatIcons.Apps) },
                    onClick = { onPage(Page.Apps) },
                    trailing = { Chevron() },
                )
                GlassRow(
                    title = s.idleMode,
                    subtitle = if (account.idleMinutes > 0) s.idleAfter(account.idleMinutes) else s.idleOff,
                    leading = { IconTile(GoatIcons.Moon) },
                    onClick = { onPage(Page.Ambient) },
                    trailing = { Chevron() },
                )
            }
        }
        item { SectionLabel("TV") }
        item {
            Group {
                GlassRow(
                    title = s.startup,
                    subtitle = device.autostart?.let { pkg -> apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg } ?: s.autostartNone,
                    leading = { IconTile(GoatIcons.Power) },
                    onClick = { onPage(Page.Startup) },
                    trailing = { Chevron() },
                )
                val n = device.buttons.size
                GlassRow(
                    title = s.buttons,
                    // Sem o serviço de acessibilidade os botões não chegam ao launcher: diz-se logo.
                    subtitle = when {
                        n == 0 -> s.buttonsNone
                        !HomeGuardService.isEnabled(context) -> "${s.buttonsCount(n)} · ${s.needsGuard}"
                        else -> s.buttonsCount(n)
                    },
                    leading = { IconTile(GoatIcons.Remote) },
                    onClick = { onPage(Page.Buttons) },
                    trailing = { Chevron() },
                )
                GlassRow(
                    title = s.parental,
                    subtitle = if (device.pin == null) s.parentalOff else s.parentalOn(device.locked.size),
                    leading = { IconTile(GoatIcons.Lock) },
                    onClick = { if (device.pin == null) onPage(Page.PinSetup) else vm.withParentalUnlocked { onPage(Page.Parental) } },
                    trailing = { Chevron() },
                )
            }
        }
        item { SectionLabel(s.general) }
        item {
            Group {
                val code = Strings.normalize(account.language)
                GlassRow(
                    title = s.general,
                    subtitle = Strings.LANGUAGES.firstOrNull { it.first == code }?.second ?: s.languageAuto,
                    leading = { IconTile(GoatIcons.Tune) },
                    onClick = { onPage(Page.General) },
                    trailing = { Chevron() },
                )
                GlassRow(
                    title = s.account,
                    subtitle = session?.let { s.linkedAs(it.email) } ?: s.linkAccountHint,
                    leading = { IconTile(GoatIcons.Person) },
                    onClick = { onPage(Page.Account) },
                    trailing = { Chevron() },
                )
            }
        }
        item { SectionLabel(s.updates) }
        item {
            Group {
                val update by vm.update.collectAsStateWithLifecycle()
                val (title, subtitle) = when (val u = update) {
                    is UpdateState.Available -> s.updateReady(u.release.version) to u.release.notes.takeIf { it.isNotBlank() }
                    is UpdateState.Downloading -> s.downloading((u.progress * 100).toInt()) to null
                    is UpdateState.Installing -> s.installing to null
                    UpdateState.Checking -> s.checking to null
                    UpdateState.UpToDate -> s.checkUpdates to s.upToDate(vm.appVersion)
                    is UpdateState.Failed -> s.checkUpdates to s.updateFailed(u.message)
                    UpdateState.Idle -> s.checkUpdates to s.version(vm.appVersion)
                }
                GlassRow(
                    title = title,
                    subtitle = subtitle,
                    leading = { IconTile(GoatIcons.Download) },
                    onClick = { if (update is UpdateState.Available) vm.installUpdate() else vm.checkForUpdate(manual = true) },
                )
                val report by vm.report.collectAsStateWithLifecycle()
                GlassRow(
                    title = s.sendReport,
                    subtitle = reportStatus(report, s),
                    leading = { IconTile(GoatIcons.ArrowUp) },
                    onClick = { onPage(Page.Report) },
                    trailing = { Chevron() },
                )
                GlassRow(
                    title = s.androidSettings,
                    leading = { IconTile(GoatIcons.Settings) },
                    onClick = { if (!SystemIntents.start(context, SystemIntents.androidSettings())) vm.toast(s.cantOpen) },
                    trailing = { Chevron() },
                )
            }
        }
    }
}

/** Largura das definições quando o ecrã se divide com a pré-visualização. */
private val SplitPanelWidth = 400.dp
