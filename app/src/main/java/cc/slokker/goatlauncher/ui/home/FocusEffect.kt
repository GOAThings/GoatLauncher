package cc.slokker.goatlauncher.ui.home

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.ui.theme.ControlRadius
import cc.slokker.goatlauncher.ui.theme.GoatColors
import kotlin.math.abs

/**
 * Como fica o cartão de uma app com foco (Definições → Aparência). [animation] é a guardada (o efeito só mostra as
 * suas, ver [FocusEffectKind.animationOf]); [color] null = automática.
 * Com uma animação (pulsar, respirar, luz a rodar, a faixa do vidro) o ecrã redesenha-se 60 vezes por segundo enquanto
 * ela corre. Na SHIELD isso cabe a 60 fps desde que os frames pesados (trocar de favorito, de secção, de separador)
 * deixaram de parar a linha principal; antes, cada um deles era um frame perdido a meio da animação.
 */
@Immutable
data class FocusStyle(val effect: FocusEffectKind, val animation: String, val color: Color?) {
    companion object {
        val Default = FocusStyle(FocusEffectKind.BORDER, AccountSettings.ANIM_NONE, null)

        fun of(account: AccountSettings) =
            FocusStyle(FocusEffectKind.of(account.focusEffect), account.focusAnimation, parseColor(account.focusColor))
    }
}

/** O estilo de foco em uso; o ecrã inicial e a pré-visualização das Definições é que o dão. */
val LocalFocusStyle = staticCompositionLocalOf { FocusStyle.Default }

/** As cores que se podem escolher (null = automática: contorno no azul do foco GOAT, brilho da cor da app). */
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

/**
 * O movimento do foco (crescer, acender), como na Apple TV. Medido num vídeo do tvOS (60 fps): metade aos ~0,17 s,
 * 90% aos ~0,4 s e parado aos ~0,6 s; esta mola dá 0,17 s e 0,39 s. Antes eram 150–180 ms a direito: a 60 fps também, mas em ~10
 * frames, a arrancar e parar de golpe. Uma mola sem ressalto (amortecimento 1, nunca passa de 1): com teclas
 * seguidas, cada movimento continua de onde o outro ia, sem saltos.
 */
val FocusSpring = spring<Float>(dampingRatio = 1f, stiffness = 100f, visibilityThreshold = 0.001f)

/**
 * O cartão com foco cresce para [target] (1 = tamanho normal), mas só no desenho. Com a escala da camada (a do
 * graphicsLayer, ou a do Surface da tv-material), o deslize que traz o cartão à vista media-o já crescido, e a meio do
 * crescer pedia um bocado diferente em cada tecla: as filas e as grelhas saltavam uns píxeis para cima e para baixo.
 * Fica na sua camada: crescer só redesenha o cartão.
 */
@Composable
fun Modifier.drawnScale(target: Float): Modifier {
    val s by animateFloatAsState(target, FocusSpring, label = "drawnScale")
    return graphicsLayer().drawWithContent { scale(s, s) { this@drawWithContent.drawContent() } }
}

/** Quantos cartões têm foco agora (para "Escurecer os outros"); o ecrã inicial e a pré-visualização dão um cada. */
@Stable
class FocusDim {
    var focused by mutableIntStateOf(0)
}

val LocalFocusDim = staticCompositionLocalOf { FocusDim() }

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
    val shown by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "focus")
    val effect = style.effect
    // "Escurecer os outros": cada cartão com foco conta; os outros escurecem enquanto houver algum.
    val dim = LocalFocusDim.current
    val dimMode = effect == FocusEffectKind.DIM
    if (dimMode && focused) {
        DisposableEffect(dim) {
            dim.focused++
            onDispose { dim.focused-- }
        }
    }
    val dimmed by animateFloatAsState(if (dimMode && !focused && dim.focused > 0) 1f else 0f, tween(220), label = "dim")
    // As animações só correm no cartão com foco e à vista: as páginas que ficam construídas invisíveis (Apps, Definições
    // com a pré-visualização) não animam. Leem-se só ao desenhar: cada frame redesenha o efeito deste cartão, sem
    // voltar a compor nada.
    val visible = focused && !moving && LocalFadeSlotActive.current
    val animation = effect.animationOf(style.animation)
    // Pulsar, respirar e a luz a rodar: começam depois de o ecrã acabar de mudar para o cartão novo (ver ANIM_START_MS);
    // até lá o efeito fica como no início da volta (sem salto quando começa).
    val animating = visible && animation != AccountSettings.ANIM_NONE
    var started by remember(animating) { mutableStateOf(false) }
    if (animating) LaunchedEffect(Unit) { delay(ANIM_START_MS); started = true }
    val phaseState = if (animating && started) {
        rememberInfiniteTransition(label = "focusAnim").animateFloat(
            0f, 1f, infiniteRepeatable(tween(effect.period(animation), easing = LinearEasing), RepeatMode.Restart), label = "phase",
        )
    } else {
        null
    }
    // O vidro tem sempre uma faixa de luz que o atravessa. Entre passagens não há nada a mexer: espera sem pedir
    // frames (o ecrã fica parado), como a Apple TV.
    val sweeping = visible && effect.alwaysAnimated
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(sweeping) {
        if (!sweeping) {
            sweep.snapTo(1f)
            return@LaunchedEffect
        }
        delay(GLASS_FIRST_MS)
        while (true) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(GLASS_SWEEP_MS, easing = LinearEasing))
            delay(GLASS_REST_MS)
        }
    }
    return drawWithCache {
        val radius = corner.toPx()
        onDrawWithContent {
            val a = shown
            if (a <= 0f) {
                drawContent()
                if (dimmed > 0f) drawRoundRect(Color.Black, cornerRadius = CornerRadius(radius), alpha = DIM_ALPHA * dimmed)
                return@onDrawWithContent
            }
            val color = style.color ?: GoatColors.FocusRing
            val stroke = 3.dp.toPx()
            val edge = Size(size.width - stroke, size.height - stroke)
            val corners = CornerRadius(radius - stroke / 2)
            if (moving) {
                drawGlow(Color.Black, 14.dp.toPx(), radius, a * 0.55f, dy = 4.dp.toPx())
                drawContent()
                drawRoundRect(GoatColors.Accent, Offset(stroke / 2, stroke / 2), edge, corners, style = Stroke(stroke), alpha = a)
                return@onDrawWithContent
            }
            val phase = phaseState?.value ?: 0f
            // Intensidade da animação, de 0 a 1 (o pulsar nunca apaga de todo; o respirar quase).
            val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * phase)
            val k = when (animation) {
                AccountSettings.ANIM_PULSE -> 0.55f + 0.45f * wave
                AccountSettings.ANIM_BREATH -> 0.15f + 0.85f * wave
                else -> 1f
            }
            val shine = animation == AccountSettings.ANIM_SHINE
            // Por baixo do cartão.
            when (effect) {
                FocusEffectKind.GLOW -> drawGlow(glowColor(style.color, appColor), (4 + 6 * k).dp.toPx(), radius, a * (0.45f + 0.55f * k))
                FocusEffectKind.AURORA -> drawGlow(auroraColors(style.color)[1], 7.dp.toPx(), radius, a * 0.5f * k)
                // O vidro (como o GL2 da AIOStreams): sombra larga por baixo (0 14px 30px preto a 50%) e um halo branco suave.
                FocusEffectKind.GLASS -> {
                    drawGlow(Color.Black, 15.dp.toPx(), radius, a * 0.75f, dy = 10.dp.toPx())
                    drawGlow(Color.White, 13.dp.toPx(), radius, a * 0.26f)
                }
                FocusEffectKind.BORDER, FocusEffectKind.REFLECTION, FocusEffectKind.VIEWFINDER, FocusEffectKind.UNDERLINE,
                FocusEffectKind.RING, FocusEffectKind.DIM, FocusEffectKind.SHADOW,
                -> drawGlow(Color.Black, 14.dp.toPx(), radius, a * 0.55f, dy = 4.dp.toPx())
            }
            drawContent()
            // Por cima (ou à volta) do cartão.
            when (effect) {
                FocusEffectKind.BORDER -> {
                    // Com a luz a rodar, o contorno fica mais discreto e a luz é que se vê.
                    val base = if (shine) 0.45f else k
                    drawRoundRect(color, Offset(stroke / 2, stroke / 2), edge, corners, style = Stroke(stroke), alpha = a * base)
                }
                // Na cor automática o anel do vidro é branco (como no GL2), não o azul do foco.
                FocusEffectKind.GLASS -> {
                    drawGlass(style.color ?: Color.White, radius, a)
                    if (sweeping) drawGlassSweep(sweep.value, radius, a)
                }
                FocusEffectKind.REFLECTION -> {
                    drawReflection(a * k)
                    drawRoundRect(color, Offset(stroke / 2, stroke / 2), edge, corners, style = Stroke(stroke), alpha = a * (if (shine) 0.45f else 0.9f))
                }
                FocusEffectKind.AURORA -> {
                    // Na aurora, a "luz a rodar" é o próprio gradiente a girar.
                    val angle = if (shine) AURORA_ANGLE + phase * 360f else AURORA_ANGLE
                    drawAurora(auroraColors(style.color), angle, stroke, radius, a * (0.4f + 0.6f * k))
                }
                FocusEffectKind.VIEWFINDER -> drawViewfinder(color, a * k)
                FocusEffectKind.UNDERLINE -> drawUnderline(color, a * k)
                FocusEffectKind.RING -> {
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
                // O brilho e as sombras só se desenham por baixo.
                FocusEffectKind.GLOW, FocusEffectKind.DIM, FocusEffectKind.SHADOW -> Unit
            }
            // A luz que dá a volta ao contorno.
            if (shine) {
                val light = if (effect == FocusEffectKind.GLOW) lerp(glowColor(style.color, appColor), Color.White, 0.6f) else Color.White
                when (effect) {
                    FocusEffectKind.BORDER, FocusEffectKind.GLOW, FocusEffectKind.REFLECTION ->
                        drawShine(light, phase * 360f, stroke, radius, a)
                    FocusEffectKind.RING -> drawShine(light, phase * 360f, stroke, radius, a, grow = RING_GAP.toPx() + stroke / 2)
                    // Na aurora a luz é o gradiente a rodar (acima); os outros não aceitam esta animação.
                    FocusEffectKind.GLASS, FocusEffectKind.AURORA, FocusEffectKind.VIEWFINDER, FocusEffectKind.UNDERLINE,
                    FocusEffectKind.DIM, FocusEffectKind.SHADOW,
                    -> Unit
                }
            }
        }
    }
}

/**
 * Quando começam o pulsar, o respirar e a luz a rodar depois de o foco pousar: depois de o "Continuar a ver" se
 * reconstruir (0,6 s, SECTION_SETTLE_MS) e o de cima da Apple TV (~0,9 s). Esse frame é comprido (na SHIELD ~17–30 ms)
 * e com a animação a correr via-se a luz parar um instante.
 */
private const val ANIM_START_MS = 1100L

/** A luz que dá a volta ao contorno do cartão ([angle] em graus); [grow] afasta-a do cartão (o anel flutuante). */
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

/** Quanto escurecem os outros cartões em "Escurecer os outros". */
private const val DIM_ALPHA = 0.55f
/** Distância entre o cartão e o anel flutuante. */
private val RING_GAP = 6.dp
/** Para onde aponta o gradiente da aurora (o rosa em baixo à direita). */
private const val AURORA_ANGLE = 35f

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
 * Vidro por cima do cartão: um reflexo do canto de cima à esquerda que se apaga a meio e uma orla fina mais clara
 * em cima (a luz vem de cima).
 */
private fun DrawScope.drawGlass(ring: Color, corner: Float, alpha: Float) {
    // brightness(1.1): um pouco mais claro (somar branco fraco clareia como o filtro, sem uma camada à parte).
    drawRoundRect(Color.White, cornerRadius = CornerRadius(corner), alpha = 0.05f * alpha, blendMode = BlendMode.Plus)
    // O reflexo: o linear-gradient(120deg, ...) do GL2, com a mesma linha do CSS (a 120°, do tamanho que cobre o cartão).
    val rad = Math.toRadians(GLASS_ANGLE.toDouble())
    val dx = kotlin.math.sin(rad).toFloat()
    val dy = (-kotlin.math.cos(rad)).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2
    val center = Offset(size.width / 2, size.height / 2)
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.3f * alpha),
            0.28f to Color.White.copy(alpha = 0.08f * alpha),
            0.46f to Color.Transparent,
            0.7f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.08f * alpha),
            start = center - Offset(dx * half, dy * half),
            end = center + Offset(dx * half, dy * half),
        ),
        cornerRadius = CornerRadius(corner),
    )
    // O anel: 2 px por fora do cartão (o box-shadow 0 0 0 2px do GL2), quase opaco.
    val stroke = 2.dp.toPx()
    drawRoundRect(
        ring,
        Offset(-stroke / 2, -stroke / 2),
        Size(size.width + stroke, size.height + stroke),
        CornerRadius(corner + stroke / 2),
        style = Stroke(stroke),
        alpha = 0.95f * alpha,
    )
}

/** O ângulo do reflexo do vidro, como no CSS (0° para cima, 90° para a direita). */
private const val GLASS_ANGLE = 120f

/** A faixa de luz do vidro: entra pela esquerda e sai pela direita, na diagonal ([t] de 0 a 1; 1 = já passou). */
private fun DrawScope.drawGlassSweep(t: Float, corner: Float, alpha: Float) {
    if (t >= 1f) return
    val mid = -0.25f + 1.5f * t
    drawRoundRect(
        Brush.linearGradient(
            (mid - 0.18f) to Color.Transparent,
            mid to Color.White.copy(alpha = 0.35f * alpha),
            (mid + 0.18f) to Color.Transparent,
            start = Offset(0f, size.height * 0.2f),
            end = Offset(size.width, size.height * 0.8f),
        ),
        cornerRadius = CornerRadius(corner),
    )
}

/**
 * A faixa de luz do vidro. A primeira só passa depois de o ecrã acabar de mudar para o cartão novo: o "Continuar a
 * ver" reconstrói-se 0,6 s depois de o foco pousar (SECTION_SETTLE_MS) e o de cima da Apple TV ~0,9 s; esse frame é
 * comprido (na SHIELD ~17 ms), e com a faixa a passar ao mesmo tempo via-se um frame em falta. Cada passagem dura
 * [GLASS_SWEEP_MS] e entre elas o ecrã descansa [GLASS_REST_MS] sem redesenhar.
 */
private const val GLASS_FIRST_MS = 1100L
private const val GLASS_SWEEP_MS = 2000
private const val GLASS_REST_MS = 1600L

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
