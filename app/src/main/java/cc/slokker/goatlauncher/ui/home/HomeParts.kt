package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.ui.theme.tracking
import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Brush
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
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import cc.slokker.goatlauncher.data.AccountSettings
import kotlin.math.roundToInt

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
        SimpleDateFormat(timePattern(s, clock24h), s.locale).format(now),
        modifier,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * O modo ambiente (e o protetor de ecrã): [AccountSettings.AMBIENT_PHOTOS] mostra o fundo com as horas, a data,
 * o tempo, o canal que estava a dar ([nowPlaying]) e o título da foto ([caption]); [AccountSettings.AMBIENT_BOUNCE]
 * é um ecrã preto com o logótipo DVD a andar e a bater nas bordas, como nos leitores de DVD de antigamente.
 */
@Composable
fun AmbientScene(
    style: String,
    clock24h: Boolean,
    weather: WeatherNow?,
    nowPlaying: String?,
    caption: String?,
    modifier: Modifier = Modifier,
) {
    if (style == AccountSettings.AMBIENT_BOUNCE) {
        BouncingDvd(modifier)
        return
    }
    val now = rememberMinute()
    // Para não marcar o ecrã: de 10 em 10 minutos o bloco muda de lado (e sobe ou desce um pouco).
    val slot = (now / 60_000 / AMBIENT_MOVE_MINUTES % 4).toInt()
    val alignment = if (slot % 2 == 0) Alignment.BottomStart else Alignment.BottomEnd
    val lift = if (slot < 2) 0.dp else 28.dp
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))
            .padding(TvSafePadding)
            .padding(bottom = lift),
        contentAlignment = alignment,
    ) {
        val end = alignment == Alignment.BottomEnd
        AmbientBlock(clock24h, weather, nowPlaying, caption, if (end) Alignment.End else Alignment.Start, now)
    }
}

/** As horas em grande e, por baixo, a data com o tempo, o canal a dar e o título da foto. */
@Composable
private fun AmbientBlock(
    clock24h: Boolean,
    weather: WeatherNow?,
    nowPlaying: String?,
    caption: String?,
    align: Alignment.Horizontal,
    now: Long,
    color: Color = Color.White,
) {
    val s = LocalStrings.current
    Column(horizontalAlignment = align) {
        Text(
            SimpleDateFormat(timePattern(s, clock24h, amPm = false), s.locale).format(now),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light),
            color = color,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(now, s), style = MaterialTheme.typography.titleMedium, color = color.copy(alpha = 0.75f))
            if (weather != null) {
                Text("  ·  ", style = MaterialTheme.typography.titleMedium, color = color.copy(alpha = 0.75f))
                Icon(weatherIcon(weather), contentDescription = null, tint = color.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${weather.temperature}°", style = MaterialTheme.typography.titleMedium, color = color.copy(alpha = 0.75f))
            }
        }
        if (nowPlaying != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GoatIcons.Play, contentDescription = null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(nowPlaying, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(6.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * O logótipo DVD a andar na diagonal e a ressaltar nas bordas (de vez em quando acerta num canto); muda de cor a
 * cada batida. Tudo o resto é preto (um bom protetor de ecrã para TVs OLED).
 */
@Composable
private fun BouncingDvd(modifier: Modifier) {
    val density = LocalDensity.current
    var area by remember { mutableStateOf(IntSize.Zero) }
    var block by remember { mutableStateOf(IntSize.Zero) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    var colorIndex by remember { mutableIntStateOf(0) }
    // Easter egg: quando acerta mesmo num canto (as duas bordas quase ao mesmo tempo), aparece uma cabra lá.
    var goatCorner by remember { mutableStateOf<Alignment?>(null) }
    LaunchedEffect(goatCorner) {
        if (goatCorner != null) {
            delay(GOAT_MS)
            goatCorner = null
        }
    }
    LaunchedEffect(area, block) {
        if (area == IntSize.Zero || block == IntSize.Zero) return@LaunchedEffect
        val maxX = (area.width - block.width).coerceAtLeast(0).toFloat()
        val maxY = (area.height - block.height).coerceAtLeast(0).toFloat()
        val speed = with(density) { BOUNCE_SPEED.toPx() }
        var p = Offset(pos.x.coerceIn(0f, maxX), pos.y.coerceIn(0f, maxY))
        var vx = speed
        var vy = speed * 0.75f
        val cornerPx = with(density) { CORNER_TOLERANCE.toPx() }
        var last = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { it }
            val dt = ((t - last) / 1e9f).coerceAtMost(0.1f)
            last = t
            var x = p.x + vx * dt
            var y = p.y + vy * dt
            val hitX = x <= 0f || x >= maxX
            val hitY = y <= 0f || y >= maxY
            if (hitX) { vx = -vx; x = x.coerceIn(0f, maxX) }
            if (hitY) { vy = -vy; y = y.coerceIn(0f, maxY) }
            if (hitX || hitY) colorIndex = (colorIndex + 1) % BOUNCE_COLORS.size
            if (isCornerHit(hitX, hitY, x, y, maxX, maxY, cornerPx)) {
                goatCorner = when {
                    x < maxX / 2 && y < maxY / 2 -> Alignment.TopStart
                    x < maxX / 2 -> Alignment.BottomStart
                    y < maxY / 2 -> Alignment.TopEnd
                    else -> Alignment.BottomEnd
                }
            }
            p = Offset(x, y)
            pos = p
        }
    }
    Box(modifier.fillMaxSize().background(Color.Black).onSizeChanged { area = it }) {
        Box(
            Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .onSizeChanged { block = it },
        ) {
            DvdLogo(BOUNCE_COLORS[colorIndex])
        }
        goatCorner?.let { corner ->
            // Entra com um salto (cresce além do tamanho e volta) e sai sozinha passado GOAT_MS.
            val pop = remember(corner) { Animatable(0f) }
            LaunchedEffect(corner) { pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
            Text(
                "🐐",
                fontSize = 120.sp,
                modifier = Modifier
                    .align(corner)
                    .padding(36.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
            )
        }
    }
}

/**
 * Um logótipo ao estilo do DVD Video, desenhado aqui (não é a arte oficial da marca): "DVD" a negrito inclinado
 * e, por baixo, um disco achatado com "VIDEO" vazado.
 */
@Composable
private fun DvdLogo(color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            // O espaço fino no fim dá lugar à ponta do último D inclinado (o texto é cortado à largura que mede).
            "DVD\u2009",
            color = color,
            softWrap = false,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 84.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-3).sp,
            ),
        )
        Box(
            Modifier
                .offset(y = (-6).dp)
                .size(width = 210.dp, height = 36.dp)
                .drawBehind { drawOval(color) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "VIDEO",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 6.sp),
            )
        }
    }
}

/** O canal em direto visto há menos tempo no "Continuar a ver" (ex.: o da GoatTV), como "TVI · Jornal da Noite". */
fun nowPlaying(watchNext: Map<String, List<WatchNextItem>>): String? =
    watchNext.values.flatten().filter { it.isChannel }.maxByOrNull { it.lastEngagement }
        ?.let { listOfNotNull(it.title, it.episodeTitle?.takeIf { t -> t.isNotBlank() }).joinToString(" · ") }

/** De quantos em quantos minutos o bloco do modo ambiente muda de lado. */
private const val AMBIENT_MOVE_MINUTES = 10L
private val BOUNCE_SPEED = 70.dp
/** A que distância da outra borda (no momento da batida) ainda conta como acertar no canto. */
private val CORNER_TOLERANCE = 2.dp
private const val GOAT_MS = 3_000L
private val BOUNCE_COLORS = listOf(
    Color.White,
    Color(0xFF7DD3FC),
    Color(0xFFF9A8D4),
    Color(0xFFFCD34D),
    Color(0xFF86EFAC),
    Color(0xFFC4B5FD),
    Color(0xFFFDBA74),
)

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

private fun weatherIcon(w: WeatherNow) = when (w.kind) {
    WeatherNow.Kind.CLEAR, WeatherNow.Kind.PARTLY -> if (w.isDay) GoatIcons.Sun else GoatIcons.Moon
    WeatherNow.Kind.RAIN -> GoatIcons.Rain
    WeatherNow.Kind.SNOW -> GoatIcons.Snow
    WeatherNow.Kind.STORM -> GoatIcons.Storm
    else -> GoatIcons.Cloud
}

/** "Segunda, 20 de outubro" / "Monday, 20 October" / "10月20日星期一": o formato de cada língua. */
private fun formatDate(time: Long, s: Strings): String {
    val pattern = DateFormat.getBestDateTimePattern(s.locale, "EEEEdMMMM")
    val text = SimpleDateFormat(pattern, s.locale).format(time)
    // Em português, "segunda" chega (sem o "-feira"), e com maiúscula.
    return if (s.locale.language == "pt") text.replace("-feira", "").replaceFirstChar { it.titlecase(s.locale) } else text
}

/** As horas no formato da língua ("21:05", "9:05 PM", "午後9:05"…). */
private fun timePattern(s: Strings, clock24h: Boolean, amPm: Boolean = true): String =
    if (clock24h) "HH:mm" else DateFormat.getBestDateTimePattern(s.locale, if (amPm) "hm" else "h:mm").let { if (amPm) it else it.replace(Regex("\\s*a\\s*"), "") }

// ---- Banners das apps ----

/** Um favorito ou uma app da grelha: o banner e, por baixo, o nome (mais claro com foco). */
@Composable
fun AppTile(
    app: LauncherApp,
    width: Dp,
    custom: ImageBitmap?,
    customPending: Boolean,
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
            customPending = customPending,
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
 * O cartão 16:9 de uma app. Com foco fica maior e com o efeito de foco escolhido (por defeito o contorno branco,
 * como no tvOS; ver FocusEffect). Em modo "mover" ([moving]) o contorno passa a ser o gradiente da marca, com setas dos lados.
 */
@Composable
fun AppBanner(
    app: LauncherApp,
    width: Dp,
    /** Ícone escolhido pela pessoa (icon pack ou imagem da box), em vez do banner da app. */
    custom: ImageBitmap?,
    /** O ícone escolhido ainda está a carregar: só a cor da app, sem o banner original. */
    customPending: Boolean = false,
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
    var focused by remember { mutableStateOf(false) }
    val armedScale by animateFloatAsState(if (armed) 0.94f else 1f, tween(120), label = "armed")
    // Ícone transparente com o fundo "transparente" (Aparência): o cartão não tem cor, vê-se o fundo do ecrã.
    val container = if (isSeeThrough(custom)) Color.Transparent else app.color
    // A escala do foco é feita aqui (não pelo Surface) para o efeito de foco crescer com o cartão.
    val focusScale by animateFloatAsState(if (focused) FocusScale else 1f, tween(150), label = "focusScale")
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width, width * 9 / 16)
            .graphicsLayer {
                scaleX = armedScale * focusScale
                scaleY = armedScale * focusScale
            }
            .focusEffect(focused, app.color, moving)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused() else {
                    pressedAt = NOT_PRESSED
                    armed = false
                }
            }
            .onPreviewKeyEvent { e ->
                // Botão Menu do comando: abre logo o menu da app (o mesmo que manter OK).
                if (e.key == Key.Menu) {
                    if (e.type == KeyEventType.KeyUp) onLongClick()
                    return@onPreviewKeyEvent true
                }
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
            containerColor = container,
            focusedContainerColor = container,
            pressedContainerColor = container,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize()) {
            BannerImage(app, custom, customPending)

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

/** O que se vê num cartão de app: o ícone escolhido, o banner, ou o ícone da app sobre a cor dela. */
@Composable
fun BoxScope.BannerImage(app: LauncherApp, custom: ImageBitmap?, customPending: Boolean = false) {
    when {
        custom != null -> CustomIconImage(custom, background = app.color)
        customPending -> Unit
        app.banner != null -> Image(app.banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        app.icon != null -> Image(app.icon, contentDescription = app.label, modifier = Modifier.align(Alignment.Center).size(52.dp))
        else -> Text(app.label, Modifier.align(Alignment.Center).padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                containerColor = Color(0xFF1C2333),
                focusedContainerColor = Color(0xFF1C2333),
                pressedContainerColor = Color(0xFF1C2333),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), inset = 0.dp, shape = TopShelfShape),
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
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White))
                    }
                }
            }
        }
        val titleAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "title")
        val details = listOfNotNull(
            s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() } ?: item.episodeTitle?.takeIf { it != item.title },
            item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
        )
        Text(
            (listOf(item.title) + details).joinToString(" · "),
            Modifier.padding(start = 4.dp, top = 12.dp).graphicsLayer { alpha = titleAlpha },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TopShelfShape = RoundedCornerShape(14.dp)

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
