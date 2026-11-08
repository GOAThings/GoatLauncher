package cc.slokker.goatlauncher.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import cc.slokker.goatlauncher.ui.theme.ControlRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * O botão de dia/noite da barra de cima, ao jeito do "Glass Light Toggle": uma calha com um botão de vidro quadrado
 * arredondado; um arco de luz contorna-lhe os cantos de baixo à noite (azul) e os de cima de dia (âmbar), e dá a
 * volta pela direita ao trocar. Dentro do vidro, a lua e o sol são píxeis: cada píxel da lua tem um par no sol e
 * voa até ele com um atraso e um remoinho seus. Desenhado em Canvas (sem shaders, para correr bem numa box).
 */

private val TrackHeight = 40.dp
private val TrackWidth = 76.dp
private val Inset = 4.dp
private val TrackShape = RoundedCornerShape(ControlRadius + Inset)

/** O botão de dia/noite: [night] é o estado; OK troca (como o botão redondo que veio substituir). */
@Composable
fun DayNightToggle(night: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 0 = noite, 1 = dia.
    val t by animateFloatAsState(if (night) 0f else 1f, tween(700, easing = FastOutSlowInEasing), label = "dayNight")
    Surface(
        onClick = onClick,
        modifier = modifier.size(TrackWidth, TrackHeight).semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(TrackShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Color.White), inset = (-4).dp, shape = TrackShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawToggle(t) }
    }
}

private fun DrawScope.drawToggle(t: Float) {
    val inset = Inset.toPx()
    val knob = size.height - inset * 2
    val trackR = (ControlRadius + Inset).toPx()
    val knobR = ControlRadius.toPx()

    // A calha: escura à noite, clara de dia.
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(NightTrackTop, DayTrackTop, t), lerp(NightTrackBottom, DayTrackBottom, t))),
        cornerRadius = CornerRadius(trackR),
    )
    drawRoundRect(Color.Black.copy(alpha = 0.18f * (1 - t) + 0.05f), cornerRadius = CornerRadius(trackR), style = Stroke(1.dp.toPx()))

    val left = inset + (size.width - knob - inset * 2) * t
    val glow = lerp(NightGlow, DayGlow, t)
    val core = lerp(NightGlowCore, DayGlowCore, t)

    // A luz que se espalha pela calha, do lado onde está o arco.
    val spillY = if (t < 0.5f) size.height else 0f
    drawCircle(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.35f * abs(1 - 2 * t)), Color.Transparent), Offset(left + knob / 2, spillY), knob),
        radius = knob,
        center = Offset(left + knob / 2, spillY),
    )

    translate(left, inset) {
        // O vidro: a luz vem de baixo à noite e de cima de dia.
        drawRoundRect(
            Brush.verticalGradient(
                0f to lerp(NightKnob[0], DayKnob[0], t),
                0.45f to lerp(NightKnob[1], DayKnob[1], t),
                0.8f to lerp(NightKnob[2], DayKnob[2], t),
                1f to lerp(NightKnob[3], DayKnob[3], t),
            ),
            size = Size(knob, knob),
            cornerRadius = CornerRadius(knobR),
        )
        drawRoundRect(Color.White.copy(alpha = 0.35f), size = Size(knob, knob), cornerRadius = CornerRadius(knobR), style = Stroke(1.dp.toPx()))

        drawPixels(t, knob, lerp(NightIcon, DayIcon, t))

        // O arco: um pedaço do contorno do vidro (um pouco por fora), centrado em baixo à noite e em cima de dia.
        val out = 2.dp.toPx()
        val outline = roundRectPath(Rect(-out, -out, knob + out, knob + out), knobR + out)
        val measure = PathMeasure().apply { setPath(outline, false) }
        val length = measure.length
        // 0 = meio de cima, a andar no sentido dos ponteiros; à noite em 0.5 (baixo), de dia em 0 (cima), pela direita.
        val center = 0.5f * (1 - t)
        val arc = arcPath(measure, length, center, 0.42f)
        listOf(9.dp to 0.12f, 6.dp to 0.22f, 3.5.dp to 0.45f).forEach { (w, a) ->
            drawPath(arc, glow.copy(alpha = a), style = Stroke(w.toPx(), cap = StrokeCap.Round))
        }
        drawPath(arc, core, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Contorno de um quadrado arredondado, a começar no meio de cima e no sentido dos ponteiros. */
private fun roundRectPath(r: Rect, radius: Float): Path = Path().apply {
    val d = radius * 2
    moveTo(r.center.x, r.top)
    lineTo(r.right - radius, r.top)
    arcTo(Rect(r.right - d, r.top, r.right, r.top + d), -90f, 90f, false)
    lineTo(r.right, r.bottom - radius)
    arcTo(Rect(r.right - d, r.bottom - d, r.right, r.bottom), 0f, 90f, false)
    lineTo(r.left + radius, r.bottom)
    arcTo(Rect(r.left, r.bottom - d, r.left + d, r.bottom), 90f, 90f, false)
    lineTo(r.left, r.top + radius)
    arcTo(Rect(r.left, r.top, r.left + d, r.top + d), 180f, 90f, false)
    close()
}

/** O pedaço do contorno com [span] do comprimento, centrado em [center] (fração do contorno; dá a volta). */
private fun arcPath(measure: PathMeasure, length: Float, center: Float, span: Float): Path {
    val start = ((center - span / 2) % 1f + 1f) % 1f * length
    val end = start + span * length
    return Path().also { p ->
        if (end <= length) {
            measure.getSegment(start, end, p, true)
        } else {
            measure.getSegment(start, length, p, true)
            measure.getSegment(0f, end - length, p, true)
        }
    }
}

/** Um píxel dos ícones: posição na grelha, ângulo (para os pares) e força (a sombra da lua é mais fraca). */
private class Px(val x: Int, val y: Int, val a: Float, val w: Float)

private const val Grid = 17

/** Os píxeis do sol e da lua, ordenados pelo ângulo para cada um ter um par no outro. */
private val Icons: Pair<List<Px>, List<Px>> by lazy {
    val c = Grid / 2
    val sun = mutableListOf<Px>()
    val moon = mutableListOf<Px>()
    for (y in 0 until Grid) for (x in 0 until Grid) {
        val dx = (x - c).toFloat()
        val dy = (y - c).toFloat()
        val r = hypot(dx, dy)
        val a = atan2(dy, dx)
        // Sol: miolo cheio e raios tracejados nas 8 direções.
        val core = max(abs(dx), abs(dy)) <= 3 && r < 4.1f
        val dir = (a / (PI / 4)).roundToInt()
        val onRay = abs(a - dir * PI / 4) * r < 0.55
        val ray = onRay && if (dir % 2 == 0) r in 5.5f..8.2f else r in 5.5f..7.6f
        if (core || ray) sun += Px(x, y, a, 1f)
        // Lua: crescente aceso; a parte na sombra em xadrez.
        val inDisc = hypot(dx + 1, dy) < 5.6f
        val lit = inDisc && hypot(dx - 2.2f, dy + 1.6f) > 4.4f
        if (lit) moon += Px(x, y, a, 1f) else if (inDisc && (x + y) % 2 == 0) moon += Px(x, y, a, 0.35f)
    }
    // Uma estrela pequena ao lado da lua.
    listOf(13 to 3, 13 to 2, 13 to 4, 12 to 3, 14 to 3, 11 to 6, 15 to 6).forEach { (x, y) ->
        moon += Px(x, y, atan2((y - c).toFloat(), (x - c).toFloat()), 0.8f)
    }
    sun.sortBy { it.a }
    moon.sortBy { it.a }
    sun to moon
}

private fun DrawScope.drawPixels(t: Float, knob: Float, color: Color) {
    val (sun, moon) = Icons
    val icon = knob * 0.6f
    val cell = icon / Grid
    val origin = (knob - icon) / 2
    val n = max(sun.size, moon.size)
    for (i in 0 until n) {
        val s = sun[i * sun.size / n]
        val m = moon[i * moon.size / n]
        // Cada píxel parte com um atraso seu e faz um remoinho a meio do caminho.
        val st = (t * 1.4f - i.toFloat() / n * 0.4f).coerceIn(0f, 1f)
        val swirl = sin(st * PI).toFloat() * 1.6f
        val x = m.x + (s.x - m.x) * st + cos(i * 1.7f) * swirl
        val y = m.y + (s.y - m.y) * st + sin(i * 1.7f) * swirl
        val w = m.w + (s.w - m.w) * st
        drawRect(
            color.copy(alpha = w),
            topLeft = Offset(origin + x * cell + cell * 0.08f, origin + y * cell + cell * 0.08f),
            size = Size(cell * 0.84f, cell * 0.84f),
        )
    }
}

private val NightTrackTop = Color(0xFF0B0C11)
private val NightTrackBottom = Color(0xFF14161D)
private val DayTrackTop = Color(0xFFF6F4F0)
private val DayTrackBottom = Color(0xFFE6E3DE)
private val NightKnob = listOf(Color(0xFF3B3E47), Color(0xFF2A2D37), Color(0xFF3A4380), Color(0xFF7C8BFF))
private val DayKnob = listOf(Color(0xFFFFC861), Color(0xFFFBF1DF), Color(0xFFF5F0E8), Color(0xFFF2EEE8))
private val NightGlow = Color(0xFF6E7DFF)
private val NightGlowCore = Color(0xFFD5DDFF)
private val DayGlow = Color(0xFFFFA62B)
private val DayGlowCore = Color(0xFFFFE6A8)
private val NightIcon = Color(0xFFF4F6FF)
private val DayIcon = Color(0xFF2A2620)
