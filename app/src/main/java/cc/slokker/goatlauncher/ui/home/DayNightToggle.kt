package cc.slokker.goatlauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/*
 * O interruptor de dia/noite da barra de cima: o mesmo do site (thegoat.systems, assets/theme.js e o CSS .lt), em
 * Canvas. Uma calha com um botão de vidro; a luz contorna o botão em baixo à noite (azul) e em cima de dia (âmbar)
 * e dá a volta pela direita ao trocar. Dentro do vidro, a lua e o sol numa grelha de 11 × 11: cada píxel da lua faz
 * par com um do sol pelo ângulo, parte um pouco depois do anterior, roda um pouco e assenta com mola.
 * t = 0 é noite (botão à esquerda), t = 1 é dia (à direita).
 */

private val TrackWidth = 72.dp
private val TrackHeight = 40.dp
private val Knob = 34.dp
private val Pad = 3.dp
private val TrackRadius = 15.dp
private val KnobRadius = 10.5.dp

/** O botão de dia/noite: [night] é o estado; OK troca. */
@Composable
fun DayNightToggle(night: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = remember { Animatable(if (night) 0f else 1f) }
    LaunchedEffect(night) {
        val target = if (night) 0f else 1f
        // Como no site: mais longo quanto mais tiver de andar, a abrandar no fim.
        t.animateTo(target, tween((520 + 380 * abs(target - t.value)).toInt(), easing = EaseOutCubic))
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "dayNightFocus")
    // Sem Surface: a luz à volta do botão sai da calha, e uma Surface cortava-a pela forma.
    Canvas(
        modifier
            .size(TrackWidth, TrackHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = label }
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        val glow = lerp(NightGlow, DayGlow, t.value)
        drawToggle(t.value, day = !night, glow)
        // O contorno de foco do site: da cor da luz, 5 dp por fora.
        if (focused) {
            val o = 5.dp.toPx()
            drawRoundRect(
                glow.copy(alpha = 0.9f),
                Offset(-o, -o),
                Size(size.width + o * 2, size.height + o * 2),
                CornerRadius((TrackRadius + 5.dp).toPx()),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

private val EaseOutCubic = Easing { 1 - (1 - it).pow(3) }

private fun DrawScope.drawToggle(t: Float, day: Boolean, glow: Color) {
    val pad = Pad.toPx()
    val k = Knob.toPx()
    val slide = t * (size.width - k - pad * 2)
    val knobCenter = Offset(pad + k / 2 + slide, pad + k / 2)

    // A calha, com a luz do botão a cair nela (em baixo à noite, em cima de dia).
    val trackR = CornerRadius(TrackRadius.toPx())
    drawRoundRect(Brush.verticalGradient(if (day) DayTrack else NightTrack), cornerRadius = trackR)
    val spill = Offset(knobCenter.x, size.height * (1 - t))
    drawRoundRect(
        Brush.radialGradient(listOf(glow.copy(alpha = if (day) 0.30f else 0.22f), Color.Transparent), spill, 28.dp.toPx()),
        cornerRadius = trackR,
    )
    // Sombra por dentro da calha (em cima) e o fio de luz à volta.
    drawRoundRect(
        Brush.verticalGradient(0f to Color.Black.copy(alpha = if (day) 0.06f else 0.45f), 0.25f to Color.Transparent),
        cornerRadius = trackR,
    )
    drawRoundRect(Color.White.copy(alpha = if (day) 0.6f else 0.05f), cornerRadius = trackR, style = Stroke(1.dp.toPx()))

    translate(pad + slide, pad) {
        // A luz à volta do botão: um arco de 160° centrado em [a] (0° em cima, 180° em baixo, a andar pela direita).
        val a = 180f - 180f * t
        val ringInset = 4.dp.toPx()
        val ringSize = Size(k + ringInset * 2, k + ringInset * 2)
        val ring = ringBrush(a, glow, Offset(k / 2, k / 2))
        // O brilho (no site, desfocado): traços largos e fracos por baixo do anel.
        listOf(9.dp to 0.18f, 6.dp to 0.28f, 3.5.dp to 0.45f).forEach { (w, alpha) ->
            drawRoundRect(ring, Offset(-ringInset, -ringInset), ringSize, CornerRadius(13.dp.toPx()), style = Stroke(w.toPx()), alpha = alpha)
        }
        drawRoundRect(ring, Offset(-ringInset, -ringInset), ringSize, CornerRadius(13.dp.toPx()), style = Stroke(1.5.dp.toPx()))

        // O corpo do botão, com a luz por dentro do lado do arco, e a sombra por baixo.
        val knobR = CornerRadius(KnobRadius.toPx())
        drawRoundRect(Color.Black.copy(alpha = if (day) 0.12f else 0.35f), Offset(0f, 3.dp.toPx()), Size(k, k), knobR)
        drawRoundRect(Brush.verticalGradient(if (day) DayBody else NightBody), size = Size(k, k), cornerRadius = knobR)
        drawRoundRect(
            Brush.radialGradient(listOf(glow.copy(alpha = if (day) 0.75f else 0.55f), Color.Transparent), Offset(k / 2, k * (1 - t)), k * 0.7f),
            size = Size(k, k),
            cornerRadius = knobR,
        )

        drawPixels(t, k, day)

        // O vidro por cima: reflexo em cima à esquerda e contorno fino.
        drawRoundRect(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), Offset(k * 0.35f, 0f), k * 0.6f),
            size = Size(k, k),
            cornerRadius = knobR,
        )
        drawRoundRect(Color.White.copy(alpha = if (day) 0.8f else 0.14f), size = Size(k, k), cornerRadius = knobR, style = Stroke(1.dp.toPx()))
        drawRoundRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = if (day) 1f else 0.45f), 0.06f to Color.Transparent),
            size = Size(k, k),
            cornerRadius = knobR,
            style = Stroke(1.dp.toPx()),
        )
    }
}

/**
 * O gradiente do anel (o "conic-gradient" do site): transparente fora de um arco de 160° à volta de [a], forte no
 * meio. [a] em graus como no CSS (0 em cima, a rodar como os ponteiros); o Compose começa à direita.
 */
private fun ringBrush(a: Float, glow: Color, center: Offset): Brush {
    val steps = 72
    val colors = List(steps + 1) { i ->
        val cssAngle = i * 360f / steps + 90f
        val rel = ((cssAngle - (a - 80f)) % 360f + 360f) % 360f
        val alpha = when {
            rel < 30f -> 0.9f * rel / 30f
            rel < 80f -> 0.9f + 0.1f * (rel - 30f) / 50f
            rel < 130f -> 1f - 0.1f * (rel - 80f) / 50f
            rel < 160f -> 0.9f * (160f - rel) / 30f
            else -> 0f
        }
        glow.copy(alpha = alpha)
    }
    return Brush.sweepGradient(colors, center)
}

private const val N = 11
private const val C = (N - 1) / 2f

/** Píxeis da lua (com três estrelas), do resto do disco (a sombra) e do sol, e os pares lua → sol pelo ângulo. */
private class Icons(val shade: List<Pair<Int, Int>>, val pairs: List<Triple<Pair<Int, Int>, Pair<Int, Int>, Float>>)

private val PixelIcons: Icons by lazy {
    val moon = mutableListOf<Pair<Int, Int>>()
    val sun = mutableListOf<Pair<Int, Int>>()
    val shade = mutableListOf<Pair<Int, Int>>()
    for (y in 0 until N) for (x in 0 until N) {
        val inDisc = (x - 4.6f).pow(2) + (y - 5.6f).pow(2) <= 4.5f * 4.5f
        val bite = (x - 6.8f).pow(2) + (y - 4.4f).pow(2) <= 3.7f * 3.7f
        if (inDisc && !bite) moon += x to y else if (inDisc) shade += x to y
        val sx = x - C
        val sy = y - C
        val r = hypot(sx, sy)
        val ray = r in 3.7f..5.2f && (abs(sx) < 0.6f || abs(sy) < 0.6f || abs(abs(sx) - abs(sy)) < 0.6f)
        if (r <= 2.3f || ray) sun += x to y
    }
    moon += listOf(8 to 1, 10 to 2, 9 to 4) // estrelas
    fun byAngle(list: List<Pair<Int, Int>>) = list.sortedBy { (x, y) -> atan2(y - C, x - C) }
    val a = byAngle(moon)
    val b = byAngle(sun)
    val n = max(a.size, b.size)
    Icons(shade, List(n) { i -> Triple(a[floor(i.toFloat() * a.size / n).toInt()], b[floor(i.toFloat() * b.size / n).toInt()], i.toFloat() / n) })
}

private fun easeBack(k: Float): Float {
    val s = 1.6f
    val x = k - 1
    return x * x * ((s + 1) * x + s) + 1
}

private fun DrawScope.drawPixels(t: Float, k: Float, day: Boolean) {
    val icons = PixelIcons
    val cell = k / N
    val gap = cell * 0.11f
    fun dot(x: Float, y: Float, color: Color) = drawRect(color, Offset(x * cell + gap, y * cell + gap), Size(cell - gap * 2, cell - gap * 2))

    // O resto do disco da lua, apagado; some ao passar para o sol.
    val shadeColor = if (day) Color(0xFF28241E).copy(alpha = 0.14f * (1 - t)) else MoonPx.copy(alpha = 0.16f * (1 - t))
    icons.shade.forEach { (x, y) -> dot(x.toFloat(), y.toFloat(), shadeColor) }

    val sunPx = if (day) SunDark else SunLight
    for ((m, s, order) in icons.pairs) {
        // Cada píxel parte um pouco depois do anterior, roda um pouco e assenta com mola.
        val p = ((t - order * 0.5f) / 0.5f).coerceIn(0f, 1f)
        val e = easeBack(p)
        val x0 = m.first + (s.first - m.first) * e
        val y0 = m.second + (s.second - m.second) * e
        val swirl = sin(PI * p).toFloat() * 0.9f
        val cx = x0 - C
        val cy = y0 - C
        val x = C + cx * cos(swirl) - cy * sin(swirl)
        val y = C + cx * sin(swirl) + cy * cos(swirl)
        dot(x, y, lerp(MoonPx, sunPx, p))
    }
}

private val NightGlow = Color(124, 146, 224)
private val DayGlow = Color(255, 178, 64)
private val MoonPx = Color(246, 238, 222)
private val SunDark = Color(44, 40, 34)
private val SunLight = Color(255, 210, 122)
private val NightTrack = listOf(Color(0xFF08080A), Color(0xFF111216))
private val DayTrack = listOf(Color(0xFFF4F2EE), Color(0xFFE6E3DE))
private val NightBody = listOf(Color(0xFF2A2C33), Color(0xFF15161B))
private val DayBody = listOf(Color(0xFFFFFFFF), Color(0xFFF1EEE9))
