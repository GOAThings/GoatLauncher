package cc.slokker.goatlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.ui.Strings
import cc.slokker.goatlauncher.ui.theme.GoatColors
import kotlinx.coroutines.delay

/*
 * O que todos os cartões do "Continuar a ver" escrevem e desenham da mesma maneira: a linha de detalhes
 * ("T2 E5 · faltam 20 min · o programa a dar"), a barra de progresso e a hora (um relógio ao minuto para o ecrã todo).
 */

/** As três maneiras de escrever os detalhes de um conteúdo, uma por tipo de cartão. */
enum class DetailsStyle {
    /** Cartões pequenos (fila de cartões, "A seguir"): a temporada e o episódio, ou senão o nome do episódio. */
    Card,

    /** Cartões grandes da Apple TV: como [Card], mas o nome do episódio só se não for igual ao título. */
    TopShelf,

    /** O destaque e o carrossel: a temporada e o episódio e também o nome do episódio (se não for o título). */
    Hero,
}

/** As partes dos detalhes de [item] às [now] horas, pela ordem em que se mostram (vazia se não houver nada). */
fun detailsParts(item: WatchNextItem, s: Strings, now: Long, style: DetailsStyle): List<String> {
    val seasonEpisode = s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() }
    val episode = when (style) {
        DetailsStyle.Card -> listOf(seasonEpisode ?: item.episodeTitle)
        DetailsStyle.TopShelf -> listOf(seasonEpisode ?: item.episodeTitle?.takeIf { it != item.title })
        DetailsStyle.Hero -> listOf(seasonEpisode, item.episodeTitle?.takeIf { it != item.title })
    }
    return (
        episode + listOf(
            item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
            // Canal em direto: o programa que está a dar.
            item.onAir(now)?.title,
        )
        ).filterNotNull()
}

/** Os detalhes de [item] numa linha ("T2 E5 · faltam 20 min"); vazia se não houver nada. */
fun detailsLine(item: WatchNextItem, s: Strings, now: Long, style: DetailsStyle): String =
    detailsParts(item, s, now, style).joinToString(" · ")

/** O fundo da barra de progresso (a parte que falta ver). */
private const val TrackAlpha = 0.35f

/**
 * A barra de progresso dos cartões, na cor do anel do foco. O tamanho e o sítio vêm em [modifier].
 * Sem [rounded], a barra é uma cápsula e a parte vista acaba a direito lá dentro; com [rounded], o fundo e a parte
 * vista têm cada um esses cantos (o destaque e o carrossel).
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier,
    trackAlpha: Float = TrackAlpha,
    fill: Color = GoatColors.FocusRing,
    rounded: Shape? = null,
) {
    val track = Color.White.copy(alpha = trackAlpha)
    if (rounded == null) {
        Box(modifier.clip(CircleShape).background(track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(fill))
        }
    } else {
        Box(modifier.background(track, rounded)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(fill, rounded))
        }
    }
}

// ---- A hora ----

/** O relógio ao minuto partilhado (ver [ProvideMinute]); fora dele, cada [currentMinute] tem o seu. */
private val LocalMinute = staticCompositionLocalOf<State<Long>?> { null }

/** Um só relógio ao minuto para tudo o que está dentro de [content] (o ecrã inicial), em vez de um por cartão. */
@Composable
fun ProvideMinute(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinute provides minuteState(), content = content)
}

/**
 * A hora atual, que só muda (e recompõe quem a lê) quando muda o minuto. Lê o relógio de [ProvideMinute]; fora dele
 * (ex.: o protetor de ecrã) usa um seu.
 */
@Composable
fun currentMinute(): Long {
    val shared = LocalMinute.current
    return if (shared != null) shared.value else minuteState().value
}

/** O minuto de agora (em ms). Vê-se a cada segundo para apanhar acertos da hora. */
@Composable
private fun minuteState(): State<Long> = produceState(minuteOf(System.currentTimeMillis())) {
    while (true) {
        delay(1_000)
        val m = minuteOf(System.currentTimeMillis())
        if (m != value) value = m
    }
}

private fun minuteOf(time: Long): Long = time / 60_000 * 60_000
