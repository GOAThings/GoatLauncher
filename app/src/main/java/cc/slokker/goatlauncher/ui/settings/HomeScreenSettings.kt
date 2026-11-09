package cc.slokker.goatlauncher.ui.settings

import cc.slokker.goatlauncher.ui.Strings
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.Toggle
import cc.slokker.goatlauncher.ui.home.Wallpapers
import cc.slokker.goatlauncher.ui.theme.GoatColors

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
                    leading = { AppThumb(app) },
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
internal const val WIKIMEDIA_NAME = "Wikimedia Commons"
internal const val NASA_NAME = "NASA APOD"

internal fun resolutionName(px: Int, s: Strings): String = when (px) {
    0 -> s.anyResolution
    1920 -> "Full HD (1920 px)"
    2560 -> "2K (2560 px)"
    3840 -> "4K (3840 px)"
    else -> "$px px"
}
