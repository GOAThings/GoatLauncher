package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.ui.common.OkKeys
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TopShelfCardMax
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.BoxWithConstraints

private val SafeH = 48.dp

/**
 * O destaque do "Continuar a ver": o conteúdo mais recente do favorito com foco, em grande.
 * Recebe o foco (↑ a partir dos favoritos): ‹ › passa pelos outros conteúdos da app, OK continua.
 * [showLogo]: a imagem não enche o ecrã (ex.: logo de um canal), por isso aparece pequena aqui.
 */
@Composable
internal fun ShelfHero(
    app: LauncherApp,
    item: WatchNextItem,
    count: Int,
    showLogo: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val s = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Para que lado vai a troca de conteúdo (‹ ou ›): o texto entra desse lado.
    var direction by remember { mutableIntStateOf(1) }
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.DirectionLeft -> {
                        if (e.type == KeyEventType.KeyDown) {
                            direction = -1
                            onPrev()
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (e.type == KeyEventType.KeyDown) {
                            direction = 1
                            onNext()
                        }
                        true
                    }
                    in OkKeys -> {
                        if (e.type == KeyEventType.KeyUp) onOpen()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
    ) {
        AnimatedContent(
            targetState = item,
            contentKey = { it.id },
            transitionSpec = {
                (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 8 * direction } + fadeIn(tween(260, delayMillis = 40)))
                    .togetherWith(slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 8 * direction } + fadeOut(tween(160)))
                    .using(SizeTransform(clip = false))
            },
            label = "hero",
        ) { shown -> HeroText(app, shown, showLogo) }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Parece um botão: com foco fica na cor da marca, com o anel do foco (o foco está no destaque inteiro).
            Row(
                Modifier
                    .then(if (focused) Modifier.border(2.dp, GoatColors.FocusRing, GoatShapes.Control) else Modifier)
                    .background(if (focused) GoatColors.Brand else Color.White.copy(alpha = 0.18f), GoatShapes.Control)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GoatIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.continueNow, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            if (count > 1) Text(s.shelfMore(count - 1), style = MaterialTheme.typography.labelMedium, color = GoatColors.TextFaint)
        }
    }
}

/**
 * Como no tvOS, uma fila só anda quando o elemento com foco ia sair dela (com [margin] de folga para o
 * cartão aumentado). O comportamento por defeito na TV centra o foco.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberEdgeScroll(margin: Dp): BringIntoViewSpec {
    val px = with(LocalDensity.current) { margin.toPx() }
    return remember(px) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val before = offset - px
                val after = offset + size + px - containerSize
                return when {
                    before < 0 -> before
                    after > 0 -> after
                    else -> 0f
                }
            }
        }
    }
}

/** Alarga o elemento [horizontal] para cada lado, para lá das margens do pai (ex.: até às bordas do ecrã). */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Disposição Apple TV: o "Continuar a ver" do favorito com foco em cartões grandes, por cima da doca.
 * Os cartões ocupam a altura que sobra (até [TopShelfCardMax]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TopShelfRow(items: List<WatchNextItem>, onOpen: (WatchNextItem) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // O título da secção, as margens da fila (para o cartão aumentado) e o título do cartão com foco.
        // Com canais em direto, mais uma linha por baixo do cartão (o programa seguinte).
        val extra = if (items.any { it.isChannel && it.airing != null }) 24.dp else 0.dp
        val cardHeight = (maxHeight - 74.dp - extra).coerceIn(100.dp, TopShelfCardMax)
        Column {
            Text(
                s.continueWatching,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = GoatColors.TextDim,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberEdgeScroll(SafeH + 8.dp)) {
                LazyRow(
                    // Ao descer de cima ou subir da doca, entra no último cartão visitado, ou no primeiro.
                    modifier = Modifier.bleed(SafeH).focusRestorer(first),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(start = SafeH + 4.dp, end = SafeH + 4.dp, top = 12.dp, bottom = 4.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { i, item ->
                        TopShelfCard(
                            item = item,
                            height = cardHeight,
                            onClick = { onOpen(item) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }
}


/** O estilo "fila de cartões": o "Continuar a ver" do favorito com foco, por cima dos favoritos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContinueWatchingRow(
    app: LauncherApp,
    items: List<WatchNextItem>,
    onOpen: (WatchNextItem) -> Unit,
    /** O cartão [i] ficou com o foco (Apps ao lado: o destaque passa para ele). */
    onFocusItem: (Int) -> Unit = {},
) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    Column {
        Text(s.continueIn(app.label), style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim, modifier = Modifier.padding(start = 4.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberEdgeScroll(SafeH + 8.dp)) {
            LazyRow(
                // Ao subir dos favoritos, entra no último cartão visitado desta app, ou no primeiro.
                // A fila vai até às bordas do ecrã (os cartões passam pela margem em vez de serem cortados nela).
                modifier = Modifier.bleed(SafeH).focusRestorer(first),
                horizontalArrangement = RowSpacing,
                contentPadding = PaddingValues(start = SafeH + 4.dp, end = SafeH + 4.dp, top = 10.dp, bottom = 4.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { i, item ->
                    WatchNextCard(
                        item = item,
                        onClick = { onOpen(item) },
                        modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { if (it.hasFocus) onFocusItem(i) },
                    )
                }
            }
        }
    }
}
