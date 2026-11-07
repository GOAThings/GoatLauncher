package cc.slokker.goatlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.glass
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import cc.slokker.goatlauncher.ui.theme.ShelfTitleStyle
import cc.slokker.goatlauncher.ui.theme.tracking
import coil3.compose.AsyncImage

/*
 * As disposições "Destaque e a seguir", "Apps ao lado" e "Carrossel" (Definições → Aparência). O ecrã inicial
 * (HomeScreen) decide qual mostra; aqui ficam as partes só delas, e o texto do destaque, que todas usam.
 */

/**
 * O texto do destaque: a app, o título, "T2 E1 · faltam 6 min" (ou o programa de um canal) e o progresso.
 * [showLogo]: a imagem não enche o ecrã (ex.: logo de um canal), por isso aparece pequena por cima.
 */
@Composable
fun HeroText(app: LauncherApp, item: WatchNextItem, showLogo: Boolean, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(modifier) {
        // Sem imagem de fundo (ex.: logo de um canal): o logo aparece num cartão, como na fila de cartões.
        if (showLogo && item.imageUri != null) {
            Box(
                Modifier
                    .padding(bottom = 16.dp)
                    .size(width = 176.dp, height = 99.dp)
                    .background(Color(0xFF1C2333), GoatShapes.Control)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = item.imageUri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            (if (item.type == WatchNextItem.TYPE_RECOMMENDED) s.shelfRecommended(app.label) else s.shelfLabel(app.label)).uppercase(s.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = tracking(2f)),
            color = GoatColors.TextDim,
        )
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = ShelfTitleStyle.copy(letterSpacing = tracking(-1.2f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val details = listOfNotNull(
            s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() },
            item.episodeTitle?.takeIf { it != item.title },
            item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
            item.onAir(System.currentTimeMillis())?.title,
        ).joinToString(" · ")
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                details,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = GoatColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.progress?.let { p ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(320.dp).height(5.dp).background(Color.White.copy(alpha = 0.3f), GoatShapes.Small)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(GoatColors.FocusRing, GoatShapes.Small))
            }
        }
        OnAirLine(item, Modifier.padding(top = 12.dp))
    }
}

/**
 * Ao lado do destaque: os conteúdos seguintes da mesma app (até três), por ordem. Só mostra; ‹ › no destaque é
 * que passa por eles. [more] conta os que não cabem.
 */
@Composable
fun UpNextPanel(items: List<WatchNextItem>, more: Int, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    if (items.isEmpty()) return
    Column(
        modifier
            .width(UpNextWidth)
            .glass(GoatShapes.Panel, tint = Color(0x99111522))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            (if (more > 0) "${s.upNextTitle} · ${s.shelfMore(more)}" else s.upNextTitle).uppercase(s.locale),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = tracking(1.6f)),
            color = GoatColors.TextFaint,
        )
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(item, Modifier.size(width = 96.dp, height = 54.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(
                        s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() } ?: item.episodeTitle,
                        item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
                        item.onAir(System.currentTimeMillis())?.title,
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(details, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private val UpNextWidth = 300.dp

/** A imagem pequena de um conteúdo: a cena a encher, ou o logo de um canal inteiro sobre fundo escuro. */
@Composable
private fun Thumb(item: WatchNextItem, modifier: Modifier) {
    Box(modifier.clip(GoatShapes.Small).background(Color(0xFF1C2333)), contentAlignment = Alignment.Center) {
        if (item.imageUri != null) {
            AsyncImage(
                model = item.imageUri,
                contentDescription = null,
                contentScale = if (item.isChannel) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (item.isChannel) Modifier.padding(8.dp) else Modifier),
            )
        }
    }
}

/**
 * O carrossel: o conteúdo [index] da app ao centro, em grande, e os vizinhos mais pequenos dos lados. Recebe o
 * foco (↑ a partir dos favoritos): ‹ › anda, OK continua.
 */
@Composable
fun CarouselShelf(
    app: LauncherApp,
    items: List<WatchNextItem>,
    index: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val item = items.getOrNull(index) ?: return
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.DirectionLeft -> {
                        if (e.type == KeyEventType.KeyDown) onPrev()
                        true
                    }
                    Key.DirectionRight -> {
                        if (e.type == KeyEventType.KeyDown) onNext()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (e.type == KeyEventType.KeyUp) onOpen()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        val height = maxHeight.coerceAtMost(CarouselMax)
        val width = height * 16 / 9
        val side = height * 0.72f
        val gap = 28.dp
        // Os vizinhos: metade de fora, mais pequenos e escurecidos.
        items.getOrNull(index - 1)?.let {
            CarouselCard(it, side, Modifier.offset(x = -(width / 2 + gap + side * 16 / 9 / 2)).graphicsLayer { alpha = 0.45f })
        }
        items.getOrNull(index + 1)?.let {
            CarouselCard(it, side, Modifier.offset(x = width / 2 + gap + side * 16 / 9 / 2).graphicsLayer { alpha = 0.45f })
        }
        Box(
            Modifier
                .size(width = width, height = height)
                .then(if (focused) Modifier.border(4.dp, GoatColors.FocusRing, CarouselShape) else Modifier)
                .clip(CarouselShape),
        ) {
            CarouselCard(item, height, Modifier)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 18.dp)) {
                Text(
                    s.shelfLabel(app.label).uppercase(s.locale),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = tracking(1.6f)),
                    color = GoatColors.TextDim,
                )
                Text(
                    item.title,
                    style = ShelfTitleStyle.copy(fontSize = ShelfTitleStyle.fontSize * 0.7f, lineHeight = ShelfTitleStyle.lineHeight * 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() },
                    item.episodeTitle?.takeIf { it != item.title },
                    item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
                    item.onAir(System.currentTimeMillis())?.title,
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal), color = GoatColors.TextDim, maxLines = 1)
                }
                item.progress?.let { p ->
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(width / 2).height(4.dp).background(Color.White.copy(alpha = 0.3f), GoatShapes.Small)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(GoatColors.FocusRing, GoatShapes.Small))
                    }
                }
                OnAirLine(item, Modifier.padding(top = 8.dp))
            }
        }
    }
}

private val CarouselMax = 300.dp
private val CarouselShape = RoundedCornerShape(18.dp)

/** Um cartão do carrossel: a imagem 16:9 com [height] de altura (os logos dos canais inteiros, ao centro). */
@Composable
private fun CarouselCard(item: WatchNextItem, height: Dp, modifier: Modifier) {
    Box(modifier.size(width = height * 16 / 9, height = height).clip(CarouselShape).background(Color(0xFF1C2333)), contentAlignment = Alignment.Center) {
        if (item.imageUri != null) {
            AsyncImage(
                model = item.imageUri,
                contentDescription = null,
                contentScale = if (item.isChannel) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (item.isChannel) Modifier.padding(height / 5) else Modifier),
            )
        }
    }
}
