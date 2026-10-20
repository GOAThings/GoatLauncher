package cc.slokker.goatlauncher.ui.home

import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.abs

private val BannerShape = GoatShapes.Control
val CardHeight = 88.dp
private val CardShape = GoatShapes.Control

// ---- Relógio ----

/** A hora atual, que só muda (e recompõe) quando muda o minuto. Vê-se a cada segundo para apanhar acertos da hora. */
@Composable
private fun rememberMinute(): Long {
    val minute by produceState(System.currentTimeMillis() / 60_000) {
        while (true) {
            delay(1_000)
            val m = System.currentTimeMillis() / 60_000
            if (m != value) value = m
        }
    }
    return minute * 60_000
}

/** Em cima à direita: as horas. */
@Composable
fun TopClock(clock24h: Boolean, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val now = rememberMinute()
    Text(
        SimpleDateFormat(if (clock24h) "HH:mm" else "h:mm a", s.locale).format(now),
        modifier,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * Em cima à esquerda: "BOA NOITE" e, por baixo, "Terça, 20 de outubro · ☁ 14°"
 * (a saudação e o tempo desligam-se nas definições).
 */
@Composable
fun TopGreeting(showGreeting: Boolean, weather: WeatherNow?, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val now = rememberMinute()
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> s.goodMorning
        in 12..19 -> s.goodAfternoon
        else -> s.goodEvening
    }
    Column(modifier) {
        if (showGreeting) {
            Text(greeting.uppercase(s.locale), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp), color = GoatColors.TextDim)
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

private fun weatherIcon(w: WeatherNow) = when (w.kind) {
    WeatherNow.Kind.CLEAR, WeatherNow.Kind.PARTLY -> if (w.isDay) GoatIcons.Sun else GoatIcons.Moon
    WeatherNow.Kind.RAIN -> GoatIcons.Rain
    WeatherNow.Kind.SNOW -> GoatIcons.Snow
    WeatherNow.Kind.STORM -> GoatIcons.Storm
    else -> GoatIcons.Cloud
}

/** "Segunda, 20 de outubro" / "Monday, 20 October". */
private fun formatDate(time: Long, s: Strings): String =
    if (s.english) {
        SimpleDateFormat("EEEE, d MMMM", s.locale).format(time)
    } else {
        SimpleDateFormat("EEEE, d 'de' MMMM", s.locale).format(time)
            .replace("-feira", "")
            .replaceFirstChar { it.titlecase(s.locale) }
    }

// ---- Banners das apps ----

/** Um favorito ou uma app da grelha: o banner e, por baixo, o nome (mais claro com foco). */
@Composable
fun AppTile(
    app: LauncherApp,
    width: Dp,
    custom: ImageBitmap?,
    showLabel: Boolean,
    hasContinue: Boolean,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    bannerModifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBanner(
            app = app,
            width = width,
            custom = custom,
            hasContinue = hasContinue,
            moving = moving,
            onFocused = onFocused,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = bannerModifier.onFocusChanged { focused = it.isFocused },
        )
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

/**
 * O cartão 16:9 de uma app. Com foco fica maior e com contorno branco, como no tvOS.
 * Em modo "mover" ([moving]) o contorno passa a ser o gradiente da marca, com setas dos lados.
 */
@Composable
fun AppBanner(
    app: LauncherApp,
    width: Dp,
    /** Ícone escolhido pela pessoa (icon pack ou imagem da box), em vez do banner da app. */
    custom: ImageBitmap?,
    hasContinue: Boolean,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // OK tratado aqui (não pelo Surface): a pressão longa só abre o menu ao largar o botão. Assim o "largar"
    // não cai no menu (escolhendo "Mover" sem querer) nem deixa o banner preso no estado "carregado".
    var pressedAt by remember { mutableLongStateOf(NOT_PRESSED) }
    var armed by remember { mutableStateOf(false) }
    val armedScale by animateFloatAsState(if (armed) 0.94f else 1f, tween(120), label = "armed")
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width, width * 9 / 16)
            .graphicsLayer {
                scaleX = armedScale
                scaleY = armedScale
            }
            .onFocusChanged {
                if (it.isFocused) onFocused() else {
                    pressedAt = NOT_PRESSED
                    armed = false
                }
            }
            .onPreviewKeyEvent { e ->
                if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                val native = e.nativeKeyEvent
                when (e.type) {
                    KeyEventType.KeyDown -> when {
                        native.repeatCount == 0 -> {
                            pressedAt = native.eventTime
                            armed = false
                        }
                        // A pressão longa fica "armada" (o banner encolhe um pouco) enquanto o botão está em baixo.
                        pressedAt != NOT_PRESSED && (native.isLongPress || native.eventTime - pressedAt >= LONG_PRESS_MS) -> armed = true
                    }
                    KeyEventType.KeyUp -> {
                        // Largar sem ter carregado aqui (o foco chegou com o botão em baixo) não faz nada.
                        if (pressedAt != NOT_PRESSED) {
                            if (armed || native.eventTime - pressedAt >= LONG_PRESS_MS) onLongClick() else onClick()
                        }
                        pressedAt = NOT_PRESSED
                        armed = false
                    }
                }
                true
            },
        shape = ClickableSurfaceDefaults.shape(BannerShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = app.color,
            focusedContainerColor = app.color,
            pressedContainerColor = app.color,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                if (moving) BorderStroke(3.dp, GoatColors.Accent) else BorderStroke(3.dp, Color.White),
                inset = 0.dp,
                shape = BannerShape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 14.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                custom != null -> CustomIconImage(custom, background = app.color)
                app.banner != null -> Image(app.banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                app.icon != null -> Image(app.icon, contentDescription = app.label, modifier = Modifier.align(Alignment.Center).size(52.dp))
                else -> Text(app.label, Modifier.align(Alignment.Center).padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            if (hasContinue) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Color.White, CircleShape),
                )
            }
            if (moving) {
                MoveArrow(Modifier.align(Alignment.CenterStart), left = true)
                MoveArrow(Modifier.align(Alignment.CenterEnd), left = false)
            }
        }
    }
}

@Composable
private fun MoveArrow(modifier: Modifier, left: Boolean) {
    Box(
        modifier.padding(4.dp).size(22.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (left) GoatIcons.ChevronLeft else GoatIcons.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

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
                containerColor = Color(0xFF1C2333),
                focusedContainerColor = Color(0xFF1C2333),
                pressedContainerColor = Color(0xFF1C2333),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), inset = 0.dp, shape = CardShape),
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
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val details = listOfNotNull(
            s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() } ?: item.episodeTitle,
            item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
        ).joinToString(" · ")
        if (details.isNotEmpty()) {
            Text(details, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Cartão do estilo "Down Under": imagem 16:9, a barra de progresso por baixo dela, o título e, numa linha,
 * o ícone da app com "T1 E3 · faltam 32 min".
 */
@Composable
fun WideWatchCard(item: WatchNextItem, app: LauncherApp?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(modifier.width(WideCardWidth)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(CardShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1C2333),
                focusedContainerColor = Color(0xFF1C2333),
                pressedContainerColor = Color(0xFF1C2333),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), inset = 0.dp, shape = CardShape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 12.dp)),
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
                        modifier = Modifier.fillMaxSize().then(if (fill) Modifier else Modifier.padding(12.dp)),
                    )
                } else {
                    Icon(GoatIcons.Play, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(28.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = if (item.progress != null) 0.25f else 0f), GoatShapes.Small)) {
            item.progress?.let { p -> Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White, GoatShapes.Small)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            app?.smallIcon?.let {
                Image(it, contentDescription = app.label, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
            }
            val details = listOfNotNull(
                s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() } ?: item.episodeTitle,
                item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
            ).joinToString(" · ").ifEmpty { app?.label.orEmpty() }
            Text(details, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val WideCardWidth = 168.dp

/** Aviso curto no topo do ecrã. */
@Composable
fun ToastPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 27.dp)
            .shadow(8.dp, GoatShapes.Control)
            .background(Color(0xE61C2333), GoatShapes.Control)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Espaço entre os elementos das filas (apps e cartões). */
val RowGap = 24.dp

private const val NOT_PRESSED = -1L
private val LONG_PRESS_MS = ViewConfiguration.getLongPressTimeout().toLong()
val RowSpacing = Arrangement.spacedBy(RowGap)
