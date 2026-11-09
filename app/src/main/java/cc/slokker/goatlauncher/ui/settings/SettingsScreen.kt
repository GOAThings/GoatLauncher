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
import cc.slokker.goatlauncher.ui.home.focusAnimations
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

private enum class Page {
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

/** Desvanece [EdgeFade] em cima e em baixo do conteúdo (as listas que passam por baixo do título e do rodapé). */
private fun Modifier.fadeEdges(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val h = EdgeFade.toPx()
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = h), size = Size(size.width, h), blendMode = BlendMode.DstIn)
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - h, endY = size.height),
            topLeft = Offset(0f, size.height - h),
            blendMode = BlendMode.DstIn,
        )
    }

private val EdgeFade = 18.dp

/** Um grupo de linhas num cartão, como nas Definições da GoatTV. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), GroupShape).padding(4.dp),
        content = content,
    )
}

/** O ícone de uma secção, num quadrado arredondado; [accent] no gradiente da marca (a Aparência). */
@Composable
private fun IconTile(icon: ImageVector, accent: Boolean = false) {
    Box(
        Modifier.size(32.dp).background(if (accent) GoatColors.Accent else SolidColor(Color.White.copy(alpha = 0.12f)), TileShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

private val SettingsShape = RoundedCornerShape(26.dp)
private val GroupShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(9.dp)
private val PanelWidth = 520.dp

/** O estado do último relatório, ou o que o botão faz. */
private fun reportStatus(state: ReportState, s: Strings): String = when (state) {
    ReportState.Idle -> s.sendReportSub
    ReportState.Sending -> s.reportSending
    is ReportState.Sent -> s.reportSent(state.id)
    is ReportState.Failed -> state.message
}

/** Enviar um relatório: o que aconteceu (opcional) e o registo do launcher, para a zona admin do site. */
@Composable
private fun ReportPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val report by vm.report.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }
    fun send() {
        if (report != ReportState.Sending) vm.sendReport(message)
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            Text(s.sendReportSub, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        }
        item {
            TvTextField(message, { message = it.take(2000) }, s.reportWhat, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).focusRequester(first), onDone = ::send)
        }
        item {
            GlassRow(title = s.reportSend, subtitle = reportStatus(report, s).takeIf { report != ReportState.Idle }, onClick = ::send)
        }
    }
}

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

/** Aparência: a disposição do ecrã inicial (Android TV ou Apple TV), o estilo do "Continuar a ver" e o resto. */
@Composable
private fun AppearancePage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    val android = account.layout == AccountSettings.LAYOUT_ANDROID

    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item { SectionLabel(s.layout) }
        item {
            GlassRow(
                title = LAYOUT_ANDROID_NAME,
                subtitle = s.layoutAndroidHint,
                onClick = { vm.setLayout(AccountSettings.LAYOUT_ANDROID) },
                trailing = { if (android) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            GlassRow(
                title = LAYOUT_APPLE_NAME,
                subtitle = s.layoutAppleHint,
                onClick = { vm.setLayout(AccountSettings.LAYOUT_APPLE_TV) },
                trailing = { if (!android) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
        // O "Continuar a ver" logo a seguir à disposição: na Android escolhe-se o estilo (ou desliga-se); a Apple TV
        // tem sempre os cartões grandes, por isso só se liga ou desliga.
        if (android) {
            item { SectionLabel(s.continueStyle) }
            items(AccountSettings.STYLES.map { it to styleName(it, s) }, key = { it.first }) { (style, name) ->
                GlassRow(
                    title = name,
                    onClick = { vm.setContinueStyle(style) },
                    trailing = {
                        if (account.showContinueWatching && account.continueStyle == style) {
                            Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                )
            }
            item(key = "continue_off") {
                GlassRow(
                    title = s.idleOff,
                    onClick = { vm.setShowContinueWatching(false) },
                    trailing = {
                        if (!account.showContinueWatching) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                )
            }
        } else {
            item {
                GlassRow(
                    title = s.continueWatching,
                    subtitle = s.continueWatchingHint,
                    onClick = { vm.setShowContinueWatching(!account.showContinueWatching) },
                    trailing = { Toggle(account.showContinueWatching) },
                )
            }
        }

        item { SectionLabel(s.focusedCard) }
        item {
            // OK passa ao efeito seguinte (pela ordem de FOCUS_EFFECTS, e volta ao primeiro); a pré-visualização ao lado mostra-o.
            val effects = AccountSettings.FOCUS_EFFECTS
            val next = effects[(effects.indexOf(account.focusEffect) + 1) % effects.size]
            val label = when (account.focusEffect) {
                AccountSettings.FOCUS_GLOW -> s.effectGlow
                AccountSettings.FOCUS_GLASS -> s.effectGlass
                AccountSettings.FOCUS_REFLECTION -> s.effectReflection
                AccountSettings.FOCUS_AURORA -> s.effectAurora
                AccountSettings.FOCUS_VIEWFINDER -> s.effectViewfinder
                AccountSettings.FOCUS_UNDERLINE -> s.effectUnderline
                AccountSettings.FOCUS_RING -> s.effectRing
                AccountSettings.FOCUS_DIM -> s.effectDim
                AccountSettings.FOCUS_SHADOW -> s.effectShadow
                else -> s.effectBorder
            }
            GlassRow(title = s.focusEffect, subtitle = label, onClick = { vm.setFocusEffect(next) }, trailing = { Chevron() })
        }
        // Cada efeito tem as suas animações (o vidro tem o brilho a passar e mais nenhuma); a cor não serve
        // para "só sombra" nem para "escurecer os outros".
        val effect = account.focusEffect
        val animations = focusAnimations(effect)
        if (animations.isNotEmpty()) {
            item {
                // OK passa à animação seguinte das que este efeito aceita (e volta à primeira).
                val current = account.focusAnimation.takeIf { it in animations } ?: AccountSettings.ANIM_NONE
                val next = animations[(animations.indexOf(current) + 1) % animations.size]
                val label = when (current) {
                    AccountSettings.ANIM_PULSE -> s.animPulse
                    AccountSettings.ANIM_BREATH -> s.animBreath
                    AccountSettings.ANIM_SHINE -> if (effect == AccountSettings.FOCUS_AURORA) s.animSpin else s.animShine
                    else -> s.animNone
                }
                GlassRow(title = s.focusAnimation, subtitle = label, onClick = { vm.setFocusAnimation(next) }, trailing = { Chevron() })
            }
        }
        if (effect != AccountSettings.FOCUS_SHADOW && effect != AccountSettings.FOCUS_DIM) {
            item { FocusColorRow(account.focusColor, onPick = vm::setFocusColor) }
        }

        item { SectionLabel(s.homeScreen) }
        item {
            GlassRow(title = s.clock24h, onClick = { vm.setClock24h(!account.clock24h) }, trailing = { Toggle(account.clock24h) })
        }
        item {
            GlassRow(title = s.greeting, onClick = { vm.setShowGreeting(!account.showGreeting) }, trailing = { Toggle(account.showGreeting) })
        }
        item {
            GlassRow(title = s.weather, onClick = { vm.setShowWeather(!account.showWeather) }, trailing = { Toggle(account.showWeather) })
        }
        item {
            GlassRow(title = s.showAppName, onClick = { vm.setShowAppName(!account.showAppName) }, trailing = { Toggle(account.showAppName) })
        }
        item { IconBackgroundRow(account.iconBackground, onPick = vm::setIconBackground) }
    }
}

/** Apps: as escondidas e "Manter como ecrã inicial". */
@Composable
private fun AppsPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            GlassRow(
                title = s.hiddenApps,
                subtitle = s.hiddenCount(device.hidden.size),
                onClick = { onPage(Page.HiddenApps) },
                trailing = { Chevron() },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            // Liga-se nas definições de Acessibilidade do Android (uma app não o pode ligar sozinha).
            val context = LocalContext.current
            var guard by remember { mutableStateOf(HomeGuardService.isEnabled(context)) }
            LifecycleResumeEffect(Unit) {
                guard = HomeGuardService.isEnabled(context)
                onPauseOrDispose { }
            }
            val stuck by vm.guardStuck.collectAsStateWithLifecycle()
            GlassRow(
                title = s.homeGuard,
                subtitle = when {
                    guard && stuck -> s.guardStuck
                    guard -> s.homeGuardOn
                    else -> s.homeGuardOff
                },
                onClick = { if (guard && stuck) vm.repairGuard() else SystemIntents.start(context, SystemIntents.accessibilitySettings()) },
                trailing = { Toggle(guard && !stuck) },
            )
        }

    }
}

/** Ao ligar: a app que abre quando a TV liga (e acorda) e quanto tempo depois. */
@Composable
private fun StartupPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            val apps by vm.allApps.collectAsStateWithLifecycle()
            GlassRow(
                modifier = Modifier.focusRequester(first),
                title = s.autostart,
                subtitle = device.autostart?.let { pkg -> apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg } ?: s.autostartNone,
                onClick = { onPage(Page.Autostart) },
                trailing = { Chevron() },
            )
        }
        if (device.autostart != null) {
            item {
                GlassRow(
                    title = s.autostartWake,
                    subtitle = s.autostartWakeHint,
                    onClick = { vm.setAutostartWake(!device.autostartWake) },
                    trailing = { Toggle(device.autostartWake) },
                )
            }
            item {
                val next = AUTOSTART_DELAYS.getOrElse(AUTOSTART_DELAYS.indexOf(device.autostartDelay) + 1) { AUTOSTART_DELAYS.first() }
                GlassRow(
                    title = s.autostartDelay,
                    subtitle = s.seconds(device.autostartDelay),
                    onClick = { vm.setAutostartDelay(next) },
                    trailing = { Chevron() },
                )
            }
        }

    }
}

/** Modo ambiente: quando entra, o que mostra, o protetor de ecrã e não deixar a TV adormecer. */
@Composable
private fun AmbientPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            // OK passa ao tempo seguinte: desligado → 1 → 2 → 5 → 10 → 30 min → desligado.
            val next = IDLE_MINUTES.getOrElse(IDLE_MINUTES.indexOf(account.idleMinutes) + 1) { IDLE_MINUTES.first() }
            GlassRow(
                modifier = Modifier.focusRequester(first),
                title = s.idleMode,
                subtitle = if (account.idleMinutes > 0) s.idleAfter(account.idleMinutes) else s.idleOff,
                onClick = { vm.setIdleMinutes(next) },
                trailing = { Chevron() },
            )
        }
        item {
            // OK troca entre as fotos (o fundo com as horas) e o relógio a ressaltar; vale também para o protetor de ecrã.
            val bounce = account.ambientStyle == AccountSettings.AMBIENT_BOUNCE
            GlassRow(
                title = s.ambientStyle,
                subtitle = if (bounce) s.ambientBounce else s.ambientPhotos,
                onClick = { vm.setAmbientStyle(if (bounce) AccountSettings.AMBIENT_PHOTOS else AccountSettings.AMBIENT_BOUNCE) },
                trailing = { Chevron() },
            )
        }
        item {
            val context = LocalContext.current
            GlassRow(
                title = s.screensaver,
                subtitle = s.screensaverHint,
                onClick = { SystemIntents.openScreensaverSettings(context) },
                trailing = { Chevron() },
            )
        }
        if (account.idleMinutes > 0) {
            item {
                GlassRow(
                    title = s.idleKeepAwake,
                    subtitle = s.idleKeepAwakeHint,
                    onClick = { vm.setIdleKeepAwake(!account.idleKeepAwake) },
                    trailing = { Toggle(account.idleKeepAwake) },
                )
            }
        }
    }
}

/** Geral: a língua, onde fica a TV (o tempo e o dia/noite), a noite automática e os sons. */
@Composable
private fun GeneralPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val sun by vm.sunTimes.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            val code = Strings.normalize(account.language)
            GlassRow(
                title = s.language,
                subtitle = Strings.LANGUAGES.firstOrNull { it.first == code }?.second ?: s.languageAuto,
                onClick = { onPage(Page.Language) },
                trailing = { Chevron() },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            val device by vm.device.collectAsStateWithLifecycle()
            GlassRow(
                title = s.location,
                subtitle = device.location?.name ?: vm.ipCity()?.let(s::locationAutoCity) ?: s.locationAuto,
                onClick = { onPage(Page.Location) },
                trailing = { Chevron() },
            )
        }
        item {
            GlassRow(
                title = s.nightAuto,
                // Com a localização, do pôr ao nascer do sol de hoje; sem ela, a hora fixa.
                subtitle = sun?.let { (rise, set) -> s.nightAutoSun(sunClock(set, s, account.clock24h), sunClock(rise, s, account.clock24h)) }
                    ?: s.nightAutoHint,
                onClick = { vm.setNightAuto(!account.nightAuto) },
                trailing = { Toggle(account.nightAuto) },
            )
        }
        item {
            GlassRow(
                title = s.navSounds,
                subtitle = s.navSoundsHint,
                onClick = { vm.setNavSounds(!account.navSounds) },
                trailing = { Toggle(account.navSounds) },
            )
        }
    }
}

/** A conta GOAT e o YouTube. */
@Composable
private fun AccountPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var confirmUnlink by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item { SectionLabel(s.account) }
        val current = session
        if (current == null) {
            item {
                GlassRow(
                    title = s.linkAccount,
                    subtitle = s.linkAccountHint,
                    onClick = vm::openActivation,
                    trailing = { Chevron() },
                    modifier = Modifier.focusRequester(first),
                )
            }
        } else {
            item {
                GlassRow(title = s.linkedAs(current.email), subtitle = current.deviceName, onClick = vm::syncNow, modifier = Modifier.focusRequester(first))
            }
            item { GlassRow(title = s.syncNow, onClick = vm::syncNow) }
            item {
                GlassRow(
                    title = s.unlink,
                    subtitle = if (confirmUnlink) s.pressAgainToConfirm else s.unlinkHint,
                    onClick = {
                        if (confirmUnlink) {
                            confirmUnlink = false
                            vm.unlink()
                        } else {
                            confirmUnlink = true
                        }
                    },
                )
            }
        }

        item { SectionLabel(s.youtube) }
        item {
            val linked by vm.youtubeLinked.collectAsStateWithLifecycle()
            var confirm by remember { mutableStateOf(false) }
            if (linked) {
                // OK passa à seguinte: os dois → só a meio → só recomendações → os dois.
                val (label, next) = when (account.youtubeFeed) {
                    AccountSettings.YT_CONTINUE -> s.ytContinue to AccountSettings.YT_RECOMMENDED
                    AccountSettings.YT_RECOMMENDED -> s.ytRecommended to AccountSettings.YT_BOTH
                    else -> s.ytBoth to AccountSettings.YT_CONTINUE
                }
                GlassRow(title = s.youtubeFeed, subtitle = label, onClick = { vm.setYoutubeFeed(next) }, trailing = { Chevron() })
                GlassRow(
                    title = s.youtubeUnlink,
                    subtitle = if (confirm) s.pressAgainToConfirm else s.youtubeOnHint,
                    onClick = {
                        if (confirm) vm.unlinkYouTube()
                        confirm = !confirm
                    },
                )
            } else {
                GlassRow(title = s.youtubeLink, subtitle = s.youtubeLinkHint, onClick = { onPage(Page.YouTube) }, trailing = { Chevron() })
            }
        }

    }
}

/** A cor do efeito de foco: a automática (círculo com as cores todas) e as da paleta; ← → escolhe. */
@Composable
private fun FocusColorRow(current: String, onPick: (String) -> Unit) {
    val s = LocalStrings.current
    val selected = parseColor(current)
    SwatchRow(
        title = s.focusColor,
        subtitle = if (selected == null) s.colorAuto else current.uppercase(),
        swatches = FocusColors.map { colorSpec(it) to (it?.let(::SolidColor) ?: AutoSwatch) },
        selected = colorSpec(selected),
        onPick = onPick,
    )
}

/** O fundo dos ícones transparentes: nenhum (vê-se o fundo do ecrã), grafite, ou uma cor da paleta. */
@Composable
private fun IconBackgroundRow(current: String, onPick: (String) -> Unit) {
    val s = LocalStrings.current
    SwatchRow(
        title = s.iconBackground,
        subtitle = when (current) {
            AccountSettings.BG_NONE -> s.bgNone
            AccountSettings.BG_GRAPHITE -> s.bgGraphite
            else -> current.uppercase()
        },
        swatches = listOf(AccountSettings.BG_NONE to null, AccountSettings.BG_GRAPHITE to GraphiteBackground) +
            FocusColors.filterNotNull().map { colorSpec(it) to SolidColor(it) },
        selected = current,
        onPick = onPick,
    )
}

/**
 * Uma linha de amostras redondas (← → escolhe, OK fica com ela). Cada amostra é (valor, pintura); sem pintura
 * é "nenhuma": um círculo vazio cortado.
 */
@Composable
private fun SwatchRow(title: String, subtitle: String, swatches: List<Pair<String, Brush?>>, selected: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { (spec, brush) ->
                Surface(
                    onClick = { onPick(spec) },
                    modifier = Modifier.size(26.dp),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        pressedContainerColor = Color.Transparent,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.3f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White), inset = 0.dp, shape = CircleShape),
                    ),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(if (brush != null) Modifier.background(brush) else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape).drawWithCache {
                                onDrawBehind {
                                    drawLine(Color.White.copy(alpha = 0.6f), Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.8f, size.height * 0.2f), 1.5.dp.toPx())
                                }
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (spec.equals(selected, ignoreCase = true)) {
                            val light = (brush as? SolidColor)?.value?.let { it.luminance() > 0.5f } == true
                            Icon(GoatIcons.Check, contentDescription = null, tint = if (light) Color.Black else Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A cor automática (a de cada app): um círculo com as cores todas. */
private val AutoSwatch = Brush.sweepGradient(
    listOf(Color(0xFFF87171), Color(0xFFFACC15), Color(0xFF4ADE80), Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFFF87171)),
)

/** Largura das definições quando o ecrã se divide com a pré-visualização. */
private val SplitPanelWidth = 400.dp

private const val LAYOUT_ANDROID_NAME = "Android TV"
private const val LAYOUT_APPLE_NAME = "Apple TV"

/** "Apple TV" ou "Android TV · Destaque". */
private fun layoutName(account: AccountSettings, s: Strings): String =
    if (account.layout == AccountSettings.LAYOUT_APPLE_TV) LAYOUT_APPLE_NAME else "$LAYOUT_ANDROID_NAME · ${styleName(account.continueStyle, s)}"

/** O nome de um estilo do "Continuar a ver" (disposição Android). */
private fun styleName(style: String, s: Strings): String = when (style) {
    AccountSettings.STYLE_SHELF -> s.styleShelf
    AccountSettings.STYLE_SPOTLIGHT -> s.styleSpotlightNext
    AccountSettings.STYLE_COLUMN -> s.styleColumn
    AccountSettings.STYLE_CAROUSEL -> s.styleCarousel
    else -> s.styleRow
}

@Composable
private fun WallpaperPage(vm: LauncherViewModel, first: FocusRequester, onBoxImages: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val account by vm.account.collectAsStateWithLifecycle()
    val current = account.wallpaper
    val canBrowseSystem = remember { Intent(Intent.ACTION_GET_CONTENT).setType("image/*").resolveActivity(context.packageManager) != null }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::setWallpaperFromImage) }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(Wallpapers.ALL, key = { it.id }) { wp ->
            val selected = current.type == Wallpaper.TYPE_BUILTIN && Wallpapers.byId(current.value).id == wp.id
            GlassRow(
                title = wp.name(s),
                onClick = { vm.setWallpaper(Wallpaper(Wallpaper.TYPE_BUILTIN, wp.id)) },
                leading = {
                    Box(
                        Modifier
                            .size(64.dp, 36.dp)
                            .clip(GoatShapes.Small)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), GoatShapes.Small)
                            .drawWithCache { onDrawBehind { wp.draw(this) } },
                    )
                },
                trailing = { if (selected) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = if (wp == Wallpapers.ALL.first()) Modifier.focusRequester(first) else Modifier,
            )
        }
        // Uma imagem da box (copiada para a app), uma pasta inteira, ou do gestor de ficheiros do sistema.
        val isImage = current.type == Wallpaper.TYPE_FILE || current.type == Wallpaper.TYPE_URL || current.type == Wallpaper.TYPE_FOLDER
        item {
            GlassRow(
                title = s.boxImages,
                subtitle = if (isImage) wallpaperName(current, s) else s.boxImagesHint,
                leading = { Icon(GoatIcons.Folder, null, Modifier.size(20.dp)) },
                onClick = onBoxImages,
                trailing = { if (isImage) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) else Chevron() },
            )
        }
        if (canBrowseSystem) {
            item {
                GlassRow(
                    title = s.fileManager,
                    leading = { Icon(GoatIcons.Image, null, Modifier.size(20.dp)) },
                    onClick = { systemPicker.launch("image/*") },
                )
            }
        }

        // De quanto em quanto tempo muda a foto (fotos do dia e pasta). OK passa ao seguinte: 5 min → … → um dia → 5 min.
        item {
            val minutes = AccountSettings.WALLPAPER_MINUTES
            val next = minutes.getOrElse(minutes.indexOf(account.wallpaperMinutes) + 1) { minutes.first() }
            GlassRow(
                title = s.rotateEvery,
                subtitle = s.rotateInterval(account.wallpaperMinutes),
                onClick = { vm.setWallpaperMinutes(next) },
                trailing = { Chevron() },
            )
        }

        // Fotos do dia, sem conta nem chave: a imagem do dia do Wikimedia Commons e a da NASA. A resolução mínima
        // vale para as duas.
        item { SectionLabel(s.photosOfTheDay) }
        item {
            PhotoSourceRow(WIKIMEDIA_NAME, s.wikimediaHint, current.type == Wallpaper.TYPE_WIKIMEDIA) {
                vm.setWallpaper(Wallpaper(Wallpaper.TYPE_WIKIMEDIA, WikimediaFeed.ID))
            }
        }
        item {
            PhotoSourceRow(NASA_NAME, s.nasaHint, current.type == Wallpaper.TYPE_NASA) {
                vm.setWallpaper(Wallpaper(Wallpaper.TYPE_NASA, ApodFeed.ID))
            }
        }
        item {
            // OK passa à seguinte: qualquer → Full HD → 2K → 4K → qualquer.
            val next = PHOTO_MIN_WIDTHS.getOrElse(PHOTO_MIN_WIDTHS.indexOf(account.photoMinWidth) + 1) { PHOTO_MIN_WIDTHS.first() }
            GlassRow(
                title = s.minWidth,
                subtitle = resolutionName(account.photoMinWidth, s),
                onClick = { vm.setPhotoMinWidth(next) },
                trailing = { Chevron() },
            )
        }
    }
}

/** Uma fonte de fotos do dia (nome da fonte e o que mostra), com o visto quando é o fundo atual. */
@Composable
private fun PhotoSourceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    GlassRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = { if (selected) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
}

/** Ligação ao YouTube por código (como na app de TV do YouTube): a TV nunca vê a password. */
@Composable
private fun YouTubePage(vm: LauncherViewModel, first: FocusRequester, onDone: () -> Unit) {
    val s = LocalStrings.current
    val login by vm.youtubeLogin.collectAsStateWithLifecycle()
    val linked by vm.youtubeLinked.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        if (!linked) vm.startYouTubeLogin()
        onDispose { vm.cancelYouTubeLogin() }
    }
    LaunchedEffect(linked) { if (linked) onDone() }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(s.youtubeGoTo, style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim)
        Text(
            login?.verificationUrl?.removePrefix("https://")?.removePrefix("www.") ?: "google.com/device",
            style = MaterialTheme.typography.headlineSmall,
            color = GoatColors.Sky,
        )
        Spacer(Modifier.height(14.dp))
        Text(s.youtubeEnterCode, style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim)
        Text(
            login?.userCode ?: "· · ·",
            style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 4.sp, textDirection = TextDirection.Ltr),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (login == null) s.youtubeRequesting else s.youtubeWaiting,
            style = MaterialTheme.typography.bodyMedium,
            color = GoatColors.TextFaint,
        )
        Spacer(Modifier.height(18.dp))
        GlassButton(s.cancel, onClick = onDone, modifier = Modifier.focusRequester(first))
    }
}

@Composable
private fun HiddenAppsPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val list = apps.orEmpty()
    Column {
        Text(s.hiddenAppsHint, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(list, key = { it.packageName }) { app ->
                val hidden = app.packageName in device.hidden
                GlassRow(
                    title = app.label,
                    onClick = { vm.setHidden(app.packageName, !hidden) },
                    leading = {
                        Box(
                            Modifier.size(64.dp, 36.dp).clip(GoatShapes.Small).background(app.color),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                app.banner != null -> Image(app.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                app.icon != null -> Image(app.icon, null, modifier = Modifier.size(24.dp))
                            }
                        }
                    },
                    trailing = { Toggle(hidden) },
                    modifier = if (app == list.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(GoatIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
}

/** [minutes] desde a meia-noite (de hoje) no formato do relógio de cima: "07:42", ou "7:42 PM", "午前7:42" sem 24 horas. */
private fun sunClock(minutes: Int, s: Strings, clock24h: Boolean): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return clockText(time, s, clock24h)
}

private fun wallpaperName(w: Wallpaper, s: cc.slokker.goatlauncher.ui.Strings): String = when (w.type) {
    Wallpaper.TYPE_BUILTIN -> Wallpapers.byId(w.value).name(s)
    Wallpaper.TYPE_WIKIMEDIA -> WIKIMEDIA_NAME
    Wallpaper.TYPE_NASA -> NASA_NAME
    Wallpaper.TYPE_FOLDER -> s.wallpaperFolder(w.value.substringAfterLast('/'))
    else -> s.wallpaperImage
}

private val IDLE_MINUTES = listOf(0, 1, 2, 5, 10, 30)
private val AUTOSTART_DELAYS = listOf(0, 3, 5, 10, 20)
private val PHOTO_MIN_WIDTHS = listOf(0, 1920, 2560, 3840)

/** Os nomes das fontes de fotos do dia (nomes próprios: não se traduzem). */
private const val WIKIMEDIA_NAME = "Wikimedia Commons"
private const val NASA_NAME = "NASA APOD"

private fun resolutionName(px: Int, s: Strings): String = when (px) {
    0 -> s.anyResolution
    1920 -> "Full HD (1920 px)"
    2560 -> "2K (2560 px)"
    3840 -> "4K (3840 px)"
    else -> "$px px"
}

/** Com as definições protegidas pelo controlo parental: só o botão que pede o código. */
@Composable
private fun ProtectedPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(s.settingsProtected, style = MaterialTheme.typography.titleMedium)
        Text(s.settingsProtectedHint, style = MaterialTheme.typography.bodyMedium, color = GoatColors.TextDim)
        Spacer(Modifier.height(18.dp))
        GlassButton(s.unlock, onClick = { vm.withSettingsUnlocked { } }, modifier = Modifier.focusRequester(first))
    }
}

/** Escolher uma app (ou [none], que dá null): para abrir ao ligar e para o botão Netflix. */
@Composable
private fun AppPickerPage(vm: LauncherViewModel, first: FocusRequester, current: String?, none: String, onPick: (String?) -> Unit) {
    val apps by vm.allApps.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            GlassRow(
                title = none,
                onClick = { onPick(null) },
                trailing = { if (current == null) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.focusRequester(first),
            )
        }
        items(apps.orEmpty(), key = { it.packageName }) { app ->
            GlassRow(
                title = app.label,
                onClick = { onPick(app.packageName) },
                leading = { AppThumb(app) },
                trailing = { if (current == app.packageName) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
    }
}

/** Controlo parental ligado: código, apps bloqueadas, proteção das definições e horário. */
@Composable
private fun ParentalPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    var confirmOff by remember { mutableStateOf(false) }
    val from = device.lockFrom
    val to = device.lockTo
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            GlassRow(
                title = s.lockedApps,
                subtitle = s.lockedCount(device.locked.size),
                onClick = { onPage(Page.LockedApps) },
                trailing = { Chevron() },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            GlassRow(
                title = s.lockSettings,
                subtitle = s.lockSettingsHint,
                onClick = { vm.setLockSettings(!device.lockSettings) },
                trailing = { Toggle(device.lockSettings) },
            )
        }
        item {
            GlassRow(
                title = s.lockHours,
                subtitle = if (from != null && to != null) s.lockBetween(from, to) else s.lockAlways,
                onClick = { if (from != null) vm.setLockHours(null, null) else vm.setLockHours(20, 8) },
                trailing = { Toggle(from != null) },
            )
        }
        if (from != null && to != null) {
            item { HourRow(s.lockFrom, from) { vm.setLockHours(it, to) } }
            item { HourRow(s.lockTo, to) { vm.setLockHours(from, it) } }
        }
        item { GlassRow(title = s.changePin, onClick = { onPage(Page.PinSetup) }, trailing = { Chevron() }) }
        item {
            GlassRow(
                title = s.parentalDisable,
                subtitle = if (confirmOff) s.pressAgainToConfirm else s.parentalDisableHint,
                onClick = {
                    if (confirmOff) {
                        vm.setPin(null)
                        onPage(Page.Main)
                    }
                    confirmOff = !confirmOff
                },
            )
        }
    }
}

/** Uma hora (0–23) que muda com ← →. Início e fim iguais querem dizer o dia todo (ver ParentalLock.inWindow). */
@Composable
private fun HourRow(title: String, hour: Int, onChange: (Int) -> Unit) {
    val s = LocalStrings.current
    GlassRow(
        title = title,
        subtitle = s.hourHint,
        onClick = { onChange((hour + 1) % 24) },
        trailing = { Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.onPreviewKeyEvent { e ->
            val delta = when (e.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown) onChange((hour + delta + 24) % 24)
            true
        },
    )
}

/** Novo código: escreve-se duas vezes. O primeiro liga o controlo parental. */
@Composable
private fun PinSetupPage(vm: LauncherViewModel, onDone: () -> Unit) {
    val s = LocalStrings.current
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        key(attempt) {
            PinPad(
                title = if (firstPin == null) s.newPin else s.repeatPin,
                message = error,
                onDone = { pin ->
                    val pending = firstPin
                    when {
                        pending == null -> {
                            firstPin = pin
                            error = null
                        }
                        pending == pin -> {
                            vm.setPin(pin)
                            vm.toast(s.pinSaved)
                            onDone()
                        }
                        else -> {
                            firstPin = null
                            error = s.pinMismatch
                        }
                    }
                    attempt++
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(s.forgotPin, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LockedAppsPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val list = apps.orEmpty()
    Column {
        Text(s.lockedAppsHint, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(list, key = { it.packageName }) { app ->
                val locked = app.packageName in device.locked
                GlassRow(
                    title = app.label,
                    onClick = { vm.setLocked(app.packageName, !locked) },
                    leading = { AppThumb(app) },
                    trailing = { Toggle(locked) },
                    modifier = if (app == list.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

/** O banner (ou o ícone) pequeno à esquerda das listas de apps. */
@Composable
private fun AppThumb(app: cc.slokker.goatlauncher.data.LauncherApp) {
    Box(
        Modifier.size(64.dp, 36.dp).clip(GoatShapes.Small).background(app.color),
        contentAlignment = Alignment.Center,
    ) {
        when {
            app.banner != null -> Image(app.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            app.icon != null -> Image(app.icon, null, modifier = Modifier.size(24.dp))
        }
    }
}

/** "Botão Netflix" para o da Shield; os outros pelo nome do Android ("Button 12", "Settings", "Guide"…). */
private fun buttonName(keyCode: Int, scanCode: Int, s: Strings): String =
    if (keyCode == ButtonMap.SHIELD_NETFLIX_KEY && scanCode == ButtonMap.SHIELD_NETFLIX_SCAN) {
        s.buttonNetflix
    } else {
        android.view.KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
            .lowercase().replaceFirstChar { it.uppercase() }
    }

/** Os botões do comando com app (até 5) e "Adicionar botão". */
@Composable
private fun ButtonsPage(vm: LauncherViewModel, first: FocusRequester, onEdit: (Pair<Int, Int>) -> Unit, onAdd: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val device by vm.device.collectAsStateWithLifecycle()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    var guard by remember { mutableStateOf(HomeGuardService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        guard = HomeGuardService.isEnabled(context)
        onPauseOrDispose { }
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(device.buttons, key = { "${it.keyCode}:${it.scanCode}" }) { b ->
            GlassRow(
                title = buttonName(b.keyCode, b.scanCode, s),
                subtitle = apps?.firstOrNull { it.packageName == b.app }?.label ?: b.app,
                onClick = { onEdit(b.keyCode to b.scanCode) },
                trailing = { Chevron() },
                modifier = if (b == device.buttons.first()) Modifier.focusRequester(first) else Modifier,
            )
        }
        item {
            val full = device.buttons.size >= ButtonMap.MAX
            GlassRow(
                title = if (guard) s.addButton else s.accessibilityOpen,
                subtitle = when {
                    !guard -> s.needsGuard
                    full -> s.buttonsFull
                    else -> null
                },
                onClick = {
                    when {
                        // Só o serviço de acessibilidade vê os botões (e os abre depois): sem ele, vai-se ligá-lo.
                        !guard -> SystemIntents.start(context, SystemIntents.accessibilitySettings())
                        !full -> onAdd()
                    }
                },
                modifier = if (device.buttons.isEmpty()) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

/** "Carrega no botão": o HomeGuardService apanha o próximo botão que se possa usar. */
@Composable
private fun ButtonCapturePage(first: FocusRequester, onCaptured: (Pair<Int, Int>) -> Unit, onCancel: () -> Unit) {
    val s = LocalStrings.current
    val captured by rememberUpdatedState(onCaptured)
    DisposableEffect(Unit) {
        ButtonCapture.listener = { key, scan -> captured(key to scan) }
        onDispose { ButtonCapture.listener = null }
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(s.pressButton, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(s.pressButtonHint, style = MaterialTheme.typography.bodyMedium, color = GoatColors.TextDim)
        Spacer(Modifier.height(18.dp))
        GlassButton(s.cancel, onClick = onCancel, modifier = Modifier.focusRequester(first))
    }
}

/** "Automático" e as 15 línguas, cada uma com o nome na própria língua. Ao escolher, o ecrã muda logo. */
/**
 * Onde está a TV (para o tempo e o dia/noite): automática pelo IP, ou uma cidade procurada pelo nome.
 * O IP engana-se com VPN ou com alguns operadores (dá a cidade do servidor).
 */
@Composable
private fun LocationPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    fun search() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = vm.searchPlaces(query)
            searching = false
        }
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            Text(s.locationHint, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        }
        item {
            GlassRow(
                title = vm.ipCity()?.let(s::locationAutoCity) ?: s.locationAuto,
                onClick = { vm.setLocation(null) },
                trailing = { if (device.location == null) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.focusRequester(first),
            )
        }
        device.location?.let { place ->
            item {
                GlassRow(title = place.name, onClick = {}, trailing = { Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) })
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvTextField(query, { query = it }, s.locationSearchHint, Modifier.weight(1f), onDone = ::search)
                GlassButton(s.search, onClick = ::search)
            }
        }
        when {
            searching -> item { Text(s.loading, color = GoatColors.TextFaint, modifier = Modifier.padding(12.dp)) }
            results?.isEmpty() == true -> item { Text(s.locationNone, color = GoatColors.TextFaint, modifier = Modifier.padding(12.dp)) }
            else -> items(results.orEmpty(), key = { "${it.lat},${it.lon}" }) { place ->
                GlassRow(title = place.name, onClick = { vm.setLocation(place) })
            }
        }
    }
}

@Composable
private fun LanguagePage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    val current = Strings.normalize(account.language)
    val options = listOf("auto" to s.languageAuto) + Strings.LANGUAGES
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(options, key = { it.first }) { (code, name) ->
            GlassRow(
                title = name,
                onClick = { vm.setLanguage(code) },
                trailing = { if (code == current) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = if (code == current) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}
