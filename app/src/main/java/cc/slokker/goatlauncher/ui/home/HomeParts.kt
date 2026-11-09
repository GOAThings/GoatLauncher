package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.ui.theme.tracking
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.Strings
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.text.SimpleDateFormat
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Calendar
import kotlin.math.abs
import cc.slokker.goatlauncher.ui.theme.tabular
val CardHeight = 88.dp
private val CardShape = GoatShapes.Control

// ---- Relógio ----

/** Relógio de 24 horas (Definições), para as horas que aparecem fora da barra de cima. */
val LocalClock24h = staticCompositionLocalOf { true }

/** As horas de [time] no formato das Definições e da língua. */
@Composable
fun clockText(time: Long): String = clockText(time, LocalStrings.current, LocalClock24h.current)

/** As horas de [time] como no relógio de cima: "07:31" com 24 horas, senão o formato da língua ("7:31 PM", "午後7:31"). */
fun clockText(time: Long, s: Strings, clock24h: Boolean): String = SimpleDateFormat(timePattern(s, clock24h), s.locale).format(time)

/** Em cima à direita: as horas. */
@Composable
fun TopClock(clock24h: Boolean, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val now = currentMinute()
    Text(
        SimpleDateFormat(timePattern(s, clock24h), s.locale).format(now),
        modifier,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium).tabular,
    )
}

/**
 * Em cima à esquerda: "BOA NOITE" e, por baixo, "Terça, 20 de outubro · ☁ 14°"
 * (a saudação e o tempo desligam-se nas definições).
 */
@Composable
fun TopGreeting(showGreeting: Boolean, weather: WeatherNow?, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val now = currentMinute()
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> s.goodMorning
        in 12..19 -> s.goodAfternoon
        else -> s.goodEvening
    }
    Column(modifier) {
        if (showGreeting) {
            Text(greeting.uppercase(s.locale), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = tracking(1.6f)), color = GoatColors.TextDim)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(now, s), style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim, maxLines = 1)
            if (weather != null) {
                Text(" · ", style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim)
                Icon(weatherIcon(weather), contentDescription = null, tint = GoatColors.TextDim, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("${weather.temperature}°", style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim)
            }
        }
    }
}

internal fun weatherIcon(w: WeatherNow) = when (w.kind) {
    WeatherNow.Kind.CLEAR, WeatherNow.Kind.PARTLY -> if (w.isDay) GoatIcons.Sun else GoatIcons.Moon
    WeatherNow.Kind.RAIN -> GoatIcons.Rain
    WeatherNow.Kind.SNOW -> GoatIcons.Snow
    WeatherNow.Kind.STORM -> GoatIcons.Storm
    else -> GoatIcons.Cloud
}

/** "Segunda, 20 de outubro" / "Monday, 20 October" / "10月20日星期一": o formato de cada língua. */
internal fun formatDate(time: Long, s: Strings): String {
    val pattern = DateFormat.getBestDateTimePattern(s.locale, "EEEEdMMMM")
    val text = SimpleDateFormat(pattern, s.locale).format(time)
    // Em português, "segunda" chega (sem o "-feira"), e com maiúscula.
    return if (s.locale.language == "pt") text.replace("-feira", "").replaceFirstChar { it.titlecase(s.locale) } else text
}

/** As horas no formato da língua ("21:05", "9:05 PM", "午後9:05"…). */
internal fun timePattern(s: Strings, clock24h: Boolean, amPm: Boolean = true): String =
    if (clock24h) "HH:mm" else DateFormat.getBestDateTimePattern(s.locale, if (amPm) "hm" else "h:mm").let { if (amPm) it else it.replace(Regex("\\s*a\\s*"), "") }

// ---- Continuar a ver ----

/** Um cartão do Watch Next: imagem com barra de progresso, título e "T2 E5 · faltam 20 min". */
@Composable
fun WatchNextCard(item: WatchNextItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val width = CardHeight * item.aspectRatio.coerceIn(0.6f, 2f)
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(CardHeight),
            shape = ClickableSurfaceDefaults.shape(CardShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = GoatColors.CardSurface,
                focusedContainerColor = GoatColors.CardSurface,
                pressedContainerColor = GoatColors.CardSurface,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, GoatColors.FocusRing), inset = 0.dp, shape = CardShape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 12.dp)),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (item.imageUri != null) {
                    // Imagem com a forma do cartão: enche-o. Outra forma (ex.: o logo quadrado de um canal da GoatTV
                    // publicado como 16:9): aparece inteira, ao centro, em vez de cortada e ampliada.
                    val painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current).data(item.imageUri).crossfade(true).build(),
                    )
                    val state by painter.state.collectAsState()
                    val size = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
                    // Logo de um canal: sempre inteiro, mesmo que tenha a forma do cartão.
                    val matches = !item.isChannel && (
                        size == null || size.width <= 0f || size.height <= 0f ||
                            abs(size.width / size.height - item.aspectRatio) / item.aspectRatio < 0.12f
                        )
                    Image(
                        painter = painter,
                        contentDescription = item.title,
                        contentScale = if (matches) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().then(if (matches) Modifier else Modifier.padding(12.dp)),
                    )
                } else {
                    Icon(GoatIcons.Play, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(32.dp))
                }
                item.progress?.let { p ->
                    ProgressBar(p, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp).height(4.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val details = detailsLine(item, s, currentMinute(), DetailsStyle.Card)
        if (details.isNotEmpty()) {
            Text(details, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Cartão grande da disposição Apple TV (como o top shelf do tvOS): a imagem 16:9, com a barra de progresso
 * por dentro, em baixo. O título só aparece por baixo do cartão com foco.
 */
@Composable
fun TopShelfCard(item: WatchNextItem, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(height * 16 / 9)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(height).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(TopShelfShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = GoatColors.CardSurface,
                focusedContainerColor = GoatColors.CardSurface,
                pressedContainerColor = GoatColors.CardSurface,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, GoatColors.FocusRing), inset = 0.dp, shape = TopShelfShape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 16.dp)),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (item.imageUri != null) {
                    val painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current).data(item.imageUri).crossfade(true).build(),
                    )
                    val state by painter.state.collectAsState()
                    val size = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
                    // Só as imagens deitadas enchem o cartão; logos de canais e cartazes ao alto aparecem inteiros.
                    val fill = !item.isChannel && (size == null || size.height <= 0f || size.width / size.height > 1.4f)
                    Image(
                        painter = painter,
                        contentDescription = item.title,
                        contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().then(if (fill) Modifier else Modifier.padding(height / 6)),
                    )
                } else {
                    Icon(GoatIcons.Play, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(40.dp))
                }
                item.progress?.let { p ->
                    // Escurece só o fundo da imagem, para a barra se ver sobre imagens claras.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(height / 4)
                            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))),
                    )
                    ProgressBar(p, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp).height(4.dp))
                }
            }
        }
        val titleAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "title")
        val details = detailsParts(item, s, currentMinute(), DetailsStyle.TopShelf)
        Text(
            (listOf(item.title) + details).joinToString(" · "),
            Modifier.padding(start = 4.dp, top = 12.dp).graphicsLayer { alpha = titleAlpha },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OnAirLine(item, Modifier.padding(start = 4.dp, top = 8.dp).graphicsLayer { alpha = titleAlpha })
    }
}

private val TopShelfShape = RoundedCornerShape(14.dp)

/**
 * Um canal em direto (GoatTV): a parte já dada do programa e "A seguir: … · 22:00". Nada se não houver programa.
 * A hora muda ao minuto, por isso a barra e o programa acompanham sem a app ter de voltar a publicar.
 */
@Composable
fun OnAirLine(item: WatchNextItem, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val air = item.onAir(currentMinute()) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        air.progress?.let { p ->
            ProgressBar(p, Modifier.width(96.dp).height(4.dp), trackAlpha = 0.3f)
        }
        air.next?.let { next ->
            // Um título comprido corta-se, mas a hora fica sempre.
            Row(Modifier.weight(1f, fill = false)) {
                Text(
                    s.upNext(next),
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = GoatColors.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                air.endsAt?.let { Text(" · " + clockText(it), style = MaterialTheme.typography.bodySmall.tabular, color = GoatColors.TextDim, maxLines = 1) }
            }
        }
    }
}

/** Espaço entre os elementos das filas (apps e cartões). */
val RowGap = 24.dp

val RowSpacing = Arrangement.spacedBy(RowGap)
