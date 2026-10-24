package cc.slokker.goatlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.home.BannerImage
import cc.slokker.goatlauncher.ui.home.FavoriteCount
import cc.slokker.goatlauncher.ui.home.FocusScale
import cc.slokker.goatlauncher.ui.home.FocusStyle
import cc.slokker.goatlauncher.ui.home.FocusDim
import cc.slokker.goatlauncher.ui.home.dockColor
import cc.slokker.goatlauncher.ui.home.LocalFocusDim
import cc.slokker.goatlauncher.ui.home.LocalFocusStyle
import cc.slokker.goatlauncher.ui.home.LocalIconBackground
import cc.slokker.goatlauncher.ui.home.isSeeThrough
import cc.slokker.goatlauncher.ui.home.TopClock
import cc.slokker.goatlauncher.ui.home.TopGreeting
import cc.slokker.goatlauncher.ui.home.WallpaperView
import cc.slokker.goatlauncher.ui.home.focusEffect
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import cc.slokker.goatlauncher.ui.theme.ShelfTitleStyle
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
import cc.slokker.goatlauncher.ui.theme.tracking
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** O tamanho do ecrã da TV em dp: a pré-visualização desenha-se a este tamanho e é reduzida. */
private val ScreenWidth = 960.dp
private val ScreenHeight = 540.dp

// As medidas do ecrã inicial (HomeScreen), para a pré-visualização ficar igual.
private val TileWidth = 148.dp
private val TileGap = 24.dp
private val TilesRowWidth = TileWidth * FavoriteCount + TileGap * (FavoriteCount - 1)

/**
 * A pré-visualização da página Aparência: o ecrã inicial em ponto pequeno, com o fundo, as apps e o
 * "Continuar a ver" verdadeiros, na disposição e com o efeito de foco escolhidos. O foco está no primeiro
 * favorito com "Continuar a ver" (para se ver também essa parte). Só se vê: nada aqui recebe foco.
 */
@Composable
fun AppearancePreview(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val account by vm.account.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val watchNext by vm.watchNext.collectAsStateWithLifecycle()
    val night by vm.night.collectAsStateWithLifecycle()
    val reddit by vm.redditImage.collectAsStateWithLifecycle()
    val folderImage by vm.folderImage.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()

    val favorites = apps.orEmpty().take(FavoriteCount)
    val focusApp = favorites.firstOrNull { !watchNext[it.packageName].isNullOrEmpty() } ?: favorites.firstOrNull()
    val items = if (account.showContinueWatching) focusApp?.let { watchNext[it.packageName] }.orEmpty() else emptyList()
    val appleTv = account.layout == AccountSettings.LAYOUT_APPLE_TV
    val shelf = !appleTv && account.continueStyle == AccountSettings.STYLE_SHELF
    val focusStyle = remember(account) { FocusStyle.of(account) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier
            .aspectRatio(16f / 9f)
            .shadow(18.dp, shape)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
    ) {
        VirtualScreen {
            WallpaperView(account.wallpaper, reddit?.url ?: folderImage)
            // Destaque: a imagem do conteúdo enche o fundo (os logos dos canais não).
            val hero = items.firstOrNull()?.takeIf { shelf && !it.isChannel && it.imageUri != null }
            if (hero != null) {
                AsyncImage(model = hero.imageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.25f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
            )
            if (shelf && items.isNotEmpty()) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.45f to Color.Black.copy(alpha = 0.35f), 0.75f to Color.Transparent),
                    ),
                )
            }
            CompositionLocalProvider(
        LocalFocusStyle provides focusStyle,
        LocalIconBackground provides account.iconBackground,
        LocalFocusDim provides remember { FocusDim() },
    ) {
                Column(Modifier.fillMaxSize().padding(TvSafePadding)) {
                    PreviewTopBar(account, weather?.takeIf { account.showWeather }, showApps = !appleTv)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val tiles: @Composable () -> Unit = {
                            Row(Modifier.width(TilesRowWidth), horizontalArrangement = Arrangement.spacedBy(TileGap, Alignment.CenterHorizontally)) {
                                favorites.forEach { app ->
                                    PreviewTile(vm, app, device.icons[app.packageName], focused = app == focusApp, showLabel = account.showAppName)
                                }
                            }
                        }
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            when {
                                appleTv -> {
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        if (items.isNotEmpty()) TopShelfPreview(items)
                                    }
                                    Spacer(Modifier.height(28.dp))
                                    Box(
                                        Modifier
                                            .requiredWidth(TilesRowWidth + 60.dp)
                                            .background(dockColor(night), RoundedCornerShape(30.dp))
                                            .padding(horizontal = 30.dp, vertical = 18.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { tiles() }
                                }
                                shelf -> {
                                    Spacer(Modifier.weight(1f))
                                    if (focusApp != null && items.isNotEmpty()) ShelfPreview(focusApp, items.first(), items.size)
                                    Spacer(Modifier.height(56.dp))
                                    tiles()
                                }
                                else -> {
                                    Spacer(Modifier.weight(1f))
                                    if (focusApp != null && items.isNotEmpty()) RowPreview(focusApp, items)
                                    Spacer(Modifier.height(40.dp))
                                    tiles()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Desenha [content] ao tamanho do ecrã da TV e reduz tudo à largura que houver. */
@Composable
private fun VirtualScreen(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.layout { measurable, constraints ->
            val w = ScreenWidth.roundToPx()
            val h = ScreenHeight.roundToPx()
            val placeable = measurable.measure(Constraints.fixed(w, h))
            val scale = constraints.maxWidth.toFloat() / w
            layout(constraints.maxWidth, (h * scale).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        },
        content = content,
    )
}

/** A barra de cima: saudação, separadores (sem Apps na Apple TV) e as horas. */
@Composable
private fun PreviewTopBar(account: AccountSettings, weather: WeatherNow?, showApps: Boolean) {
    val s = LocalStrings.current
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        TopGreeting(account.showGreeting, weather, Modifier.align(Alignment.CenterStart))
        Row(
            Modifier.align(Alignment.Center).background(Color.White.copy(alpha = 0.14f), CircleShape).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOfNotNull(s.tabHome, s.tabApps.takeIf { showApps }, s.settings).forEachIndexed { i, label ->
                Text(
                    label,
                    Modifier
                        .background(if (i == 0) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (i == 0) Color.White else Color.White.copy(alpha = 0.78f),
                )
            }
        }
        TopClock(account.clock24h, Modifier.align(Alignment.CenterEnd))
    }
}

/** Um favorito, como no ecrã inicial; o que tem foco aparece aumentado e com o efeito de foco. */
@Composable
private fun PreviewTile(vm: LauncherViewModel, app: LauncherApp, iconSpec: String?, focused: Boolean, showLabel: Boolean) {
    val custom = remember(iconSpec) { iconSpec?.let { vm.icons.cached(it) } }
    Column(Modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(TileWidth, TileWidth * 9 / 16)
                .graphicsLayer {
                    if (focused) {
                        scaleX = FocusScale
                        scaleY = FocusScale
                    }
                }
                .focusEffect(focused, app.color)
                .clip(GoatShapes.Control)
                .background(if (isSeeThrough(custom)) Color.Transparent else app.color),
        ) {
            BannerImage(app, custom, customPending = iconSpec != null && custom == null)
        }
        if (showLabel) {
            Text(
                app.label,
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal),
                color = if (focused) GoatColors.Text else GoatColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A imagem de um conteúdo do "Continuar a ver", com a barra de progresso por dentro. */
@Composable
private fun PreviewCard(item: WatchNextItem, width: Dp, height: Dp, corner: Dp) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(corner)).background(Color(0xFF1C2333))) {
        if (item.imageUri != null) {
            val fill = !item.isChannel
            AsyncImage(
                model = item.imageUri,
                contentDescription = null,
                contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize().then(if (fill) Modifier else Modifier.padding(height / 6)),
            )
        } else {
            Icon(GoatIcons.Play, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
        item.progress?.let { p ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White))
            }
        }
    }
}

/** Disposição Android, fila de cartões. */
@Composable
private fun RowPreview(app: LauncherApp, items: List<WatchNextItem>) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Text(s.continueIn(app.label), style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            items.take(5).forEach { item ->
                val h = 88.dp
                PreviewCard(item, h * item.aspectRatio.coerceIn(0.6f, 2f), h, 8.dp)
            }
        }
    }
}

/** Disposição Android, destaque. */
@Composable
private fun ShelfPreview(app: LauncherApp, item: WatchNextItem, count: Int) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Text(
                s.shelfLabel(app.label).uppercase(s.locale),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = tracking(2f)),
                color = GoatColors.TextDim,
            )
            Spacer(Modifier.height(6.dp))
            Text(item.title, style = ShelfTitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.progress?.let { p ->
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(320.dp).height(5.dp).background(Color.White.copy(alpha = 0.3f), GoatShapes.Small)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White, GoatShapes.Small))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.background(Color.White.copy(alpha = 0.18f), GoatShapes.Control).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(GoatIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.continueNow, style = MaterialTheme.typography.labelLarge)
                }
                if (count > 1) Text(s.shelfMore(count - 1), style = MaterialTheme.typography.labelMedium, color = GoatColors.TextFaint)
            }
        }
    }
}

/** Disposição Apple TV: os cartões grandes por cima da doca. */
@Composable
private fun TopShelfPreview(items: List<WatchNextItem>) {
    val s = LocalStrings.current
    Column {
        Text(
            s.continueWatching,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = GoatColors.TextDim,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            items.take(3).forEach { PreviewCard(it, 380.dp, 214.dp, 14.dp) }
        }
    }
}
