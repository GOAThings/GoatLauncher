package cc.slokker.goatlauncher.ui.settings

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.BuildConfig
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.RedditWallpapers
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

private enum class Page { Main, Wallpaper, WallpaperFiles, HiddenApps, YouTube }

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
    val first = remember { FocusRequester() }

    // O navegador de imagens trata do seu foco (muda a cada pasta). A primeira página só leva o foco se
    // [autoFocus]: aberta por passar o foco no separador, o foco fica lá até se carregar em ↓.
    var firstPage by remember { mutableStateOf(true) }
    LaunchedEffect(page) {
        if (page != Page.WallpaperFiles && (autoFocus || !firstPage)) focusSoon(first)
        firstPage = false
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
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
            when (page) {
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

        item { SectionLabel(s.apps) }
        item {
            GlassRow(
                title = s.hiddenApps,
                subtitle = s.hiddenCount(device.hidden.size),
                onClick = { onPage(Page.HiddenApps) },
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
