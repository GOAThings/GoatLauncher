package cc.slokker.goatlauncher.ui.settings

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

import androidx.compose.foundation.Image
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
import cc.slokker.goatlauncher.data.RedditWallpapers
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.UpdateState
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
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import cc.slokker.goatlauncher.ui.theme.TvSafePadding

private enum class Page { Main, Wallpaper, WallpaperFiles, HiddenApps, YouTube, Autostart, Buttons, ButtonCapture, ButtonApp, Parental, PinSetup, LockedApps }

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

    Box(
        Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.background(Color.Black.copy(alpha = 0.35f)).padding(TvSafePadding))
            // Voltar sai sempre das Definições para o Início, de qualquer página.
            .onBackKey {
                onExit()
                true
            },
    ) {
        Column(
            Modifier
                .align(if (embedded) Alignment.TopCenter else Alignment.CenterEnd)
                .then(if (embedded) Modifier.padding(top = 16.dp) else Modifier)
                .width(if (embedded) 640.dp else 520.dp)
                .fillMaxHeight()
                .glass(GoatShapes.Panel, tint = Color(0xD9111522))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                when (page) {
                    Page.Main -> s.settings
                    Page.Wallpaper -> s.wallpaper
                    Page.WallpaperFiles -> s.boxImages
                    Page.HiddenApps -> s.hiddenApps
                    Page.YouTube -> s.youtube
                    Page.Autostart -> s.autostart
                    Page.Buttons -> s.buttons
                    Page.ButtonCapture -> s.addButton
                    Page.ButtonApp -> buttonKey?.let { (k, sc) -> s.buttonOpens(buttonName(k, sc, s)) } ?: s.buttons
                    Page.Parental, Page.PinSetup -> s.parental
                    Page.LockedApps -> s.lockedApps
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
            if (locked) {
                ProtectedPage(vm, first)
            } else when (page) {
                Page.Main -> MainPage(vm, first, onPage = { page = it })
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
                    first = first,
                )
                Page.HiddenApps -> HiddenAppsPage(vm, first)
                Page.YouTube -> YouTubePage(vm, first, onDone = { page = Page.Main })
                Page.Autostart -> {
                    val device by vm.device.collectAsStateWithLifecycle()
                    AppPickerPage(vm, first, current = device.autostart, none = s.autostartNone) {
                        vm.setAutostart(it)
                        page = Page.Main
                    }
                }
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
            }
        }
    }
}

@Composable
private fun MainPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val account by vm.account.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var confirmUnlink by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item { SectionLabel(s.appearance) }
        item {
            GlassRow(
                title = s.wallpaper,
                subtitle = wallpaperName(account.wallpaper, s),
                onClick = { onPage(Page.Wallpaper) },
                trailing = { Chevron() },
                modifier = Modifier.focusRequester(first),
            )
        }
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
        item {
            GlassRow(
                title = s.continueWatching,
                subtitle = s.continueWatchingHint,
                onClick = { vm.setShowContinueWatching(!account.showContinueWatching) },
                trailing = { Toggle(account.showContinueWatching) },
            )
        }
        item {
            // OK passa ao estilo seguinte: fila de cartões → destaque → Down Under → fila de cartões.
            val (label, next) = when (account.continueStyle) {
                AccountSettings.STYLE_SHELF -> s.styleShelf to AccountSettings.STYLE_DOWN_UNDER
                AccountSettings.STYLE_DOWN_UNDER -> s.styleDownUnder to AccountSettings.STYLE_ROW
                else -> s.styleRow to AccountSettings.STYLE_SHELF
            }
            GlassRow(
                title = s.continueStyle,
                subtitle = label,
                onClick = { vm.setContinueStyle(next) },
                trailing = { Chevron() },
            )
        }
        item {
            // OK passa ao tempo seguinte: desligado → 1 → 2 → 5 → 10 → 30 min → desligado.
            val next = IDLE_MINUTES.getOrElse(IDLE_MINUTES.indexOf(account.idleMinutes) + 1) { IDLE_MINUTES.first() }
            GlassRow(
                title = s.idleMode,
                subtitle = if (account.idleMinutes > 0) s.idleAfter(account.idleMinutes) else s.idleOff,
                onClick = { vm.setIdleMinutes(next) },
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

        item { SectionLabel(s.apps) }
        item {
            GlassRow(
                title = s.hiddenApps,
                subtitle = s.hiddenCount(device.hidden.size),
                onClick = { onPage(Page.HiddenApps) },
                trailing = { Chevron() },
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
            GlassRow(
                title = s.homeGuard,
                subtitle = if (guard) s.homeGuardOn else s.homeGuardOff,
                onClick = { SystemIntents.start(context, SystemIntents.accessibilitySettings()) },
                trailing = { Toggle(guard) },
            )
        }

        item { SectionLabel(s.startup) }
        item {
            val apps by vm.allApps.collectAsStateWithLifecycle()
            GlassRow(
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

        item { SectionLabel(s.remote) }
        item {
            val context = LocalContext.current
            val n = device.buttons.size
            GlassRow(
                title = s.buttons,
                // Sem o serviço de acessibilidade os botões não chegam ao launcher: diz-se logo.
                subtitle = when {
                    n == 0 -> s.buttonsNone
                    !HomeGuardService.isEnabled(context) -> "${s.buttonsCount(n)} · ${s.needsGuard}"
                    else -> s.buttonsCount(n)
                },
                onClick = { onPage(Page.Buttons) },
                trailing = { Chevron() },
            )
        }

        item { SectionLabel(s.parental) }
        item {
            GlassRow(
                title = s.parental,
                subtitle = if (device.pin == null) s.parentalOff else s.parentalOn(device.locked.size),
                onClick = { if (device.pin == null) onPage(Page.PinSetup) else vm.withParentalUnlocked { onPage(Page.Parental) } },
                trailing = { Chevron() },
            )
        }

        item { SectionLabel(s.account) }
        val current = session
        if (current == null) {
            item { GlassRow(title = s.linkAccount, subtitle = s.linkAccountHint, onClick = vm::openActivation, trailing = { Chevron() }) }
        } else {
            item { GlassRow(title = s.linkedAs(current.email), subtitle = current.deviceName, onClick = vm::syncNow) }
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

        item { SectionLabel(s.updates) }
        item {
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
                onClick = { if (update is UpdateState.Available) vm.installUpdate() else vm.checkForUpdate(manual = true) },
            )
        }

        item { SectionLabel(s.android) }
        item {
            GlassRow(
                title = s.androidSettings,
                onClick = { if (!SystemIntents.start(context, SystemIntents.androidSettings())) vm.toast(s.cantOpen) },
                trailing = { Chevron() },
            )
        }
        item {
            Text(
                s.version(BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = GoatColors.TextFaint,
                modifier = Modifier.padding(start = 12.dp, top = 16.dp),
            )
        }
    }
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
        // Uma imagem da box (copiada para a app) ou do gestor de ficheiros do sistema.
        val isImage = current.type == Wallpaper.TYPE_FILE || current.type == Wallpaper.TYPE_URL
        item {
            GlassRow(
                title = s.boxImages,
                subtitle = if (isImage) s.wallpaperImage else s.boxImagesHint,
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

        // Reddit: subreddits de fotos sugeridos, ou outro escrito à mão.
        item { SectionLabel(s.reddit) }
        item {
            Text(s.redditHint, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        }
        item {
            // OK passa à seguinte: qualquer → Full HD → 2K → 4K → qualquer.
            val next = REDDIT_MIN_WIDTHS.getOrElse(REDDIT_MIN_WIDTHS.indexOf(account.redditMinWidth) + 1) { REDDIT_MIN_WIDTHS.first() }
            GlassRow(
                title = s.minWidth,
                subtitle = resolutionName(account.redditMinWidth, s),
                onClick = { vm.setRedditMinWidth(next) },
                trailing = { Chevron() },
            )
        }
        val isReddit = current.type == Wallpaper.TYPE_REDDIT
        items(RedditWallpapers.SUGGESTED, key = { "r-$it" }) { sub ->
            GlassRow(
                title = "r/$sub",
                onClick = { vm.setWallpaper(Wallpaper(Wallpaper.TYPE_REDDIT, sub)) },
                trailing = {
                    if (isReddit && current.value.equals(sub, ignoreCase = true)) {
                        Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                },
            )
        }
        item {
            var custom by remember {
                mutableStateOf(current.value.takeIf { isReddit && RedditWallpapers.SUGGESTED.none { s -> s.equals(it, true) } }.orEmpty())
            }
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvTextField(custom, { custom = it }, s.otherSubreddit, Modifier.weight(1f))
                GlassButton(s.use, onClick = {
                    val sub = custom.trim().removePrefix("/").removePrefix("r/").trim('/')
                    if (sub.matches(Regex("[A-Za-z0-9_]{2,21}"))) vm.setWallpaper(Wallpaper(Wallpaper.TYPE_REDDIT, sub))
                })
            }
        }
    }
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
            style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 4.sp),
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

private fun wallpaperName(w: Wallpaper, s: cc.slokker.goatlauncher.ui.Strings): String = when (w.type) {
    Wallpaper.TYPE_BUILTIN -> Wallpapers.byId(w.value).name(s)
    Wallpaper.TYPE_REDDIT -> "r/${w.value}"
    else -> s.wallpaperImage
}

private val IDLE_MINUTES = listOf(0, 1, 2, 5, 10, 30)
private val AUTOSTART_DELAYS = listOf(0, 3, 5, 10, 20)
private val REDDIT_MIN_WIDTHS = listOf(0, 1920, 2560, 3840)

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

/** Uma hora (0–23) que muda com ← →. A hora de início e a de fim nunca ficam iguais. */
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
