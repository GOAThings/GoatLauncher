package cc.slokker.goatlauncher.ui.settings

import cc.slokker.goatlauncher.ui.Strings
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.key
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.after
import kotlinx.coroutines.launch
import cc.slokker.goatlauncher.data.ApodFeed
import cc.slokker.goatlauncher.data.WikimediaFeed
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.SectionLabel
import cc.slokker.goatlauncher.ui.common.Toggle
import cc.slokker.goatlauncher.ui.home.Wallpapers
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
/** Apps: as escondidas e "Manter como ecrã inicial". */
@Composable
internal fun AppsPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
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

/** Modo ambiente: quando entra, o que mostra, o protetor de ecrã e não deixar a TV adormecer. */
@Composable
internal fun AmbientPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            // OK passa ao tempo seguinte: desligado → 1 → 2 → 5 → 10 → 30 min → desligado.
            val next = AccountSettings.IDLE_MINUTES.after(account.idleMinutes)
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
                onClick = { vm.setAmbientStyle(AccountSettings.AMBIENT_STYLES.after(account.ambientStyle)) },
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

@Composable
internal fun WallpaperPage(vm: LauncherViewModel, first: FocusRequester, onBoxImages: () -> Unit) {
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
            val next = AccountSettings.WALLPAPER_MINUTES.after(account.wallpaperMinutes)
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
            val next = AccountSettings.PHOTO_MIN_WIDTHS.after(account.photoMinWidth)
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

@Composable
internal fun HiddenAppsPage(vm: LauncherViewModel, first: FocusRequester) {
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

internal fun wallpaperName(w: Wallpaper, s: cc.slokker.goatlauncher.ui.Strings): String = when (w.type) {
    Wallpaper.TYPE_BUILTIN -> Wallpapers.byId(w.value).name(s)
    Wallpaper.TYPE_WIKIMEDIA -> WIKIMEDIA_NAME
    Wallpaper.TYPE_NASA -> NASA_NAME
    Wallpaper.TYPE_FOLDER -> s.wallpaperFolder(w.value.substringAfterLast('/'))
    else -> s.wallpaperImage
}

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
