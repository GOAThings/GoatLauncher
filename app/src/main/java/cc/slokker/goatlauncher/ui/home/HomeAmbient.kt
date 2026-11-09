package cc.slokker.goatlauncher.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.WeatherNow
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
import cc.slokker.goatlauncher.ui.theme.GoatColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
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
/**
 * O modo ambiente (e o protetor de ecrã): [AccountSettings.AMBIENT_PHOTOS] mostra o fundo com as horas, a data,
 * o tempo, o canal que estava a dar ([nowPlaying]), o título da foto ([caption]) e o autor ([credit]); [AccountSettings.AMBIENT_BOUNCE]
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
    credit: String? = null,
) {
    if (style == AccountSettings.AMBIENT_BOUNCE) {
        BouncingDvd(modifier)
        return
    }
    val now = currentMinute()
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
        AmbientBlock(clock24h, weather, nowPlaying, caption, credit, if (end) Alignment.End else Alignment.Start, now)
    }
}

/** As horas em grande e, por baixo, a data com o tempo, o canal a dar, o título da foto e o crédito. */
@Composable
private fun AmbientBlock(
    clock24h: Boolean,
    weather: WeatherNow?,
    nowPlaying: String?,
    caption: String?,
    credit: String?,
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
        // O autor e a licença (Wikimedia) ou o crédito (NASA): muitas fotos pedem atribuição, por isso numa linha só sua.
        if (credit != null) {
            Spacer(Modifier.height(2.dp))
            Text(s.photoCredit(credit), style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            // Só no canto exato: as duas bordas na mesma frame (perto não conta).
            if (isCornerHit(hitX, hitY, x, y, maxX, maxY, tolerance = 0f)) {
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
