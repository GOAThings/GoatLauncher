package cc.slokker.goatlauncher.ui.home

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.ui.theme.ControlRadius
import cc.slokker.goatlauncher.ui.theme.GoatColors
import kotlin.math.PI
import kotlin.math.cos

/** Como fica o cartão de uma app com foco (Definições → Aparência). [color] null = automática. */
@Immutable
data class FocusStyle(val effect: String, val animation: String, val color: Color?) {
    companion object {
        val Default = FocusStyle(AccountSettings.FOCUS_BORDER, AccountSettings.ANIM_NONE, null)

        fun of(account: AccountSettings) = FocusStyle(account.focusEffect, account.focusAnimation, parseColor(account.focusColor))
    }
}

/** O estilo de foco em uso; o ecrã inicial e a pré-visualização das Definições é que o dão. */
val LocalFocusStyle = staticCompositionLocalOf { FocusStyle.Default }

/** As cores que se podem escolher (null = automática: contorno branco, brilho da cor da app). */
val FocusColors: List<Color?> = listOf(
    null,
    Color.White,
    GoatColors.Sky,
    GoatColors.Indigo,
    Color(0xFFC084FC),
    Color(0xFFF472B6),
    Color(0xFFF87171),
    Color(0xFFFB923C),
    Color(0xFFFACC15),
    Color(0xFF4ADE80),
)

fun parseColor(spec: String): Color? =
    spec.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }?.let { Color(0xFF000000 or it.substring(1).toLong(16)) }

fun colorSpec(color: Color?): String =
    color?.let { "#%06X".format(it.toArgb() and 0xFFFFFF) } ?: AccountSettings.COLOR_AUTO

/** Quanto o cartão com foco cresce. */
const val FocusScale = 1.1f

/** Quantos cartões têm foco agora (para "Escurecer os outros"); o ecrã inicial e a pré-visualização dão um cada. */
@Stable
class FocusDim {
    var focused by mutableIntStateOf(0)
}

val LocalFocusDim = staticCompositionLocalOf { FocusDim() }

/** As animações que cada efeito aceita: o vidro e o "escurecer" não têm (o vidro tem o seu brilho a passar). */
fun focusAnimations(effect: String): List<String> = when (effect) {
    AccountSettings.FOCUS_GLASS, AccountSettings.FOCUS_DIM, AccountSettings.FOCUS_SHADOW -> emptyList()
    AccountSettings.FOCUS_VIEWFINDER, AccountSettings.FOCUS_UNDERLINE ->
        listOf(AccountSettings.ANIM_NONE, AccountSettings.ANIM_PULSE, AccountSettings.ANIM_BREATH)
    else -> listOf(AccountSettings.ANIM_NONE, AccountSettings.ANIM_PULSE, AccountSettings.ANIM_BREATH, AccountSettings.ANIM_SHINE)
}

/**
 * O efeito de foco desenhado à volta do cartão (o brilho ou a sombra por trás, o contorno por cima). Tem de vir
 * depois do graphicsLayer da escala, para crescer com o cartão. [appColor] é a cor do brilho automático;
 * [moving] (a mover a app) põe o contorno no gradiente da marca.
 */
@Composable
fun Modifier.focusEffect(
    focused: Boolean,
    appColor: Color,
    moving: Boolean = false,
    corner: Dp = ControlRadius,
    style: FocusStyle = LocalFocusStyle.current,
): Modifier {
    val shown by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "focus")
    val effect = style.effect
    val animation = style.animation.takeIf { it in focusAnimations(effect) } ?: AccountSettings.ANIM_NONE
    val glass = effect == AccountSettings.FOCUS_GLASS
    // "Escurecer os outros": cada cartão com foco conta; os outros escurecem enquanto houver algum.
    val dim = LocalFocusDim.current
    val dimMode = effect == AccountSettings.FOCUS_DIM
    if (dimMode && focused) {
        DisposableEffect(dim) {
            dim.focused++
            onDispose { dim.focused-- }
        }
    }
    val dimmed by animateFloatAsState(if (dimMode && !focused && dim.focused > 0) 1f else 0f, tween(220), label = "dim")
    // A animação só corre no cartão com foco (um relógio por cartão seria desperdício). Lê-se ao desenhar:
    // cada frame só redesenha o efeito, não volta a compor o cartão. O vidro tem sempre o brilho a passar.
    val phaseState = if (focused && !moving && (glass || animation != AccountSettings.ANIM_NONE)) {
        val period = when {
            glass -> GLASS_PERIOD
            animation == AccountSettings.ANIM_PULSE -> 1200
            animation == AccountSettings.ANIM_BREATH -> 3200
            effect == AccountSettings.FOCUS_AURORA -> 4000
            else -> 2600
        }
        val transition = rememberInfiniteTransition(label = "focusAnim")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    } else {
        null
    }
    return drawWithCache {
        val radius = corner.toPx()
        onDrawWithContent {
            val a = shown
            val phase = phaseState?.value ?: 0f
            if (a <= 0f) {
                drawContent()
                if (dimmed > 0f) drawRoundRect(Color.Black, cornerRadius = CornerRadius(radius), alpha = DIM_ALPHA * dimmed)
                return@onDrawWithContent
            }
            // Intensidade da animação, de 0 a 1 (o pulsar nunca apaga de todo; o respirar quase).
            val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * phase)
            val k = when (animation) {
                AccountSettings.ANIM_PULSE -> 0.55f + 0.45f * wave
                AccountSettings.ANIM_BREATH -> 0.15f + 0.85f * wave
                else -> 1f
            }
            val shine = animation == AccountSettings.ANIM_SHINE
            val color = style.color ?: Color.White
            val stroke = 3.dp.toPx()
            val edge = Size(size.width - stroke, size.height - stroke)
            val corners = CornerRadius(radius - stroke / 2)
            if (moving) {
                drawGlow(Color.Black, 14.dp.toPx(), radius, a * 0.55f, dy = 4.dp.toPx())
                drawContent()
                drawRoundRect(GoatColors.Accent, Offset(stroke / 2, stroke / 2), edge, corners, style = Stroke(stroke), alpha = a)
                return@onDrawWithContent
            }
            // Por baixo do cartão.
            when (effect) {
                AccountSettings.FOCUS_GLOW -> drawGlow(glowColor(style.color, appColor), (4 + 6 * k).dp.toPx(), radius, a * (0.45f + 0.55f * k))
                AccountSettings.FOCUS_AURORA -> drawGlow(auroraColors(style.color)[1], 7.dp.toPx(), radius, a * 0.5f * k)
                AccountSettings.FOCUS_GLASS -> drawGlow(Color.Black, 16.dp.toPx(), radius, a * 0.6f, dy = 5.dp.toPx())
                else -> drawGlow(Color.Black, 14.dp.toPx(), radius, a * 0.55f, dy = 4.dp.toPx())
            }
            drawContent()
            // Por cima (ou à volta) do cartão.
            when (effect) {
                AccountSettings.FOCUS_BORDER -> {
                    // Com a luz a rodar, o contorno fica mais discreto e a luz é que se vê.
                    val base = if (shine) 0.45f else k
                    drawRoundRect(color, Offset(stroke / 2, stroke / 2), edge, corners, style = Stroke(stroke), alpha = a * base)
                }
                AccountSettings.FOCUS_GLASS -> drawGlass(color, radius, a, phase)
                AccountSettings.FOCUS_REFLECTION -> {
                    drawReflection(a * k)
                    drawRoundRect(color, Offset(stroke / 2, stroke / 2), edge, corners, style = Stroke(stroke), alpha = a * (if (shine) 0.45f else 0.9f))
                }
                AccountSettings.FOCUS_AURORA -> {
                    val angle = if (shine) phase * 360f else AURORA_REST_ANGLE
                    drawAurora(auroraColors(style.color), angle, stroke, radius, a * (0.4f + 0.6f * k))
                }
                AccountSettings.FOCUS_VIEWFINDER -> drawViewfinder(color, a * k)
                AccountSettings.FOCUS_UNDERLINE -> drawUnderline(color, a * k)
                AccountSettings.FOCUS_RING -> {
                    val gap = RING_GAP.toPx()
                    drawRoundRect(
                        color,
                        Offset(-gap - stroke / 2, -gap - stroke / 2),
                        Size(size.width + 2 * gap + stroke, size.height + 2 * gap + stroke),
                        CornerRadius(radius + gap + stroke / 2),
                        style = Stroke(stroke),
                        alpha = a * (if (shine) 0.4f else k),
                    )
                }
            }
            if (shine) {
                val light = if (effect == AccountSettings.FOCUS_GLOW) lerp(glowColor(style.color, appColor), Color.White, 0.6f) else Color.White
                when (effect) {
                    AccountSettings.FOCUS_BORDER, AccountSettings.FOCUS_GLOW, AccountSettings.FOCUS_REFLECTION ->
                        drawShine(light, phase * 360f, stroke, radius, a)
                    AccountSettings.FOCUS_RING -> drawShine(light, phase * 360f, stroke, radius, a, grow = RING_GAP.toPx() + stroke / 2)
                }
            }
        }
    }
}

/** Quanto escurecem os outros cartões em "Escurecer os outros". */
private const val DIM_ALPHA = 0.55f
/** Distância entre o cartão e o anel flutuante. */
private val RING_GAP = 6.dp
/** O vidro: o brilho passa na primeira parte do ciclo e descansa no resto. */
private const val GLASS_PERIOD = 3600
private const val GLASS_SWEEP = 0.55f
/** Onde fica o gradiente da aurora quando não roda (o rosa em baixo à direita). */
private const val AURORA_REST_ANGLE = 35f

/** As cores da aurora: as da GOAT (céu, índigo, rosa), ou a escolhida com dois tons vizinhos. */
private fun auroraColors(chosen: Color?): List<Color> {
    if (chosen == null) return listOf(GoatColors.Sky, GoatColors.Indigo, Color(0xFFF472B6))
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(chosen.toArgb(), hsv)
    fun hue(shift: Float) = Color.hsv((hsv[0] + shift + 360f) % 360f, hsv[1].coerceAtLeast(0.5f), hsv[2].coerceAtLeast(0.85f))
    return if (hsv[1] < 0.2f) listOf(Color.White, Color(0xFFCBD5E1), Color(0xFF94A3B8)) else listOf(chosen, hue(40f), hue(-40f))
}

/** Contorno com as cores da aurora em volta (gradiente circular, rodado [angle] graus). */
private fun DrawScope.drawAurora(colors: List<Color>, angle: Float, stroke: Float, corner: Float, alpha: Float) {
    val cx = size.width / 2
    val cy = size.height / 2
    val argb = (colors + colors.first()).map { it.copy(alpha = alpha).toArgb() }.toIntArray()
    val shader = SweepGradient(cx, cy, argb, null)
    shader.setLocalMatrix(Matrix().apply { setRotate(angle, cx, cy) })
    drawRoundRect(
        ShaderBrush(shader),
        Offset(stroke / 2, stroke / 2),
        Size(size.width - stroke, size.height - stroke),
        CornerRadius(corner - stroke / 2),
        style = Stroke(stroke),
    )
}

/** O cartão ao contrário por baixo dele, a desaparecer, como num chão brilhante. */
private fun ContentDrawScope.drawReflection(alpha: Float) {
    val gap = 4.dp.toPx()
    val top = size.height + gap
    val depth = size.height * 0.45f
    val area = Rect(0f, top, size.width, top + depth)
    drawContext.canvas.saveLayer(area, Paint())
    clipRect(area.left, area.top, area.right, area.bottom) {
        withTransform({ scale(1f, -1f, pivot = Offset(0f, size.height + gap / 2)) }) { this@drawReflection.drawContent() }
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f * alpha), Color.Transparent), startY = top, endY = top + depth),
            topLeft = area.topLeft,
            size = area.size,
            blendMode = BlendMode.DstIn,
        )
    }
    drawContext.canvas.restore()
}

/**
 * A cor do brilho: a escolhida ou a da app, com o mesmo tom mas viva (as cores das apps são muitas vezes escuras,
 * e clareá-las com branco dava um cinzento). Uma app sem cor (preta, cinzenta) brilha a branco.
 */
private fun glowColor(chosen: Color?, appColor: Color): Color {
    if (chosen != null) return chosen
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(appColor.toArgb(), hsv)
    return if (hsv[1] < 0.2f) Color.White else Color.hsv(hsv[0], hsv[1].coerceAtLeast(0.55f), 0.95f)
}

/**
 * Halo à volta do cartão: retângulos arredondados cada vez maiores e mais transparentes, por baixo do cartão
 * (as sombras do Canvas, setShadowLayer, não aparecem aqui com a aceleração por hardware).
 */
private fun DrawScope.drawGlow(color: Color, blur: Float, corner: Float, alpha: Float, dy: Float = 0f) {
    if (alpha <= 0f || blur <= 0f) return
    val layer = color.copy(alpha = color.alpha * (alpha.coerceIn(0f, 1f) * GLOW_LAYER_ALPHA))
    for (i in GLOW_LAYERS downTo 1) {
        val spread = blur * i / GLOW_LAYERS
        drawRoundRect(
            layer,
            Offset(-spread, -spread + dy),
            Size(size.width + 2 * spread, size.height + 2 * spread),
            CornerRadius(corner + spread),
        )
    }
}

/** Quantas camadas tem o halo e quanto pinta cada uma (sobrepostas, ficam mais fortes junto ao cartão). */
private const val GLOW_LAYERS = 12
private const val GLOW_LAYER_ALPHA = 0.09f

/**
 * Vidro por cima do cartão: um reflexo do canto de cima à esquerda que se apaga a meio, uma orla fina mais clara
 * em cima (a luz vem de cima) e uma faixa de luz que atravessa o vidro de vez em quando ([phase], de 0 a 1).
 */
private fun DrawScope.drawGlass(edge: Color, corner: Float, alpha: Float, phase: Float) {
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.42f * alpha),
            0.38f to Color.White.copy(alpha = 0.1f * alpha),
            0.6f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        cornerRadius = CornerRadius(corner),
    )
    // A faixa: entra pela esquerda, sai pela direita, na diagonal.
    val t = phase / GLASS_SWEEP
    if (t < 1f) {
        val mid = -0.25f + 1.5f * t
        drawRoundRect(
            Brush.linearGradient(
                (mid - 0.18f) to Color.Transparent,
                mid to Color.White.copy(alpha = 0.5f * alpha),
                (mid + 0.18f) to Color.Transparent,
                start = Offset(0f, size.height * 0.2f),
                end = Offset(size.width, size.height * 0.8f),
            ),
            cornerRadius = CornerRadius(corner),
        )
    }
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        Brush.verticalGradient(listOf(edge.copy(alpha = 0.9f * alpha), edge.copy(alpha = 0.25f * alpha))),
        Offset(stroke / 2, stroke / 2),
        Size(size.width - stroke, size.height - stroke),
        CornerRadius(corner - stroke / 2),
        style = Stroke(stroke),
    )
}

/** Quatro cantos em L à volta do cartão, como no visor de uma câmara. */
private fun DrawScope.drawViewfinder(color: Color, alpha: Float) {
    val out = 7.dp.toPx()
    val len = minOf(16.dp.toPx(), size.height / 3)
    val l = -out
    val t = -out
    val r = size.width + out
    val b = size.height + out
    val path = Path().apply {
        moveTo(l, t + len); lineTo(l, t); lineTo(l + len, t)
        moveTo(r - len, t); lineTo(r, t); lineTo(r, t + len)
        moveTo(r, b - len); lineTo(r, b); lineTo(r - len, b)
        moveTo(l + len, b); lineTo(l, b); lineTo(l, b - len)
    }
    drawPath(path, color, alpha = alpha, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Uma barra de luz curta por baixo do cartão, com um halo suave. */
private fun DrawScope.drawUnderline(color: Color, alpha: Float) {
    val w = size.width * 0.5f
    val h = 4.dp.toPx()
    val top = size.height + 9.dp.toPx()
    val left = (size.width - w) / 2
    for (i in 3 downTo 1) {
        val spread = 3.dp.toPx() * i
        drawRoundRect(
            color.copy(alpha = 0.12f * alpha),
            Offset(left - spread, top - spread),
            Size(w + 2 * spread, h + 2 * spread),
            CornerRadius(h / 2 + spread),
        )
    }
    drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(h / 2), alpha = alpha)
}

/** Uma luz que dá a volta ao contorno do cartão ([angle] em graus); [grow] afasta-a do cartão (o anel flutuante). */
private fun DrawScope.drawShine(color: Color, angle: Float, stroke: Float, corner: Float, alpha: Float, grow: Float = 0f) {
    val clear = color.copy(alpha = 0f).toArgb()
    val bright = color.copy(alpha = alpha).toArgb()
    val cx = size.width / 2
    val cy = size.height / 2
    val shader = SweepGradient(cx, cy, intArrayOf(clear, clear, bright, clear, clear), floatArrayOf(0f, 0.36f, 0.5f, 0.64f, 1f))
    shader.setLocalMatrix(Matrix().apply { setRotate(angle, cx, cy) })
    val inset = stroke / 2 - grow
    drawRoundRect(
        ShaderBrush(shader),
        Offset(inset, inset),
        Size(size.width - 2 * inset, size.height - 2 * inset),
        CornerRadius(corner - inset),
        style = Stroke(stroke * 1.3f),
    )
}
