package cc.slokker.goatlauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import cc.slokker.goatlauncher.ui.Screen
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A troca de separador com um fade (como o AnimatedContent), mas o fade só começa depois de o separador novo estar
 * construído. Na SHIELD construir as Apps ou as Definições leva 30–100 ms num só frame: com o AnimatedContent o fade
 * começava nesse frame e, quando ele acabava, já ia a meio (dava um salto). Aqui o separador novo é composto
 * invisível, por baixo do antigo parado, e os fades correm nos frames seguintes, que são leves.
 * [onFadeStart] diz quando começa o fade para o separador novo (os véus e o fundo do ecrã seguem-no).
 */
@Composable
internal fun TabCrossfade(
    target: Screen,
    modifier: Modifier = Modifier,
    onFadeStart: (Screen) -> Unit = {},
    /** Os separadores a construir antes da primeira visita (ver [BuildThenFade]). */
    prebuild: List<Screen> = emptyList(),
    canPrebuild: () -> Boolean = { true },
    content: @Composable (Screen) -> Unit,
) = BuildThenFade(
    target,
    keyOf = { it },
    outMs = TAB_OUT_MS,
    inMs = TAB_IN_MS,
    inDelayMs = TAB_IN_DELAY_MS,
    modifier = modifier,
    onFadeStart = onFadeStart,
    // Início, Apps e Definições ficam construídos depois da primeira visita: trocar é só o fade (na SHIELD construir
    // um separador levava 40–100 ms num frame).
    keepAlive = 2,
    prebuild = prebuild,
    canPrebuild = canPrebuild,
    content = content,
)

/**
 * Troca [target] por outro com um fade, construindo o novo primeiro (ver [TabCrossfade]). Só há fade quando muda
 * [keyOf]; com a mesma chave (ex.: os mesmos cartões com outro progresso), o conteúdo atualiza-se no sítio.
 */
@Composable
internal fun <T> BuildThenFade(
    target: T,
    keyOf: (T) -> Any?,
    outMs: Int,
    inMs: Int,
    inDelayMs: Int,
    modifier: Modifier = Modifier,
    onFadeStart: (T) -> Unit = {},
    /** O novo entra a deslizar esta distância (da direita), com uma mola sem ressalto; 0 = só o fade. */
    slide: Dp = 0.dp,
    /**
     * Quantos dos que saíram ficam construídos (invisíveis, sem foco): voltar a um deles é só o fade, sem o frame
     * comprido de o construir. 0 = cada um é construído de novo.
     */
    keepAlive: Int = 0,
    /**
     * Os que convém ter construídos antes de aparecerem pela primeira vez (só com [keepAlive]): constroem-se
     * invisíveis, um de cada vez, quando [canPrebuild] o deixa (ex.: sem teclas há um bocado), para nem a primeira
     * visita ter o frame comprido.
     */
    prebuild: List<T> = emptyList(),
    canPrebuild: () -> Boolean = { true },
    /** Desenhar os construídos antes uns frames quase transparentes, para a GPU compilar já os shaders deles. */
    warmPrebuilt: Boolean = true,
    /** Onde ficam, dentro da caixa, o que entra e o que sai (ex.: em baixo, se tiverem alturas diferentes). */
    contentAlignment: Alignment = Alignment.TopStart,
    /** O novo entra deslocado esta fração da largura dele (negativa = da esquerda), com a mola do [slide]. */
    slideInFraction: Float = 0f,
    /** O que sai vai-se deslocando até esta fração da largura dele, ao ritmo do fade de saída. */
    slideOutFraction: Float = 0f,
    content: @Composable (T) -> Unit,
) {
    val slides = slide != 0.dp || slideInFraction != 0f
    val targetKey = keyOf(target)
    var current by remember { mutableStateOf(Slot(target)) }
    // O que está a desaparecer (null fora da troca).
    var previous by remember { mutableStateOf<Slot<T>?>(null) }
    // Os que saíram e ficam construídos, do mais recente para o mais antigo (nunca o atual nem o que está a sair).
    val kept = remember { mutableStateListOf<Slot<T>>() }
    fun keep(slot: Slot<T>) {
        if (keepAlive <= 0) return
        kept.removeAll { keyOf(it.value) == keyOf(slot.value) }
        kept.add(0, slot)
        while (kept.size > keepAlive) kept.removeAt(kept.lastIndex)
    }
    val inAlpha = remember { Animatable(1f) }
    val outAlpha = remember { Animatable(0f) }
    // 1 = no sítio; 0 = deslocado [slide] para a direita.
    val inPlace = remember { Animatable(1f) }
    val slidePx = with(LocalDensity.current) { slide.toPx() }
    val fadeStarted by rememberUpdatedState(onFadeStart)
    val latest by rememberUpdatedState(target)
    val mayPrebuild by rememberUpdatedState(canPrebuild)
    LaunchedEffect(prebuild) {
        if (prebuild.isEmpty() || keepAlive <= 0) return@LaunchedEffect
        delay(PREBUILD_START_MS)
        for (value in prebuild) {
            val k = keyOf(value)
            fun built() = keyOf(current.value) == k || previous?.let { keyOf(it.value) == k } == true || kept.any { keyOf(it.value) == k }
            // À espera de um momento calmo (sem troca a meio e sem teclas).
            while (!built() && (previous != null || !mayPrebuild())) delay(PREBUILD_POLL_MS)
            if (built()) continue
            // Só se houver lugar: não se deita fora um que já foi visitado para construir um que ainda não foi.
            if (kept.size >= keepAlive) break
            val slot = Slot(value).apply { warming = warmPrebuilt }
            kept.add(slot)
            // Desenhado uns frames quase transparente (não se vê): a GPU compila já os shaders dele e a primeira vez
            // que aparecer não tem o frame comprido (na SHIELD, ~600 ms nas Definições acabadas de instalar).
            if (warmPrebuilt) {
                repeat(WARM_FRAMES) { withFrameNanos { } }
                slot.warming = false
            }
            delay(PREBUILD_GAP_MS)
        }
    }
    LaunchedEffect(targetKey) {
        if (targetKey == keyOf(current.value)) return@LaunchedEffect
        inAlpha.snapTo(0f)
        outAlpha.snapTo(1f)
        if (slides) inPlace.snapTo(0f)
        // Uma troca interrompida a meio deixou o anterior por arrumar.
        previous?.let { keep(it) }
        previous = current
        // Se o novo já estava construído, volta-se a ele (sai da lista dos guardados).
        val alive = kept.firstOrNull { keyOf(it.value) == targetKey }
        if (alive != null) kept.remove(alive)
        current = alive ?: Slot(latest)
        // O frame em que o novo se compõe, mede e desenha (o pesado) passa com tudo parado; o fade começa no frame a
        // seguir, com o relógio desse frame.
        withFrameNanos { }
        withFrameNanos { }
        fadeStarted(latest)
        coroutineScope {
            launch { outAlpha.animateTo(0f, tween(outMs)) }
            if (slides) launch { inPlace.animateTo(1f, SlideSpring) }
            inAlpha.animateTo(1f, tween(inMs, delayMillis = inDelayMs))
        }
        previous?.let { keep(it) }
        previous = null
    }
    // Dentro de outro (ex.: as páginas das Definições, que ficam construídas quando se fecham): só é o atual se o de
    // fora também for. Senão uma animação lá dentro corria às escondidas (a pré-visualização da Aparência).
    val parentActive = LocalFadeSlotActive.current
    Box(modifier, contentAlignment = contentAlignment) {
        // Todos na mesma lista com key: o que sai (e os guardados) mantêm-se, e quem volta a ser o atual não volta a
        // ser construído.
        for (slot in kept.reversed() + listOfNotNull(previous, current)) {
            val k = keyOf(slot.value)
            key(k) {
                val isCurrent = slot === current
                val isLeaving = slot === previous
                // O atual mostra sempre o valor mais recente com a mesma chave.
                val shown = if (isCurrent && k == targetKey) target else slot.value
                Box(
                    Modifier
                        .graphicsLayer {
                            // Os guardados ficam a 0: a camada não se desenha.
                            alpha = when {
                                isCurrent -> inAlpha.value
                                isLeaving -> outAlpha.value
                                slot.warming -> WARM_ALPHA
                                else -> 0f
                            }
                            translationX = when {
                                isCurrent -> (1f - inPlace.value) * (slidePx + slideInFraction * size.width)
                                isLeaving -> (1f - outAlpha.value) * slideOutFraction * size.width
                                else -> 0f
                            }
                        }
                        // Só o atual recebe o foco (senão ele saltava para o que sai e perdia-se no fim do fade).
                        .focusProperties { onEnter = { if (!isCurrent) cancelFocusChange() } }
                        .focusGroup(),
                ) {
                    CompositionLocalProvider(LocalFadeSlotActive provides (isCurrent && parentActive)) { content(shown) }
                }
            }
        }
    }
}

/** Dentro do [BuildThenFade]: se este conteúdo é o atual (false no que sai e nos guardados, invisíveis). */
internal val LocalFadeSlotActive = compositionLocalOf { true }

/** O deslize do [BuildThenFade]: a mesma mola sem ressalto do foco, um pouco mais rápida (90% em ~0,23 s). */
private val SlideSpring = spring<Float>(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.002f)

/** Uma caixa à volta do valor: o [BuildThenFade] aceita null como valor (ex.: sem cartões). */
private class Slot<T>(val value: T) {
    /** Construído antes de ser visitado: desenha-se uns frames quase transparente (ver [BuildThenFade]). */
    var warming by mutableStateOf(false)
}

/** Quase transparente (1/255): a GPU desenha (compila os shaders), o olho não vê. */
private const val WARM_ALPHA = 1f / 255f
private const val WARM_FRAMES = 3

/** Construir antes: começa este tempo depois de aparecer, espera assim entre tentativas e dá este intervalo entre um e outro. */
private const val PREBUILD_START_MS = 1_500L
private const val PREBUILD_POLL_MS = 250L
private const val PREBUILD_GAP_MS = 400L

/** Os tempos do fade da troca de separador (os do AnimatedContent de antes). */
private const val TAB_OUT_MS = 150
private const val TAB_IN_MS = 220
private const val TAB_IN_DELAY_MS = 60
