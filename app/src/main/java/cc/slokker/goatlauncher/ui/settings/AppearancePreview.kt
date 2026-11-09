package cc.slokker.goatlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.home.BannerImage
import cc.slokker.goatlauncher.ui.home.ContinueSection
import cc.slokker.goatlauncher.ui.home.Dock
import cc.slokker.goatlauncher.ui.home.FavoriteCount
import cc.slokker.goatlauncher.ui.home.FavoritesColumn
import cc.slokker.goatlauncher.ui.home.FavoritesRow
import cc.slokker.goatlauncher.ui.home.FocusDim
import cc.slokker.goatlauncher.ui.home.FocusScale
import cc.slokker.goatlauncher.ui.home.FocusStyle
import cc.slokker.goatlauncher.ui.home.HeroBackdropImage
import cc.slokker.goatlauncher.ui.home.HomeBody
import cc.slokker.goatlauncher.ui.home.HomeMetrics
import cc.slokker.goatlauncher.ui.home.HomeScrims
import cc.slokker.goatlauncher.ui.home.HomeStyle
import cc.slokker.goatlauncher.ui.home.LocalFocusDim
import cc.slokker.goatlauncher.ui.home.LocalFocusStyle
import cc.slokker.goatlauncher.ui.home.LocalIconBackground
import cc.slokker.goatlauncher.ui.home.MoveHintText
import cc.slokker.goatlauncher.ui.home.SectionActions
import cc.slokker.goatlauncher.ui.home.TabLabel
import cc.slokker.goatlauncher.ui.home.TopClock
import cc.slokker.goatlauncher.ui.home.TopGreeting
import cc.slokker.goatlauncher.ui.home.WallpaperView
import cc.slokker.goatlauncher.ui.home.dockColor
import cc.slokker.goatlauncher.ui.home.focusEffect
import cc.slokker.goatlauncher.ui.home.isSeeThrough
import cc.slokker.goatlauncher.ui.home.rememberHeroBackdrop
import cc.slokker.goatlauncher.ui.theme.BarPadding
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
import kotlin.math.roundToInt

/** O tamanho do ecrã da TV em dp: a pré-visualização desenha-se a este tamanho e é reduzida. */
private val ScreenWidth = 960.dp
private val ScreenHeight = 540.dp

/**
 * A pré-visualização da página Aparência: o ecrã inicial em ponto pequeno, com o fundo, as apps e o
 * "Continuar a ver" verdadeiros, na disposição e com o efeito de foco escolhidos. O corpo é o do ecrã inicial
 * (HomeBody, ContinueSection, o fundo do destaque e os véus): só os banners são desenhados à parte, com o primeiro
 * favorito com "Continuar a ver" como se tivesse o foco. Só se vê: nada aqui recebe o foco.
 */
@Composable
fun AppearancePreview(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val watchNext by vm.watchNext.collectAsStateWithLifecycle()
    val night by vm.night.collectAsStateWithLifecycle()
    val wallpaperImage by vm.wallpaperImage.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()

    val favorites = apps.orEmpty().take(FavoriteCount)
    val focusApp = favorites.firstOrNull { !watchNext[it.packageName].isNullOrEmpty() } ?: favorites.firstOrNull()
    val items = if (account.showContinueWatching) focusApp?.let { watchNext[it.packageName] }.orEmpty() else emptyList()
    val style = HomeStyle.of(account)
    // Como no ecrã inicial: o primeiro conteúdo enche o fundo no destaque e nas apps ao lado, se tiver a forma do ecrã.
    val heroItem = items.firstOrNull()?.takeIf { style.heroBackdrop }
    val backdrop = rememberHeroBackdrop(heroItem)
    val focusStyle = remember(account) { FocusStyle.of(account) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier
            .aspectRatio(16f / 9f)
            .shadow(18.dp, shape)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            // Os cartões do "Continuar a ver" são os do ecrã inicial (focáveis): o foco não entra aqui.
            .focusProperties { onEnter = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        VirtualScreen {
            WallpaperView(account.wallpaper, wallpaperImage)
            HeroBackdropImage(backdrop, visible = backdrop.fullBleed)
            HomeScrims(
                side = { if (heroItem != null) 1f else 0f },
                columnDim = { if (style == HomeStyle.SideColumn && backdrop.fullBleed) 1f else 0f },
                appsDim = { 0f },
            )
            CompositionLocalProvider(
                LocalFocusStyle provides focusStyle,
                LocalIconBackground provides account.iconBackground,
                LocalFocusDim provides remember { FocusDim() },
            ) {
                Column(Modifier.fillMaxSize().padding(TvSafePadding)) {
                    PreviewTopBar(account, weather?.takeIf { account.showWeather }, showApps = style.hasAppsTab)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        @Composable
                        fun tiles(width: Dp = HomeMetrics.TileWidth, label: Boolean = account.showAppName) {
                            favorites.forEach { app ->
                                PreviewTile(vm, app, device.icons[app.packageName], focused = app == focusApp, showLabel = label, width = width)
                            }
                        }
                        HomeBody(
                            style = style,
                            section = { m ->
                                ContinueSection(style, focusApp, items, index = 0, showLogo = !backdrop.fullBleed, actions = SectionActions.None, modifier = m)
                            },
                            favorites = {
                                when (style) {
                                    HomeStyle.AppleTv -> Dock(dockColor(night)) { FavoritesRow { tiles() } }
                                    HomeStyle.SideColumn -> FavoritesColumn { tiles(width = HomeMetrics.ColumnTileWidth, label = false) }
                                    HomeStyle.CardRow, HomeStyle.Shelf, HomeStyle.Spotlight, HomeStyle.Carousel -> FavoritesRow { tiles() }
                                }
                            },
                            // A ajuda "Mantém OK para reordenar" já se apagou no ecrã inicial: só o lugar dela.
                            moveHint = { m -> MoveHintText(s.holdToRearrange, alpha = { 0f }, m) },
                        )
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

/** A barra de cima: saudação, separadores (sem Apps na Apple TV, o Início aberto) e as horas. */
@Composable
private fun PreviewTopBar(account: AccountSettings, weather: WeatherNow?, showApps: Boolean) {
    val s = LocalStrings.current
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        TopGreeting(account.showGreeting, weather, Modifier.align(Alignment.CenterStart))
        Row(
            Modifier.align(Alignment.Center).background(GoatColors.ControlFill, GoatShapes.Bar).padding(BarPadding),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOfNotNull(s.tabHome, s.tabApps.takeIf { showApps }, s.settings).forEachIndexed { i, label ->
                Box(Modifier.background(if (i == 0) Color.White.copy(alpha = 0.2f) else Color.Transparent, GoatShapes.Control)) {
                    TabLabel(label, onPill = i == 0, barFocused = false, selected = i == 0)
                }
            }
        }
        TopClock(account.clock24h, Modifier.align(Alignment.CenterEnd))
    }
}

/** Um favorito, como no ecrã inicial; o que tem foco aparece aumentado e com o efeito de foco. */
@Composable
private fun PreviewTile(vm: LauncherViewModel, app: LauncherApp, iconSpec: String?, focused: Boolean, showLabel: Boolean, width: Dp) {
    // O ícone escolhido carrega como no ecrã inicial (até lá, só a cor da app).
    val custom by produceState(iconSpec?.let { vm.icons.cached(it) }, iconSpec) { value = iconSpec?.let { vm.icons.load(it) } }
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, width * 9 / 16)
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
