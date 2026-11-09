package cc.slokker.goatlauncher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.ui.home.HomeMetrics.ColumnTileGap
import cc.slokker.goatlauncher.ui.home.HomeMetrics.DockPadding
import cc.slokker.goatlauncher.ui.home.HomeMetrics.DockShape
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TileGap
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TilesRowWidth
import cc.slokker.goatlauncher.ui.theme.GoatColors
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlin.math.abs

/*
 * O corpo do Início, igual no ecrã inicial e na pré-visualização da Aparência: a disposição de cada estilo
 * (HomeBody), o "Continuar a ver" de cada estilo (ContinueSection), as caixas dos favoritos, o fundo do destaque e os
 * véus. O ecrã inicial põe aqui os banners com foco e as ações; a pré-visualização, banners só desenhados e nenhuma ação.
 */

/** O que as secções do "Continuar a ver" fazem com as teclas. A pré-visualização não faz nada ([None]). */
@Immutable
internal class SectionActions(
    /** OK num conteúdo: continua-o na app. */
    val open: (WatchNextItem) -> Unit = {},
    /** ‹ › no destaque e no carrossel. */
    val prev: () -> Unit = {},
    val next: () -> Unit = {},
    /** O cartão [i] da fila ficou com o foco (apps ao lado: o destaque passa para ele). */
    val focusItem: (Int) -> Unit = {},
    /** ↓ nos cartões da Apple TV: volta à app dona deles. */
    val down: (LauncherApp) -> Unit = {},
) {
    companion object {
        val None = SectionActions()
    }
}

/**
 * A disposição do Início no estilo [style]. [section] é o "Continuar a ver" (recebe o modificador do sítio dele),
 * [favorites] a caixa dos favoritos (fila, coluna ou doca), [moveHint] a ajuda "Mantém OK para reordenar".
 * Apple TV: [scroll] desce para [below] (as outras apps), por baixo do primeiro ecrã.
 */
@Composable
internal fun HomeBody(
    style: HomeStyle,
    section: @Composable (Modifier) -> Unit,
    favorites: @Composable () -> Unit,
    moveHint: @Composable (Modifier) -> Unit,
    scroll: ScrollState? = null,
    below: @Composable () -> Unit = {},
) {
    when (style) {
        // ---- Apple TV: cartões grandes, a doca dos favoritos e, a descer, as outras apps ----
        HomeStyle.AppleTv -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().then(if (scroll != null) Modifier.verticalScroll(scroll) else Modifier)) {
                // O primeiro ecrã: os cartões em cima e a doca em baixo; a grelha começa por baixo dele.
                Column(Modifier.fillMaxWidth().height(viewport), horizontalAlignment = Alignment.CenterHorizontally) {
                    section(Modifier.weight(1f).fillMaxWidth())
                    moveHint(Modifier.padding(bottom = 8.dp))
                    favorites()
                }
                below()
            }
        }
        // ---- Apps ao lado: os favoritos à esquerda, o que a app com foco tem à direita ----
        HomeStyle.SideColumn -> Row(Modifier.fillMaxSize().padding(top = 12.dp)) {
            favorites()
            Spacer(Modifier.width(48.dp))
            section(Modifier.weight(1f).fillMaxHeight())
        }
        // ---- O carrossel ocupa o espaço por cima dos favoritos ----
        HomeStyle.Carousel -> Column(Modifier.fillMaxSize()) {
            section(Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            FavoritesBlock(favorites, moveHint)
        }
        HomeStyle.Shelf, HomeStyle.Spotlight -> Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            section(Modifier)
            Spacer(Modifier.height(28.dp))
            FavoritesBlock(favorites, moveHint)
        }
        HomeStyle.CardRow -> Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            section(Modifier)
            Spacer(Modifier.height(12.dp))
            FavoritesBlock(favorites, moveHint)
        }
    }
}

/** A fila dos favoritos com a ajuda por cima (sem título). */
@Composable
private fun FavoritesBlock(favorites: @Composable () -> Unit, moveHint: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        moveHint(Modifier)
        Spacer(Modifier.height(12.dp))
        favorites()
    }
}

/** A ajuda "Mantém OK para reordenar" ([text]), alinhada com a fila; [alpha] lê-se só ao desenhar. */
@Composable
internal fun MoveHintText(text: String, alpha: () -> Float, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.width(TilesRowWidth).graphicsLayer { this.alpha = alpha() },
        style = MaterialTheme.typography.labelSmall,
        color = GoatColors.TextFaint,
        textAlign = TextAlign.End,
    )
}

/** Os favoritos numa fila, à largura de cinco banners. */
@Composable
internal fun FavoritesRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.width(TilesRowWidth),
        horizontalArrangement = Arrangement.spacedBy(TileGap, Alignment.CenterHorizontally),
        content = content,
    )
}

/** Apps ao lado: os favoritos numa coluna, mais pequenos e sem nome (a app com foco diz-se à direita). */
@Composable
internal fun FavoritesColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(ColumnTileGap, Alignment.CenterVertically),
        content = content,
    )
}

/**
 * A doca da Apple TV: só os 5 favoritos, numa barra de vidro ([tint]) à medida deles, com folga para o banner
 * aumentado das pontas (pode passar um pouco das margens).
 */
@Composable
internal fun Dock(tint: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .requiredWidth(TilesRowWidth + DockPadding * 2)
            .background(tint, DockShape)
            .padding(horizontal = DockPadding, vertical = 18.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * O "Continuar a ver" de [app] no estilo [style]: os [items], com o [index] em destaque. Entre apps troca com um fade
 * (o estilo de cada um). [showLogo]: o conteúdo não enche o fundo, por isso o logo aparece no texto do destaque.
 */
@Composable
internal fun ContinueSection(
    style: HomeStyle,
    app: LauncherApp?,
    items: List<WatchNextItem>,
    index: Int,
    showLogo: Boolean,
    actions: SectionActions,
    modifier: Modifier = Modifier,
) {
    val current = app?.takeIf { items.isNotEmpty() }
    when (style) {
        HomeStyle.AppleTv -> AnimatedContent(
            targetState = current?.let { it to items },
            contentKey = { it?.first?.packageName },
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            modifier = modifier,
            label = "topShelf",
        ) { section ->
            if (section != null) {
                // ↓ volta à app dona dos cartões (não à que estiver por baixo deles), como no tvOS.
                TopShelfRow(
                    section.second,
                    onOpen = actions.open,
                    modifier = Modifier.onPreviewKeyEvent { e ->
                        if (e.key != Key.DirectionDown) return@onPreviewKeyEvent false
                        if (e.type == KeyEventType.KeyDown) actions.down(section.first)
                        true
                    },
                )
            } else {
                Spacer(Modifier.fillMaxSize())
            }
        }
        HomeStyle.SideColumn -> AnimatedContent(
            targetState = current?.let { it to items },
            contentKey = { it?.first?.packageName },
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            modifier = modifier,
            label = "side",
        ) { section ->
            if (section != null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    // O destaque é o cartão com foco (o primeiro, enquanto o foco está nas apps).
                    val hero = section.second.getOrNull(index) ?: section.second.first()
                    AnimatedContent(
                        targetState = hero,
                        contentKey = { it.id },
                        transitionSpec = {
                            (fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(140)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "sideHero",
                    ) { shown -> HeroText(section.first, shown, showLogo = showLogo) }
                    Spacer(Modifier.height(24.dp))
                    ContinueWatchingRow(section.first, section.second, onOpen = actions.open, onFocusItem = actions.focusItem)
                }
            } else {
                Spacer(Modifier.fillMaxSize())
            }
        }
        HomeStyle.Carousel -> AnimatedContent(
            targetState = current,
            contentKey = { it?.packageName },
            transitionSpec = { fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(140)) },
            modifier = modifier,
            label = "carousel",
        ) { shown ->
            // A app que está a sair não desenha os conteúdos da que entra (os [items] já são os novos).
            if (shown != null && items.firstOrNull()?.packageName == shown.packageName) {
                CarouselShelf(
                    app = shown,
                    items = items,
                    index = index,
                    onPrev = actions.prev,
                    onNext = actions.next,
                    onOpen = { items.getOrNull(index)?.let(actions.open) },
                    modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
                )
            } else {
                Spacer(Modifier.fillMaxSize())
            }
        }
        HomeStyle.Shelf, HomeStyle.Spotlight -> AnimatedContent(
            targetState = current,
            contentKey = { it?.packageName },
            transitionSpec = { fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(140)) },
            modifier = modifier,
            label = "shelf",
        ) { shown ->
            val item = items.getOrNull(index)
            if (shown != null && item != null && item.packageName == shown.packageName) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    ShelfHero(
                        app = shown,
                        item = item,
                        count = items.size,
                        showLogo = showLogo,
                        onPrev = actions.prev,
                        onNext = actions.next,
                        onOpen = { actions.open(item) },
                    )
                    if (style == HomeStyle.Spotlight && item.isChannel) {
                        // Um canal: o que vai dar nele, não os outros canais da app.
                        Spacer(Modifier.weight(1f))
                        ChannelUpNextPanel(item)
                    } else if (style == HomeStyle.Spotlight) {
                        // Destaque e a seguir: os que vêm depois deste (e, no fim da lista, os do princípio).
                        val next = (items.drop(index + 1) + items.take(index)).take(UP_NEXT_SHOWN)
                        Spacer(Modifier.weight(1f))
                        UpNextPanel(next, more = items.size - 1 - next.size)
                    }
                }
            } else {
                Spacer(Modifier.fillMaxWidth())
            }
        }
        // Entre favoritos, a secção troca com um fade (e a altura acompanha), em vez de saltar.
        HomeStyle.CardRow -> AnimatedContent(
            targetState = current?.let { it to items },
            contentKey = { it?.first?.packageName },
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) })
            },
            modifier = modifier,
            label = "continue",
        ) { section ->
            if (section != null) {
                ContinueWatchingRow(section.first, section.second, onOpen = actions.open)
            } else {
                Spacer(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Quantos conteúdos seguintes mostra o painel da "Destaque e a seguir". */
private const val UP_NEXT_SHOWN = 3

/** Se a imagem do conteúdo em destaque enche o fundo e, quando já carregou, a imagem. */
@Immutable
internal class HeroBackdrop(val fullBleed: Boolean, val loaded: Painter?)

/**
 * O fundo do destaque: a imagem de [item], se tiver a forma do ecrã (16:9). Os logos quadrados (ex.: canais da
 * GoatTV) não enchem o ecrã: fica o fundo normal e o logo aparece pequeno no texto.
 */
@Composable
internal fun rememberHeroBackdrop(item: WatchNextItem?): HeroBackdrop {
    val painter = rememberAsyncImagePainter(ImageRequest.Builder(LocalContext.current).data(item?.imageUri).build())
    val state by painter.state.collectAsState()
    val size = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
    // Enquanto a imagem do conteúdo seguinte carrega, vale a decisão do anterior: o fundo não cai para o papel de
    // parede e o logo pequeno não aparece por um instante (o texto saltava).
    val pending = state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty
    var lastFullBleed by remember { mutableStateOf(false) }
    // Os canais em direto (ex.: GoatTV) só têm o logo: nunca enchem o ecrã, mesmo que o logo seja largo.
    val fullBleed = item != null && !item.isChannel && item.imageUri != null && (
        if (pending) lastFullBleed
        else size != null && size.height > 0f && abs(size.width / size.height - 16f / 9f) < 0.25f
        )
    if (!pending) lastFullBleed = fullBleed
    return HeroBackdrop(fullBleed, (state as? AsyncImagePainter.State.Success)?.painter)
}

/**
 * Desenha o fundo do destaque ([visible]). Mostra o último conteúdo que enche o ecrã: ao sair (fade), fica nessa
 * imagem, em vez de mostrar por um instante, em ecrã inteiro, o logo do canal para onde se foi. Só muda para a imagem
 * nova quando ela já carregou, e passa de uma para a outra com um crossfade.
 */
@Composable
internal fun HeroBackdropImage(backdrop: HeroBackdrop, visible: Boolean) {
    var shown by remember { mutableStateOf<Painter?>(null) }
    if (backdrop.fullBleed && backdrop.loaded != null) shown = backdrop.loaded
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
        Crossfade(targetState = shown, animationSpec = tween(450), label = "backdrop") { painter ->
            if (painter != null) {
                Image(painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Os véus por cima do fundo, para o texto se ler sobre qualquer imagem: em cima e em baixo (sempre), à esquerda
 * ([side], com destaque), por cima de tudo ([columnDim], apps ao lado sobre uma imagem) e o das Apps e Definições
 * ([appsDim]). Os valores leem-se só ao desenhar (animá-los não volta a compor o ecrã).
 */
@Composable
internal fun HomeScrims(side: () -> Float, columnDim: () -> Float, appsDim: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.25f to Color.Transparent,
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.78f),
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = side() }
            .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.45f to Color.Black.copy(alpha = 0.35f), 0.75f to Color.Transparent)),
    )
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = columnDim() }.background(Color.Black.copy(alpha = 0.45f)))
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appsDim() }.background(Color.Black.copy(alpha = 0.6f)))
}
