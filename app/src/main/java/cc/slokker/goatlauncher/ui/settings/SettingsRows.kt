package cc.slokker.goatlauncher.ui.settings

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.key
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import cc.slokker.goatlauncher.ui.home.FocusColors
import cc.slokker.goatlauncher.ui.home.GraphiteBackground
import androidx.compose.ui.geometry.Offset
import cc.slokker.goatlauncher.ui.home.colorSpec
import cc.slokker.goatlauncher.ui.home.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.data.after
import cc.slokker.goatlauncher.ui.common.Toggle
/** Desvanece [EdgeFade] em cima e em baixo do conteúdo (as listas que passam por baixo do título e do rodapé). */
internal fun Modifier.fadeEdges(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val h = EdgeFade.toPx()
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = h), size = Size(size.width, h), blendMode = BlendMode.DstIn)
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - h, endY = size.height),
            topLeft = Offset(0f, size.height - h),
            blendMode = BlendMode.DstIn,
        )
    }

private val EdgeFade = 18.dp

/** Um grupo de linhas num cartão, como nas Definições da GoatTV. */
@Composable
internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), GroupShape).padding(4.dp),
        content = content,
    )
}

/** O ícone de uma secção, num quadrado arredondado; [accent] no gradiente da marca (a Aparência). */
@Composable
internal fun IconTile(icon: ImageVector, accent: Boolean = false) {
    Box(
        Modifier.size(32.dp).background(if (accent) GoatColors.Accent else SolidColor(Color.White.copy(alpha = 0.12f)), TileShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

private val GroupShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(9.dp)

/** A cor do efeito de foco: a automática (círculo com as cores todas) e as da paleta; ← → escolhe. */
@Composable
internal fun FocusColorRow(current: String, onPick: (String) -> Unit) {
    val s = LocalStrings.current
    val selected = parseColor(current)
    SwatchRow(
        title = s.focusColor,
        subtitle = if (selected == null) s.colorAuto else current.uppercase(),
        swatches = FocusColors.map { colorSpec(it) to (it?.let(::SolidColor) ?: AutoSwatch) },
        selected = colorSpec(selected),
        onPick = onPick,
    )
}

/** O fundo dos ícones transparentes: nenhum (vê-se o fundo do ecrã), grafite, ou uma cor da paleta. */
@Composable
internal fun IconBackgroundRow(current: String, onPick: (String) -> Unit) {
    val s = LocalStrings.current
    SwatchRow(
        title = s.iconBackground,
        subtitle = when (current) {
            AccountSettings.BG_NONE -> s.bgNone
            AccountSettings.BG_GRAPHITE -> s.bgGraphite
            else -> current.uppercase()
        },
        swatches = listOf(AccountSettings.BG_NONE to null, AccountSettings.BG_GRAPHITE to GraphiteBackground) +
            FocusColors.filterNotNull().map { colorSpec(it) to SolidColor(it) },
        selected = current,
        onPick = onPick,
    )
}

/**
 * Uma linha de amostras redondas (← → escolhe, OK fica com ela). Cada amostra é (valor, pintura); sem pintura
 * é "nenhuma": um círculo vazio cortado.
 */
@Composable
private fun SwatchRow(title: String, subtitle: String, swatches: List<Pair<String, Brush?>>, selected: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextDim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { (spec, brush) ->
                Surface(
                    onClick = { onPick(spec) },
                    modifier = Modifier.size(26.dp),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        pressedContainerColor = Color.Transparent,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.3f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White), inset = 0.dp, shape = CircleShape),
                    ),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(if (brush != null) Modifier.background(brush) else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape).drawWithCache {
                                onDrawBehind {
                                    drawLine(Color.White.copy(alpha = 0.6f), Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.8f, size.height * 0.2f), 1.5.dp.toPx())
                                }
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (spec.equals(selected, ignoreCase = true)) {
                            val light = (brush as? SolidColor)?.value?.let { it.luminance() > 0.5f } == true
                            Icon(GoatIcons.Check, contentDescription = null, tint = if (light) Color.Black else Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A cor automática (a de cada app): um círculo com as cores todas. */
private val AutoSwatch = Brush.sweepGradient(
    listOf(Color(0xFFF87171), Color(0xFFFACC15), Color(0xFF4ADE80), Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFFF87171)),
)

@Composable
internal fun Chevron() {
    Icon(GoatIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
}

/** Uma hora (0–23) que muda com ← →. Início e fim iguais querem dizer o dia todo (ver ParentalLock.inWindow). */
@Composable
internal fun HourRow(title: String, hour: Int, onChange: (Int) -> Unit) {
    val s = LocalStrings.current
    GlassRow(
        title = title,
        subtitle = s.hourHint,
        onClick = { onChange((hour + 1) % 24) },
        trailing = { Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.onPreviewKeyEvent { e ->
            val delta = when (e.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown) onChange((hour + delta + 24) % 24)
            true
        },
    )
}

/** O banner (ou o ícone) pequeno à esquerda das listas de apps. */
@Composable
internal fun AppThumb(app: cc.slokker.goatlauncher.data.LauncherApp) {
    Box(
        Modifier.size(64.dp, 36.dp).clip(GoatShapes.Small).background(app.color),
        contentAlignment = Alignment.Center,
    ) {
        when {
            app.banner != null -> Image(app.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            app.icon != null -> Image(app.icon, null, modifier = Modifier.size(24.dp))
        }
    }
}

// ---- As linhas de sempre (todas são GlassRow com o mesmo aspeto) ----

/** O visto das opções escolhidas. */
@Composable
internal fun CheckMark() {
    Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
}

/** Liga/desliga: OK troca. */
@Composable
internal fun ToggleRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit, subtitle: String? = null, modifier: Modifier = Modifier) {
    GlassRow(title = title, subtitle = subtitle, onClick = { onToggle(!checked) }, trailing = { Toggle(checked) }, modifier = modifier)
}

/** Leva a outra página (ou ao Android): a seta à direita e, se houver, o ícone à esquerda. */
@Composable
internal fun NavRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlassRow(title = title, subtitle = subtitle, leading = leading, onClick = onClick, trailing = { Chevron() }, modifier = modifier)
}

/** Uma opção de uma lista: o visto na escolhida. */
@Composable
internal fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlassRow(title = title, subtitle = subtitle, leading = leading, onClick = onClick, trailing = { if (selected) CheckMark() }, modifier = modifier)
}

/** Um valor de uma lista curta: mostra o atual e OK passa ao seguinte (do último volta ao primeiro). */
@Composable
internal fun <T> CycleRow(title: String, options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    GlassRow(title = title, subtitle = label(current), onClick = { onPick(options.after(current)) }, trailing = { Chevron() }, modifier = modifier)
}

/**
 * Uma ação que não se desfaz (desligar, desassociar): a primeira pressão arma (o subtítulo pede outra), a segunda faz
 * [onConfirm] e desarma.
 */
@Composable
internal fun ConfirmRow(title: String, hint: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    var armed by remember { mutableStateOf(false) }
    GlassRow(
        title = title,
        subtitle = if (armed) s.pressAgainToConfirm else hint,
        onClick = {
            if (armed) onConfirm()
            armed = !armed
        },
        modifier = modifier,
    )
}

/**
 * Se o serviço de acessibilidade (HomeGuardService) está ligado. Liga-se nas definições do Android, por isso
 * volta-se a ver ao regressar de lá (ao retomar o ecrã).
 */
@Composable
internal fun rememberGuardEnabled(): Boolean {
    val context = LocalContext.current
    var guard by remember { mutableStateOf(HomeGuardService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        guard = HomeGuardService.isEnabled(context)
        onPauseOrDispose { }
    }
    return guard
}
