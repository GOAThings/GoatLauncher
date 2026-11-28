package cc.slokker.goatlauncher.ui.home

import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.animation.AnimatedContent
import cc.slokker.goatlauncher.ui.theme.ControlRadius
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.slokker.goatlauncher.data.NetworkKind
import androidx.compose.ui.focus.onFocusChanged
import cc.slokker.goatlauncher.data.WeatherNow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.focusColors
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.BarPadding
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * À esquerda a saudação, a data e o tempo; ao centro os separadores (Início, Apps, Definições: mudam ao passar
 * o foco, como no tvOS); à direita o estado da rede e as horas.
 */
@Composable
internal fun TopBar(
    /** Com o separador Apps (disposição Android); [selected] conta só os separadores que se veem. */
    showApps: Boolean,
    selected: Int,
    clock24h: Boolean,
    showGreeting: Boolean,
    weather: WeatherNow?,
    network: NetworkKind,
    onHome: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    onNetwork: () -> Unit,
    night: Boolean,
    onNight: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    // As Definições já não são um separador: abrem-se no botão da roda, entre a rede e as horas.
    val entries = listOfNotNull(s.tabHome to onHome, (s.tabApps to onApps).takeIf { showApps })
    val tabs = remember(entries.size) { List(entries.size) { FocusRequester() } }
    // Só se troca de separador ao andar com ← → nos separadores (acabado de carregar). Quando o sistema põe lá o
    // foco sozinho (ex.: o elemento com foco desapareceu ao mudar de página nas Definições), não se abre nada.
    var arrowedAt by remember { mutableLongStateOf(0L) }
    // Se o foco está em algum sítio da barra (separadores ou botões), para o Início: com o foco cá em cima, abrir uma
    // página não lho tira. Como nos separadores, ao passar de um elemento para outro a barra diz por um instante que
    // perdeu o foco; só conta se no frame seguinte continuar fora.
    val outerScope = rememberCoroutineScope()
    val anyFocus = remember { BooleanArray(1) }
    var reportJob by remember { mutableStateOf<Job?>(null) }
    // Numa camada sua: a barra a mexer (destaque, foco) não obriga a redesenhar o resto do ecrã.
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer()
            .onFocusChanged {
                anyFocus[0] = it.hasFocus
                reportJob?.cancel()
                if (it.hasFocus) {
                    onFocusChanged(true)
                } else {
                    reportJob = outerScope.launch {
                        withFrameNanos { }
                        if (!anyFocus[0]) onFocusChanged(false)
                    }
                }
            },
    ) {
        TopGreeting(showGreeting, weather, Modifier.align(Alignment.CenterStart))
        // Barra translúcida, sem contorno; o separador aberto é um botão mais claro lá dentro (cantos como os da GoatTV).
        // Esse destaque é um só e desliza (com mola) para o separador aberto ou com foco; com foco fica branco.
        // Ao subir para os separadores, o foco cai no separador aberto.
        var focusedTab by remember { mutableIntStateOf(-1) }
        val scope = rememberCoroutineScope()
        var openJob by remember { mutableStateOf<Job?>(null) }
        var pendingOpen by remember { mutableStateOf<(() -> Unit)?>(null) }
        var leaveJob by remember { mutableStateOf<Job?>(null) }
        // Se o foco está dentro da barra (o último aviso dela); não é estado do Compose: só se lê no leaveJob.
        val barFocus = remember { BooleanArray(1) }
        val selectedTab by rememberUpdatedState(selected.coerceIn(entries.indices))
        val bounds = remember(entries.size) { mutableStateListOf(*Array(entries.size) { 0f to 0f }) }
        val target = if (focusedTab >= 0) focusedTab else selected.coerceIn(entries.indices)
        // A mola acaba a meio píxel do sítio: com o limite por defeito continuava a mexer décimas de píxel (a pedir
        // frames) bem depois de parecer parada, e a página só deve abrir com a barra parada (ver pillSettled).
        val pillXState = animateFloatAsState(bounds[target].first, PillSpring, label = "tabX")
        val pillWState = animateFloatAsState(bounds[target].second, PillSpring, label = "tabW")
        val pillX by pillXState
        val pillW by pillWState
        val pillColor by animateColorAsState(if (focusedTab >= 0) Color.White else Color.White.copy(alpha = 0.2f), tween(180), label = "tabColor")
        // Só um separador (Apple TV: não há Apps, e as Definições são o botão da roda): não há barra de separadores.
        if (entries.size > 1) {
        Box(
            Modifier
                .align(Alignment.Center)
                .onFocusChanged {
                    barFocus[0] = it.hasFocus
                    leaveJob?.cancel()
                    if (!it.hasFocus) {
                        // Ao passar de um separador para o do lado, a barra diz por um instante que perdeu o foco (o
                        // separador antigo larga-o antes de o novo o apanhar). Só conta como sair se no frame seguinte
                        // o foco continuar fora; antes, isto abria a página pendente a meio de → → → (construía as
                        // Apps de passagem).
                        leaveJob = scope.launch {
                            withFrameNanos { }
                            if (barFocus[0]) return@launch
                            if (anyFocus[0]) {
                                // Foi para os botões da direita (a caminho da roda, por exemplo): o separador por onde
                                // passou não abre.
                                openJob?.cancel()
                                pendingOpen = null
                                return@launch
                            }
                            // Saiu da barra antes de a página abrir (ex.: ↓ logo a seguir): abre-a já.
                            pendingOpen?.let { open -> openJob?.cancel(); pendingOpen = null; open() }
                        }
                    }
                }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) arrowedAt = SystemClock.uptimeMillis()
                    false
                }
                .background(GoatColors.ControlFill, GoatShapes.Bar)
                .padding(BarPadding)
                // O destaque lê a animação só ao desenhar: não recompõe a barra a cada fotograma.
                .drawBehind {
                    if (pillW > 0f) {
                        drawRoundRect(pillColor, Offset(pillX, 0f), Size(pillW, size.height), CornerRadius(ControlRadius.toPx()))
                    }
                },
        ) {
            Row(
                // Ao entrar na barra (de baixo, ou com ← do botão à direita), o foco cai sempre no separador aberto.
                // Com focusRestorer caía às vezes noutro (← vindo do botão do dia/noite punha-o no Início) sem o
                // abrir, porque essa tecla foi tratada fora da barra: ficava o foco no Início com as Definições à vista.
                Modifier.focusProperties { onEnter = { tabs[selectedTab].requestFocus() } }.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                entries.forEachIndexed { i, (label, action) ->
                    Surface(
                        onClick = action,
                        // Passar o foco por um separador abre-o (sem carregar em OK).
                        modifier = Modifier
                            .focusRequester(tabs[i])
                            .onGloballyPositioned {
                                // Só quando mudam: escrever o mesmo valor a cada layout recomporia a barra à toa.
                                val b = it.positionInParent().x to it.size.width.toFloat()
                                if (bounds[i] != b) bounds[i] = b
                            }
                            .onFocusChanged {
                                if (it.isFocused) focusedTab = i else if (focusedTab == i) focusedTab = -1
                                if (it.isFocused && SystemClock.uptimeMillis() - arrowedAt < TAB_ARROW_MS && i != selected) {
                                    // Abre a página só quando o foco pára aqui: o destaque desliza primeiro (construir a
                                    // página nova a meio do deslize dava um solavanco), e ao passar por um separador a
                                    // caminho de outro não se constrói a página dele.
                                    openJob?.cancel()
                                    pendingOpen = action
                                    openJob = scope.launch {
                                        delay(TAB_OPEN_DELAY_MS)
                                        // Construir a página nova leva um frame comprido (na SHIELD 30–100 ms): só com o
                                        // destaque parado (a menos de um píxel do sítio), senão o fim do deslize dá um salto.
                                        withTimeoutOrNull(TAB_SETTLE_MAX_MS) {
                                            snapshotFlow {
                                                abs(pillXState.value - bounds[i].first) < PILL_SETTLED_PX &&
                                                    abs(pillWState.value - bounds[i].second) < PILL_SETTLED_PX
                                            }.first { it }
                                        }
                                        pendingOpen = null
                                        action()
                                    }
                                } else if (!it.isFocused && focusedTab == -1) {
                                    openJob?.cancel()
                                }
                                // arrowedAt não se apaga aqui: com ← ← ← rápidos o Compose pode entregar as mudanças de
                                // foco todas de uma vez, depois da última tecla; apagá-la no separador do meio fazia o
                                // último não abrir (ficava o foco no Início com as Definições à vista). Já vale só
                                // TAB_ARROW_MS depois da tecla.
                            },
                        shape = ClickableSurfaceDefaults.shape(GoatShapes.Control),
                        // O fundo é o destaque que desliza (atrás); aqui fica transparente.
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            pressedContainerColor = Color.White.copy(alpha = 0.3f),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        TabLabel(label, onPill = i == target, barFocused = focusedTab >= 0, selected = i == selected)
                    }
                }
            }
        }
        }
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Dia/noite: OK troca entre fundo claro e fundo escurecido (e fotos do tom certo).
            TopButton(onClick = onNight) {
                // O sol e a lua trocam a crescer e a aparecer, em vez de saltar.
                AnimatedContent(
                    targetState = night,
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320), initialScale = 0.6f)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.6f))
                    },
                    label = "dayNightIcon",
                ) { isNight ->
                    Icon(
                        if (isNight) GoatIcons.Moon else GoatIcons.Sun,
                        contentDescription = s.dayNight,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Estado da rede (Wi-Fi, cabo ou sem rede); OK abre as definições de rede.
            TopButton(onClick = onNetwork) {
                Icon(
                    when (network) {
                        NetworkKind.ETHERNET -> GoatIcons.Ethernet
                        NetworkKind.WIFI -> GoatIcons.Wifi
                        NetworkKind.NONE -> GoatIcons.WifiOff
                    },
                    contentDescription = s.network,
                    modifier = Modifier.size(18.dp),
                )
            }
            // As Definições do launcher (página inteira).
            TopButton(onClick = onSettings) {
                Icon(GoatIcons.Settings, contentDescription = s.settings, modifier = Modifier.size(18.dp))
            }
            TopClock(clock24h)
        }
    }
}

/** Quanto tempo depois de ← → o foco a chegar a um separador ainda conta como ter andado até ele. */
private const val TAB_ARROW_MS = 400L

/** Quanto o foco tem de ficar num separador (a andar com ← →) para ele abrir: o tempo do destaque deslizar. */
private const val TAB_OPEN_DELAY_MS = 250L

/** Quanto mais se espera, no máximo, que o destaque pare antes de abrir a página (depois de [TAB_OPEN_DELAY_MS]). */
private const val TAB_SETTLE_MAX_MS = 250L

/** A que distância do sítio (px) o destaque já conta como parado para abrir a página. */
private const val PILL_SETTLED_PX = 1f

/** O deslize do destaque dos separadores; acaba a meio píxel (ver o TopBar). */
private val PillSpring = spring(dampingRatio = 0.82f, stiffness = 420f, visibilityThreshold = 0.5f)

/** O nome de um separador: preto sobre o destaque com foco, branco no aberto, apagado nos outros. */
@Composable
internal fun TabLabel(label: String, onPill: Boolean, barFocused: Boolean, selected: Boolean) {
    val color by animateColorAsState(
        when {
            onPill && barFocused -> Color.Black
            selected -> Color.White
            else -> Color.White.copy(alpha = 0.78f)
        },
        tween(180),
        label = "tabText",
    )
    Text(
        label,
        Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        color = color,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
    )
}

/** Botão da barra de cima (vidro; branco com foco), com os cantos dos controlos, como na GoatTV. */
@Composable
private fun TopButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = ClickableSurfaceDefaults.shape(GoatShapes.Control),
        colors = focusColors(container = GoatColors.ControlFill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
